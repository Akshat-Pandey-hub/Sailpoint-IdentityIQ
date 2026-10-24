package com.keyforge.nativeiiq;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoggingSourceScanTest {

    @Test
    void nativePluginSourcesUseTheIiqLoggerAndNeverWriteToConsole() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java", "com", "keyforge", "nativeiiq");
        String[] forbidden = {"java.util.logging", "System.out", "System.err", "printStackTrace"};
        List<String> violations = new ArrayList<String>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    for (String token : forbidden) {
                        if (source.contains(token)) {
                            violations.add(path + " contains " + token);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
