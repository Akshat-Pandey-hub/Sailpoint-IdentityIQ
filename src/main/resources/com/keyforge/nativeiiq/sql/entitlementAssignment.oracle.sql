SELECT DISTINCT
    i.id AS "identity_id",
    i.name AS "user_id",
    i.display_name AS "user_name",
    app.name AS "application_name",
    ie.native_identity AS "account_name",
    ie.name AS "entitlement_attribute",
    ie.value AS "entitlement_value",
    CASE WHEN ie.assigned = 1 THEN 'true' WHEN ie.assigned = 0 THEN 'false' END AS "assigned",
    CASE WHEN ie.granted_by_role = 1 THEN 'true' WHEN ie.granted_by_role = 0 THEN 'false' END AS "granted_by_role",
    ie.source AS "entitlement_source",
    ie.assignment_id AS "entitlement_assignment_id",
    ie.assigner AS "entitlement_assigner",
    ie.created AS "entitlement_created",
    ie.modified AS "entitlement_modified",
    b.id AS "role_id",
    b.name AS "role_name",
    b.display_name AS "role_display_name",
    b.type AS "role_type",
    NULL AS "role_requestable",
    ir.id AS "identity_request_id",
    ir.name AS "access_request_id",
    ir.type AS "request_type",
    ir.requester_display_name AS "requested_by",
    ir.created AS "request_date",
    ir.completion_status AS "request_status",
    iri.operation AS "request_operation",
    iri.provisioning_state AS "provisioning_state",

    CASE
        WHEN ie.granted_by_role = 1
             AND b.id IS NOT NULL
             AND ir.id IS NOT NULL
             AND (
                    iri.name = b.name
                    OR iri.value = b.name
                    OR iri.value = b.id
                 )
        THEN 'REQUESTED ROLE'

        WHEN ie.granted_by_role = 1
             AND b.id IS NOT NULL
             AND NOT EXISTS (
                 SELECT 1
                 FROM spt_identity_request_item iri2
                 INNER JOIN spt_identity_request ir2
                     ON ir2.id = iri2.identity_request_id
                 WHERE ir2.target_id = i.id
                   AND (
                        iri2.name = b.name
                        OR iri2.value = b.name
                        OR iri2.value = b.id
                   )
                   AND (
                        iri2.operation = 'Add'
                        OR iri2.operation = 'RoleAdd'
                        OR iri2.operation = 'Assign'
                   )
             )
        THEN 'BIRTHRIGHT / AUTOMATIC ROLE'

        WHEN NVL(ie.granted_by_role, 0) = 0
             AND ie.assigned = 1
             AND UPPER(ie.source) = 'LCM'
             AND EXISTS (
                 SELECT 1
                 FROM spt_identity_request_item iri3
                 INNER JOIN spt_identity_request ir3
                     ON ir3.id = iri3.identity_request_id
                 WHERE ir3.target_id = i.id
                   AND iri3.native_identity = ie.native_identity
                   AND (
                        iri3.name = ie.name
                        OR iri3.name = ie.value
                        OR iri3.value = ie.value
                   )
                   AND (
                        iri3.operation = 'Add'
                        OR iri3.operation = 'EntitlementAdd'
                        OR iri3.operation = 'Assign'
                   )
             )
        THEN 'DIRECT ENTITLEMENT REQUEST'

        WHEN NVL(ie.granted_by_role, 0) = 0
             AND NVL(ie.assigned, 0) = 0
        THEN 'TARGET ASSIGNED / AGGREGATED'

        WHEN NVL(ie.granted_by_role, 0) = 0
             AND ie.assigned = 1
        THEN 'DIRECT IIQ ASSIGNMENT - REVIEW'

        ELSE 'REVIEW'
    END AS "assignment_type",

    CASE
        WHEN ie.granted_by_role = 1
             AND b.id IS NOT NULL
             AND ir.id IS NOT NULL
             AND (
                    iri.name = b.name
                    OR iri.value = b.name
                    OR iri.value = b.id
                 )
        THEN 'Entitlement is provided by role: ' || b.name || '. Matching LCM request found.'

        WHEN ie.granted_by_role = 1
             AND b.id IS NOT NULL
             AND NOT EXISTS (
                 SELECT 1
                 FROM spt_identity_request_item iri2
                 INNER JOIN spt_identity_request ir2
                     ON ir2.id = iri2.identity_request_id
                 WHERE ir2.target_id = i.id
                   AND (
                        iri2.name = b.name
                        OR iri2.value = b.name
                        OR iri2.value = b.id
                   )
                   AND (
                        iri2.operation = 'Add'
                        OR iri2.operation = 'RoleAdd'
                        OR iri2.operation = 'Assign'
                   )
             )
        THEN 'Entitlement is provided by role: ' || b.name || '. No matching LCM role request found.'

        WHEN ie.assigned = 1
             AND UPPER(ie.source) = 'LCM'
        THEN 'Entitlement was directly assigned through Lifecycle Manager.'

        WHEN NVL(ie.assigned, 0) = 0
             AND NVL(ie.granted_by_role, 0) = 0
        THEN 'Entitlement is present on the target and was detected during aggregation.'

        ELSE 'Assignment source requires additional review.'
    END AS "assignment_reason"

FROM spt_identity_entitlement ie

INNER JOIN spt_identity i
    ON i.id = ie.identity_id

LEFT JOIN spt_application app
    ON app.id = ie.application

LEFT JOIN spt_identity_bundles ib
    ON ib.identity_id = i.id

LEFT JOIN spt_bundle b
    ON b.id = ib.bundle

LEFT JOIN spt_profile p
    ON p.bundle_id = b.id

LEFT JOIN spt_profile_constraints pc
    ON pc.profile = p.id

LEFT JOIN spt_identity_request_item iri
    ON iri.native_identity = ie.native_identity
   AND (
        iri.name = ie.name
        OR iri.name = ie.value
        OR iri.value = ie.value
        OR iri.name = b.name
        OR iri.value = b.name
        OR iri.value = b.id
   )
   AND EXISTS (
        SELECT 1
        FROM spt_identity_request ir_match
        WHERE ir_match.id = iri.identity_request_id
          AND ir_match.target_id = i.id
   )

LEFT JOIN spt_identity_request ir
    ON ir.id = iri.identity_request_id

WHERE
(
    NVL(ie.granted_by_role, 0) = 0
    OR (
        ie.granted_by_role = 1
        AND pc.elt IS NOT NULL
        AND pc.elt LIKE '%' || ie.value || '%'
    )
)
AND
(
    CAST(? AS NUMBER(19)) IS NULL
    -- Long.MIN_VALUE keeps all-null timestamp sets older than every representable modifiedAfter bound.
    OR GREATEST(NVL(ie.created, -9223372036854775808), NVL(ie.modified, -9223372036854775808),
                NVL(i.modified, -9223372036854775808), NVL(app.modified, -9223372036854775808),
                NVL(b.modified, -9223372036854775808), NVL(p.created, -9223372036854775808),
                NVL(p.modified, -9223372036854775808), NVL(ir.created, -9223372036854775808),
                NVL(ir.modified, -9223372036854775808), NVL(iri.created, -9223372036854775808),
                NVL(iri.modified, -9223372036854775808)) > CAST(? AS NUMBER(19))
    OR EXISTS (
        SELECT 1
        FROM spt_identity_request irx
        WHERE irx.target_id = i.id
          AND (irx.created > CAST(? AS NUMBER(19)) OR irx.modified > CAST(? AS NUMBER(19)))
    )
)

ORDER BY
    "user_id",
    "application_name",
    "entitlement_value",
    "role_name",
    "request_date" DESC,
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
    11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
    21, 22, 23, 24, 25, 26, 27, 28, 29
