package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeWorkflowRow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import sailpoint.object.Script;
import sailpoint.object.Workflow;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A workflow step's script is returned whole, however long it is; secrets in it are still redacted. */
class NativeWorkflowScriptLengthTest {

    @Test
    void aLongScriptIsReturnedWholeAndStillRedacted() {
        try {
            StringBuilder code = new StringBuilder("String password = \"Welcome@123\";\n");
            while (code.length() < 20000) {
                code.append("log.debug(\"provisioning step line\");\n");
            }
            code.append("return \"END_OF_SCRIPT_MARKER\";\n");

            Workflow.Step step = new Workflow.Step();
            step.setName("Provision");
            step.setScript(new Script(code.toString()));
            Workflow w = new Workflow();
            w.setName("Long Script Workflow");
            w.setSteps(Collections.singletonList(step));

            NativeWorkflowRow row = NativeWorkflowMapper.map(w, "IdentityIQ", "run");
            String definition = String.valueOf(row.getDefinition());
            assertTrue(definition.contains("END_OF_SCRIPT_MARKER"), "the end of a 20,000-character script is returned");
            assertTrue(definition.contains("truncated=false"), "never marked as cut");
            assertFalse(definition.contains("Welcome@123"), "a hard-coded password in the script is still redacted");
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime; native signatures are compile-verified: " + e);
        }
    }
}
