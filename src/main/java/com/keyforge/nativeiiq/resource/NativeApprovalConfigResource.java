package com.keyforge.nativeiiq.resource;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow;
import sailpoint.object.WorkItemConfig;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.SystemAdmin;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Read-only export of deployed LCM workflow approval declarations; never executes workflow code. */
@Path("keyForgeNativeIIQ")
public class NativeApprovalConfigResource extends BasePluginResource {
    private static final Logger LOG = Logger.getLogger(NativeApprovalConfigResource.class.getName());
    private static final String DEFAULT_FLOW = "EntitlementsRequest";
    private static final int MAX_APPROVAL_DEPTH = 16;
    private static final int MAX_SOURCE_CHARS = 12000;
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?im)(password|passwd|secret|token|credential|private[_ -]?key|client[_ -]?secret)(\\s*[=:]\\s*)(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\r\\n]*)");
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----");
    private static final Pattern FLOW_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    @Override public String getPluginName() { return "KeyForgeNativeIIQ"; }

    @GET
    @Path("approval-config")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApprovalConfig(@QueryParam("flow") @DefaultValue(DEFAULT_FLOW) String flow) {
        String stage = "validateFlow";
        try {
            String flowName = flow == null || flow.trim().isEmpty() ? DEFAULT_FLOW : flow.trim();
            if (!FLOW_NAME.matcher(flowName).matches()) {
                return Response.status(Response.Status.BAD_REQUEST).entity(error(400, "Invalid flow name")).type(MediaType.APPLICATION_JSON).build();
            }
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "readFlowConfiguration";
            Configuration configuration = context.getConfiguration();
            String key = "workflowLCM" + flowName;
            String workflowName = configuration == null ? null : configuration.getString(key);
            Map<String, Object> result = base(flowName, key, workflowName);
            if (workflowName == null || workflowName.trim().isEmpty()) {
                result.put("configurationFound", Boolean.FALSE);
                result.put("message", "The configuration key has no workflow value.");
                return Response.ok(result).type(MediaType.APPLICATION_JSON).build();
            }
            stage = "loadWorkflow";
            Workflow workflow = (Workflow) context.getObjectByName(Workflow.class, workflowName);
            if (workflow == null) {
                result.put("configurationFound", Boolean.TRUE);
                result.put("workflowFound", Boolean.FALSE);
                result.put("message", "The configured workflow name did not resolve to a Workflow object.");
                return Response.ok(result).type(MediaType.APPLICATION_JSON).build();
            }
            stage = "buildWorkflowTrace";
            result.put("configurationFound", Boolean.TRUE);
            result.put("workflowFound", Boolean.TRUE);
            result.put("workflow", workflow(workflow));
            result.put("readOnly", Boolean.TRUE);
            result.put("rulesExecuted", Boolean.FALSE);
            result.put("scriptsExecuted", Boolean.FALSE);
            return Response.ok(result).type(MediaType.APPLICATION_JSON).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "Approval config trace authorization denied at stage=" + stage, e);
            return Response.status(Response.Status.FORBIDDEN).entity(error(403, e.getClass().getName() + ": " + String.valueOf(e.getMessage()))).type(MediaType.APPLICATION_JSON).build();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Approval config trace failed at stage=" + stage, t);
            Map<String, Object> error = error(500, t.getClass().getName() + " at " + stage + ": " + String.valueOf(t.getMessage()));
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).type(MediaType.APPLICATION_JSON).build();
        }
    }

    private static Map<String, Object> base(String flow, String key, String workflowName) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("entity", "ApprovalConfiguration"); m.put("flow", flow);
        m.put("workflowConfigKey", key); m.put("workflowName", workflowName);
        return m;
    }

    private static Map<String, Object> workflow(Workflow w) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", w.getId()); m.put("name", w.getName()); m.put("description", w.getDescription());
        m.put("type", w.getType()); m.put("taskType", w.getTaskType() == null ? null : w.getTaskType().toString());
        m.put("handler", w.getHandler()); m.put("libraries", w.getLibraries());
        m.put("configForm", w.getConfigForm()); m.put("explicitTransitions", Boolean.valueOf(w.isExplicitTransitions()));
        List<Map<String, Object>> variables = new ArrayList<Map<String, Object>>();
        if (w.getVariableDefinitions() != null) {
            for (Workflow.Variable v : w.getVariableDefinitions()) {
                Map<String, Object> vm = new LinkedHashMap<String, Object>();
                vm.put("name", v.getName()); vm.put("type", v.getType());
                vm.put("input", Boolean.valueOf(v.isInput())); vm.put("output", Boolean.valueOf(v.isOutput()));
                vm.put("editable", Boolean.valueOf(v.isEditable())); vm.put("transient", Boolean.valueOf(v.isTransient()));
                boolean sensitive = sensitiveName(v.getName());
                vm.put("initializer", sensitive ? "<redacted>" : redact(v.getInitializer()));
                vm.put("script", sensitive ? null : script(v.getScript()));
                vm.put("initializerSource", sensitive ? null : scriptlet(v.getInitializerSource())); variables.add(vm);
            }
        }
        m.put("variables", variables);
        List<Map<String, Object>> rules = new ArrayList<Map<String, Object>>();
        if (w.getRuleLibraries() != null) for (Rule r : w.getRuleLibraries()) rules.add(rule(r));
        m.put("ruleLibraries", rules);
        List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
        if (w.getSteps() != null) {
            int index = 0;
            for (Workflow.Step s : w.getSteps()) {
                Map<String, Object> sm = new LinkedHashMap<String, Object>();
                sm.put("id", s.getId()); sm.put("name", s.getName()); sm.put("description", redact(s.getDescription()));
                sm.put("arrayIndex", Integer.valueOf(index++)); sm.put("action", redact(s.getAction()));
                sm.put("condition", redact(s.getCondition())); sm.put("conditionScript", script(s.getConditionScript()));
                sm.put("conditionSource", scriptlet(s.getConditionSource()));
                sm.put("script", script(s.getScript())); sm.put("actionSource", scriptlet(s.getActionSource()));
                sm.put("args", args(s.getArgs()));
                sm.put("transitions", transitions(s.getTransitions()));
                sm.put("approvalPresent", Boolean.valueOf(s.getApproval() != null));
                if (s.getApproval() != null) sm.put("approval", approval(s.getApproval(), 0, Collections.newSetFromMap(new IdentityHashMap<Workflow.Approval, Boolean>())));
                steps.add(sm);
            }
        }
        m.put("steps", steps); m.put("stepCount", Integer.valueOf(steps.size()));
        return m;
    }

    private static List<Map<String, Object>> transitions(List<Workflow.Transition> transitions) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (transitions == null) return out;
        for (Workflow.Transition t : transitions) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("to", redact(t.getTo())); m.put("when", redact(t.getWhen()));
            m.put("unconditional", Boolean.valueOf(t.isUnconditional())); m.put("script", script(t.getScript()));
            m.put("whenSource", scriptlet(t.getWhenSource())); m.put("toSource", scriptlet(t.getToSource())); out.add(m);
        }
        return out;
    }

    private static Map<String, Object> approval(Workflow.Approval a, int depth, Set<Workflow.Approval> seen) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", a.getName()); m.put("mode", redact(a.getMode())); m.put("modeScript", script(a.getModeScript()));
        m.put("modeSource", scriptlet(a.getModeSource())); m.put("owner", redact(a.getOwner()));
        m.put("ownerScript", script(a.getOwnerScript())); m.put("ownerSource", scriptlet(a.getOwnerSource()));
        m.put("afterScript", script(a.getAfterScript())); m.put("interceptorScript", script(a.getInterceptorScript()));
        m.put("parallel", Boolean.valueOf(a.isParallel())); m.put("any", Boolean.valueOf(a.isAny())); m.put("poll", Boolean.valueOf(a.isPoll()));
        m.put("parentName", a.getParent() == null ? null : a.getParent().getName());
        m.put("args", args(a.getArgs())); m.put("variables", safeMap(a.getVariables()));
        m.put("workItemConfig", workItemConfig(a.getWorkItemConfig()));
        List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
        if (!seen.add(a)) {
            m.put("children", children); m.put("cycleDetected", Boolean.TRUE); return m;
        }
        if (a.getChildren() != null) {
            if (depth >= MAX_APPROVAL_DEPTH) {
                Map<String, Object> marker = new LinkedHashMap<String, Object>(); marker.put("truncated", Boolean.TRUE); children.add(marker);
            } else for (Workflow.Approval child : a.getChildren()) {
                if (child == null) continue;
                children.add(approval(child, depth + 1, seen));
            }
        }
        m.put("children", children); return m;
    }

    private static Map<String, Object> workItemConfig(WorkItemConfig c) {
        if (c == null) return null;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", c.getId()); m.put("name", c.getName()); m.put("escalationStyle", c.getEscalationStyle());
        m.put("ownerRule", rule(c.getOwnerRule())); m.put("escalationRule", rule(c.getEscalationRule()));
        List<Map<String, Object>> owners = new ArrayList<Map<String, Object>>();
        if (c.getOwners() != null) for (Identity i : c.getOwners()) {
            if (i == null) continue;
            Map<String, Object> owner = new LinkedHashMap<String, Object>(); owner.put("id", i.getId());
            owner.put("name", i.getName()); owner.put("displayName", i.getDisplayName());
            owner.put("isWorkgroup", Boolean.valueOf(i.isWorkgroup())); owners.add(owner);
        }
        m.put("owners", owners); return m;
    }

    private static List<Map<String, Object>> args(List<Workflow.Arg> args) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (args != null) for (Workflow.Arg a : args) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            boolean sensitive = sensitiveName(a.getName());
            m.put("name", a.getName()); m.put("value", sensitive ? "<redacted>" : redact(a.getValue()));
            m.put("literal", sensitive ? "<redacted>" : safeScalar(a.getLiteral()));
            m.put("script", sensitive ? null : script(a.getScript()));
            m.put("valueSource", sensitive ? null : scriptlet(a.getValueSource())); out.add(m);
        }
        return out;
    }

    private static Map<String, Object> rule(Rule r) {
        if (r == null) return null;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", r.getId()); m.put("name", r.getName()); m.put("type", r.getType() == null ? null : r.getType().toString());
        m.put("language", r.getLanguage()); m.put("source", source(r.getSource()));
        return m;
    }

    private static Map<String, Object> script(Script s) {
        if (s == null) return null;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("language", s.getLanguage()); m.put("source", source(s.getSource())); return m;
    }

    private static Map<String, Object> scriptlet(Scriptlet s) {
        if (s == null) return null;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("string", redact(s.getString())); m.put("call", redact(s.getCall()));
        m.put("rule", redact(s.getRule())); m.put("reference", redact(s.getReference()));
        m.put("script", script(s.getScript())); return m;
    }

    private static Map<String, Object> source(String text) {
        if (text == null) return null;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        String cleaned = redact(text);
        m.put("text", cleaned.length() > MAX_SOURCE_CHARS ? cleaned.substring(0, MAX_SOURCE_CHARS) : cleaned);
        m.put("truncated", Boolean.valueOf(cleaned.length() > MAX_SOURCE_CHARS));
        m.put("sha256", sha256(text)); m.put("length", Integer.valueOf(text.length()));
        return m;
    }

    private static Map<String, Object> safeMap(Map<?, ?> input) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (input != null) for (Map.Entry<?, ?> e : input.entrySet()) {
            if (e.getKey() != null) {
                String key = String.valueOf(e.getKey());
                out.put(key, sensitiveName(key) ? "<redacted>" : safeScalar(e.getValue()));
            }
        }
        return out;
    }

    private static Object safeScalar(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return redact(value);
        if (value instanceof SailPointObject) {
            SailPointObject o = (SailPointObject) value; Map<String, Object> ref = new LinkedHashMap<String, Object>();
            ref.put("id", o.getId()); ref.put("name", o.getName()); return ref;
        }
        return redact(String.valueOf(value));
    }

    private static String redact(String value) {
        if (value == null) return null;
        String assignmentRedacted = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1$2<redacted>");
        return PRIVATE_KEY_BLOCK.matcher(assignmentRedacted).replaceAll("<redacted-private-key>");
    }

    private static boolean sensitiveName(String value) {
        return value != null && Pattern.compile("(?i).*(password|passwd|secret|token|credential|private[_ -]?key).*" )
                .matcher(value).matches();
    }

    private static Object redact(Object value) { return value instanceof String ? redact((String) value) : value; }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) { return null; }
    }

    private static Map<String, Object> error(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("entity", "ApprovalConfiguration"); m.put("status", Integer.valueOf(status)); m.put("message", message); return m;
    }
}
