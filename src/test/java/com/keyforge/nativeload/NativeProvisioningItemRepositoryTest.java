package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK (parent txn + content) + record-hash for native provisioning items. */
class NativeProvisioningItemRepositoryTest {

    private static NativeProvisioningItemRecord rec(String txnId, String name, String value, int idx) {
        NativeProvisioningItemRecord r = new NativeProvisioningItemRecord();
        r.txnSourceId = txnId;
        r.itemType = "ATTRIBUTE";
        r.operation = "Add";
        r.name = name;
        r.value = value;
        r.itemIndex = idx;
        return r;
    }

    @Test
    void itemIdIsDeterministicOverTxnAndContent() {
        String a = NativeProvisioningItemRepository.canonicalItemId(rec("t1", "memberOf", "g1", 0));
        String b = NativeProvisioningItemRepository.canonicalItemId(rec("t1", "memberOf", "g1", 0));
        assertNotNull(a);
        assertEquals(a, b, "same item ⇒ same id (never random)");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void duplicateNameValueDisambiguatedByIndex() {
        String i0 = NativeProvisioningItemRepository.canonicalItemId(rec("t1", "memberOf", "g1", 0));
        String i1 = NativeProvisioningItemRepository.canonicalItemId(rec("t1", "memberOf", "g1", 1));
        assertNotEquals(i0, i1, "index disambiguates otherwise-identical items");
    }

    @Test
    void recordHashChangesWhenValueChanges() {
        String h1 = NativeProvisioningItemRepository.recordHash(rec("t1", "memberOf", "g1", 0));
        String h2 = NativeProvisioningItemRepository.recordHash(rec("t1", "memberOf", "g2", 0));
        assertNotEquals(h1, h2);
    }

    @Test
    void structuredValueChangesDeterministicIdAndBusinessHash() {
        NativeProvisioningItemRecord first = rec("t1", "groups", null, 0);
        first.valueJson = "[\"a\",\"b\"]";
        NativeProvisioningItemRecord second = rec("t1", "groups", null, 0);
        second.valueJson = "[\"a\",\"c\"]";
        assertNotEquals(NativeProvisioningItemRepository.canonicalItemId(first),
                NativeProvisioningItemRepository.canonicalItemId(second));
        assertNotEquals(NativeProvisioningItemRepository.recordHash(first),
                NativeProvisioningItemRepository.recordHash(second));
    }

    @Test
    void derivedItemLineageReferencesParentAndNeverItsGeneratedPrimaryKey() {
        NativeProvisioningItemRecord item = rec("iiq-parent-id", "memberOf", "group-a", 2);
        assertEquals("iiq-parent-id", item.txnSourceId);
        assertEquals("iiq-parent-id|ATTRIBUTE|2", NativeProvisioningItemRepository.derivedNaturalKey(item));
        assertNotEquals(NativeProvisioningItemRepository.canonicalItemId(item), item.txnSourceId);
    }

    @Test
    void repositoryBindsTextValueAndJsonbValueToTheirMatchingInsertColumns() throws Exception {
        NativeProvisioningItemRecord item = rec("txn-source", "groups", "group-a", 0);
        item.valueJson = "[\"group-a\"]";
        Map<Integer, Object> bindings = new HashMap<Integer, Object>();
        String[] preparedSql = new String[1];
        ResultSet result = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return Boolean.TRUE;
                    if ("getBoolean".equals(method.getName())) return Boolean.TRUE;
                    return null;
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("set") && args != null && args.length >= 2
                            && args[0] instanceof Integer) {
                        bindings.put((Integer) args[0], args[1]);
                    }
                    if ("executeQuery".equals(method.getName())) return result;
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        preparedSql[0] = (String) args[0];
                        return statement;
                    }
                    return null;
                });

        NativeProvisioningItemRepository repository = new NativeProvisioningItemRepository("iiq_native");
        repository.upsert(connection, item);

        String sql = preparedSql[0];
        int valuesAt = sql.indexOf(") VALUES (");
        String columnText = sql.substring(sql.indexOf('(') + 1, valuesAt);
        int valuesStart = valuesAt + ") VALUES (".length();
        int valuesEnd = sql.indexOf(") ON CONFLICT", valuesStart);
        String valuesText = sql.substring(valuesStart, valuesEnd);
        String[] columns = columnText.split(",\\s*");
        String[] expressions = valuesText.split(",\\s*");
        assertEquals(columns.length, expressions.length, "every INSERT column must have one expression");

        int valueIndex = java.util.Arrays.asList(columns).indexOf("value");
        int jsonIndex = java.util.Arrays.asList(columns).indexOf("value_json");
        assertEquals("?", expressions[valueIndex], "plain source value must bind to text value column");
        assertEquals("?::jsonb", expressions[jsonIndex], "structured value must be cast for JSONB column");
        assertEquals("group-a", bindings.get(valueIndex + 1));
        assertEquals("[\"group-a\"]", bindings.get(jsonIndex + 1));
    }
}
