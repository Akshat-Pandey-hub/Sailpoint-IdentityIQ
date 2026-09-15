package com.keyforge.iiq.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Account;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Account → {@code account} mapping: typed core columns, resolved FK
 * relationships, correct booleans, and the connector attribute bag preserved verbatim
 * (arrays intact) — with nothing invented.
 */
class AccountRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ACC_HEX = "7f00010198421229819849ebc9ef0c72";
    private static final String ACC_CANON = "7f000101-9842-1229-8198-49ebc9ef0c72";
    private static final String USER_HEX = "7f00010198421229819849ebc9ef0c71";
    private static final String USER_CANON = "7f000101-9842-1229-8198-49ebc9ef0c71";
    private static final String INST_HEX = "7f00010198421229819849c815b90bfc";
    private static final String INST_CANON = "7f000101-9842-1229-8198-49c815b90bfc";

    private static Account.Ref identityRef() {
        return new Account.Ref("Alexander Evans", "Alexander Evans", USER_HEX, "https://iiq/Users/" + USER_HEX);
    }

    private static Account.Ref applicationRef() {
        return new Account.Ref("EntraAuth", null, INST_HEX, "https://iiq/Applications/" + INST_HEX);
    }

    private static ObjectNode connectorAttrs() {
        ObjectNode a = MAPPER.createObjectNode();
        a.put("mail", "alexander.evans@srallapa.onmicrosoft.com");
        a.putArray("groups").add("00edc4ac-9a67-4e7e-abe3-f84d9e8ffee5").add("02cf391b-36ae-4fb2-bcca-3c12448d9560");
        a.putArray("proxyAddresses").add("SMTP:alexander.evans@srallapa.onmicrosoft.com");
        return a;
    }

    private static Account account(ObjectNode attrs) {
        return new Account(ACC_HEX, "Alexander Evans", "179fcc53-99af-43e1-92c6-38c46c6af4ee",
                true, false, false, true, "2025-07-27T03:27:26.192Z",
                applicationRef(), identityRef(),
                new Account.Meta("Account", "https://iiq/x", "2025-07-27T03:27:26.192Z",
                        "2025-07-27T04:10:07.384Z", "W/\"1\""),
                List.of("urn:acct"), attrs);
    }

    @Test
    void mapsCoreColumnsAndRelationships() {
        AccountRow row = AccountRowMapper.map(account(connectorAttrs()), Set.of(USER_CANON), Set.of(INST_CANON));

        assertEquals(ACC_CANON, row.accountid());
        assertEquals(USER_CANON, row.userid());
        assertEquals("Alexander Evans", row.identityDisplayName());
        assertEquals(INST_CANON, row.instanceid());
        assertEquals("EntraAuth", row.applicationDisplayName());
        assertEquals("179fcc53-99af-43e1-92c6-38c46c6af4ee", row.nativeIdentity());
        assertEquals("Alexander Evans", row.accountDisplayName());
        assertEquals(Boolean.TRUE, row.active());
        assertEquals(Boolean.FALSE, row.locked());
        assertEquals(Boolean.TRUE, row.hasEntitlements());
        assertEquals(Boolean.FALSE, row.manuallyCorrelated());
        assertEquals(LocalDateTime.of(2025, 7, 27, 4, 10, 7, 384_000_000), row.modifiedAt());
    }

    @Test
    void preservesConnectorAttributesWithCompleteArrays() throws Exception {
        AccountRow row = AccountRowMapper.map(account(connectorAttrs()), Set.of(USER_CANON), Set.of(INST_CANON));
        JsonNode attrs = MAPPER.readTree(row.attributesJson());

        assertEquals("alexander.evans@srallapa.onmicrosoft.com", attrs.path("mail").asText());
        assertTrue(attrs.path("groups").isArray());
        assertEquals(2, attrs.path("groups").size());       // both groups kept, not reduced
        assertEquals(1, attrs.path("proxyAddresses").size());
    }

    @Test
    void unresolvedRelationshipsBecomeNullFks() {
        AccountRow row = AccountRowMapper.map(account(connectorAttrs()), Set.of(), Set.of());
        assertNull(row.userid());       // identity not a known usr
        assertNull(row.instanceid());   // application not a known instance
        // ...but the display names are still captured.
        assertEquals("Alexander Evans", row.identityDisplayName());
        assertEquals("EntraAuth", row.applicationDisplayName());
    }

    @Test
    void nullAttributesWhenNoConnectorData() {
        Account acc = new Account(ACC_HEX, "X", "nid", null, null, null, null, null,
                null, null, null, List.of(), MAPPER.createObjectNode());
        assertNull(AccountRowMapper.map(acc, Set.of(), Set.of()).attributesJson());
    }

    @Test
    void invalidIdIsRejected() {
        Account acc = new Account("bad", "X", "nid", true, false, false, true, null,
                null, null, null, List.of(), MAPPER.createObjectNode());
        assertThrows(AccountMappingException.class, () -> AccountRowMapper.map(acc, Set.of(), Set.of()));
    }
}
