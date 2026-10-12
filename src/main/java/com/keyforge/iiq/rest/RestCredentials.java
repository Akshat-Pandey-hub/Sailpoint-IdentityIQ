package com.keyforge.iiq.rest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/**
 * The server-side REST API credential (HTTP Basic) for the KF Agent and Parquet query endpoints. Deliberately
 * separate from the IdentityIQ login ({@code IIQ_USERNAME}/{@code IIQ_PASSWORD}), which the service itself uses to
 * call IIQ.
 *
 * <p>Stored as a properties file holding the username and a salted PBKDF2-HMAC-SHA256 hash of the password — never
 * the password itself. Default location {@code <user home>/.keyforge/kfagent-rest-auth.properties} (outside any
 * repository, so it cannot be committed); override the PATH (not the secret) with {@code KFAGENT_REST_AUTH_FILE}.
 * The file is written by the interactive {@code set-rest-credentials} command and restricted to the current user.
 */
public final class RestCredentials {

    public static final String KEY_AUTH_FILE = "KFAGENT_REST_AUTH_FILE";
    static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final String PREFIX = "pbkdf2-sha256";

    private final String username;
    private final int iterations;
    private final byte[] salt;
    private final byte[] hash;

    private RestCredentials(String username, int iterations, byte[] salt, byte[] hash) {
        this.username = username;
        this.iterations = iterations;
        this.salt = salt;
        this.hash = hash;
    }

    /** Hashes a new credential (fresh random salt). */
    public static RestCredentials create(String username, char[] password) {
        if (username == null || username.isBlank() || username.indexOf(':') >= 0) {
            throw new IllegalArgumentException("username must be non-blank and must not contain ':'");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return new RestCredentials(username.trim(), ITERATIONS, salt, pbkdf2(password, salt, ITERATIONS));
    }

    public String username() {
        return username;
    }

    /** Constant-time check of a supplied username/password against the stored credential. */
    public boolean matches(String suppliedUser, char[] suppliedPassword) {
        if (suppliedUser == null || suppliedPassword == null) {
            return false;
        }
        boolean userOk = MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8),
                suppliedUser.getBytes(StandardCharsets.UTF_8));
        boolean passOk = MessageDigest.isEqual(hash, pbkdf2(suppliedPassword, salt, iterations));
        return userOk & passOk; // evaluate both: no early exit on a wrong username
    }

    // --- file storage ---------------------------------------------------------

    /** The configured credential file path ({@code KFAGENT_REST_AUTH_FILE}, else the per-user default). */
    public static Path defaultPath() {
        String override = System.getenv(KEY_AUTH_FILE);
        if (override == null || override.isBlank()) {
            override = System.getProperty(KEY_AUTH_FILE);
        }
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".keyforge", "kfagent-rest-auth.properties");
    }

    /** Loads the credential, or {@code null} when the file does not exist. Malformed content throws. */
    public static RestCredentials load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        String user = p.getProperty("username");
        String stored = p.getProperty("password.hash");
        if (user == null || user.isBlank() || stored == null) {
            throw new IOException("credential file is incomplete: " + file);
        }
        String[] parts = stored.trim().split(":");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            throw new IOException("unsupported password.hash format in " + file);
        }
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            return new RestCredentials(user.trim(), Integer.parseInt(parts[1]), b64.decode(parts[2]),
                    b64.decode(parts[3]));
        } catch (IllegalArgumentException e) {
            throw new IOException("malformed password.hash in " + file, e);
        }
    }

    /** Writes the credential (hash only) atomically and restricts the file to the current user (best effort). */
    public void save(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Base64.Encoder b64 = Base64.getEncoder();
        Properties p = new Properties();
        p.setProperty("username", username);
        p.setProperty("password.hash", PREFIX + ":" + iterations + ":" + b64.encodeToString(salt) + ":"
                + b64.encodeToString(hash));
        Path tmp = Files.createTempFile(dir, ".kfagent-rest-auth", ".tmp");
        try {
            restrictToOwner(tmp);
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "KF Agent REST API credential (salted PBKDF2 hash; managed by set-rest-credentials)."
                        + " Do not commit or share.");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictToOwner(file);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            return;
        } catch (UnsupportedOperationException | IOException notPosix) {
            // Windows: fall through to an owner-only ACL
        }
        try {
            AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (view != null) {
                UserPrincipal owner = Files.getOwner(file);
                AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                        .setPermissions(EnumSet.of(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA,
                                AclEntryPermission.APPEND_DATA, AclEntryPermission.READ_ATTRIBUTES,
                                AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.READ_ACL,
                                AclEntryPermission.WRITE_ACL, AclEntryPermission.DELETE,
                                AclEntryPermission.SYNCHRONIZE, AclEntryPermission.READ_NAMED_ATTRS,
                                AclEntryPermission.WRITE_NAMED_ATTRS))
                        .build();
                view.setAcl(List.of(entry));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // best effort: the file still lives in the user's own profile directory
        }
    }

    static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
