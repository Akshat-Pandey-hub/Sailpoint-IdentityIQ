package com.keyforge.iiq.user;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code usr} migration table, shaped by the fields the
 * IdentityIQ SCIM User resource actually returns (NOT by any ISPM/public table).
 * Every value is stored in its own typed column; arrays/objects are kept as JSON text
 * for JSONB columns so nothing is reduced or lost. There is no generic
 * {@code customattributes} dump.
 *
 * @param userid        SCIM {@code id} (canonical UUID) — primary key
 * @param username      SCIM {@code userName}
 * @param displayName   SCIM {@code displayName}
 * @param formattedName SCIM {@code name.formatted}
 * @param firstName     SCIM {@code name.givenName}
 * @param lastName      SCIM {@code name.familyName}
 * @param email         primary address from {@code emails} (convenience scalar), or null
 * @param emailsJson    the COMPLETE {@code emails} array as JSON text (all values + type/primary), or null
 * @param active        SCIM {@code active}
 * @param department    SailPoint extension {@code department}
 * @param employeeId    SailPoint extension {@code employeeId}
 * @param isManager     SailPoint extension {@code isManager}
 * @param riskScore     SailPoint extension {@code riskScore}
 * @param lastRefresh   SailPoint extension {@code lastRefresh}
 * @param capabilitiesJson SailPoint extension {@code capabilities} array as JSON text, or null
 * @param accountRefsJson  SailPoint extension {@code accounts} array as JSON text, or null
 * @param enterpriseAttributesJson enterprise-extension object as JSON text (when non-empty), or null
 * @param createdAt     {@code meta.created}
 * @param modifiedAt    {@code meta.lastModified}
 */
public record UserRow(
        String userid,
        String username,
        String displayName,
        String formattedName,
        String firstName,
        String lastName,
        String email,
        String emailsJson,
        Boolean active,
        String department,
        String employeeId,
        Boolean isManager,
        Integer riskScore,
        LocalDateTime lastRefresh,
        String capabilitiesJson,
        String accountRefsJson,
        String enterpriseAttributesJson,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
