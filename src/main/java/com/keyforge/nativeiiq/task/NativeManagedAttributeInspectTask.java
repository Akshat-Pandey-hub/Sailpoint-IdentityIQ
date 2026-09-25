package com.keyforge.nativeiiq.task;

import com.keyforge.nativeiiq.source.NativeManagedAttributeInspector;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.JsonHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * READ-ONLY IIQ task that exports the ACTUAL native {@code sailpoint.object.ManagedAttribute} (entitlement)
 * source model to a JSON file, exactly as IdentityIQ exposes it — no SCIM, no REST, no KeyForge
 * {@code kf_entitlement} mapper/schema/terminology. It is a source-understanding utility, not a POC
 * detector and not part of the extraction pipeline; it writes no IIQ data and no PostgreSQL.
 *
 * <p>Deployed as a TaskDefinition whose {@code executor} is this class (see the importable definition in
 * {@code inspection/ManagedAttributeSourceInspection.xml}). IIQ supplies the live {@link SailPointContext}.
 *
 * <p>Optional task arguments:
 * <ul>
 *   <li>{@code outputFile} — absolute path for the JSON dump (default: {@code <java.io.tmpdir>/
 *       keyforge_managedattribute_source.json});</li>
 *   <li>{@code start} / {@code limit} — pagination; {@code limit<=0} (default) exports the full population;</li>
 *   <li>{@code includeXml} — include IIQ's native {@code XMLObjectFactory} serialization per record
 *       (default {@code true}); this is the authoritative persisted-name view;</li>
 *   <li>{@code echoStdout} — also print the full JSON to the server log (default {@code false}; a summary
 *       is always printed).</li>
 * </ul>
 */
public class NativeManagedAttributeInspectTask extends AbstractTaskExecutor {

    private volatile boolean terminated = false;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result,
                        Attributes<String, Object> args) throws Exception {
        int start = argInt(args, "start", 0);
        int limit = argInt(args, "limit", 0);           // 0 = full population
        boolean includeXml = argBool(args, "includeXml", true);
        boolean echoStdout = argBool(args, "echoStdout", false);
        String outputFile = arg(args, "outputFile");
        if (outputFile == null || outputFile.trim().isEmpty()) {
            outputFile = Paths.get(System.getProperty("java.io.tmpdir"),
                    "keyforge_managedattribute_source.json").toString();
        }

        Map<String, Object> envelope =
                new NativeManagedAttributeInspector(includeXml).inspect(context, start, limit);
        String json = JsonHelper.toJson(envelope, JsonHelper.JsonOptions.PRETTY_PRINT);  // pretty, nulls kept

        Path out = Paths.get(outputFile);
        writeFile(out, json);

        int count = intValue(envelope.get("count"));
        List<String> propNames = keysOf(envelope.get("native_getter_property_summary"));
        List<String> extKeys = stringList(envelope.get("native_extended_attribute_keys"));

        System.out.println("[KeyForge] ManagedAttribute source inspection: object_type="
                + envelope.get("object_type") + " count=" + count
                + " native_getter_properties=" + propNames.size()
                + " extended_attribute_keys=" + extKeys.size());
        System.out.println("[KeyForge] native getter properties: " + propNames);
        System.out.println("[KeyForge] extended attribute keys:  " + extKeys);
        System.out.println("[KeyForge] JSON written to: " + out.toAbsolutePath());
        if (echoStdout) {
            System.out.println(json);
        }

        if (result != null) {
            result.setAttribute("object_type", envelope.get("object_type"));
            result.setAttribute("count", Integer.valueOf(count));
            result.setAttribute("output_file", out.toAbsolutePath().toString());
            result.setAttribute("native_getter_property_count", Integer.valueOf(propNames.size()));
            result.setAttribute("native_getter_property_names", propNames);
            result.setAttribute("native_extended_attribute_keys", extKeys);
            result.setAttribute("include_xml", Boolean.valueOf(includeXml));
        }
    }

    @Override
    public boolean terminate() {
        this.terminated = true;
        return true;
    }

    private static void writeFile(Path out, String json) throws IOException {
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.write(out, json.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static List<String> keysOf(Object summary) {
        List<String> names = new ArrayList<String>();
        if (summary instanceof Map) {
            for (Object k : ((Map<String, Object>) summary).keySet()) {
                names.add(String.valueOf(k));
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object v) {
        List<String> out = new ArrayList<String>();
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private static int intValue(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static String arg(Attributes<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int argInt(Attributes<String, Object> args, String key, int dflt) {
        String s = arg(args, key);
        if (s == null) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static boolean argBool(Attributes<String, Object> args, String key, boolean dflt) {
        String s = arg(args, key);
        return s == null ? dflt : Boolean.parseBoolean(s.trim());
    }
}
