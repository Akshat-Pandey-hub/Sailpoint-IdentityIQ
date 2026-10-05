SELECT DISTINCT
    i.id AS identity_id,
    i.name AS user_id,
    i.display_name AS user_name,
    app.name AS application_name,
    ie.native_identity AS account_name,
    ie.name AS entitlement_attribute,
    ie.value AS entitlement_value,
    ie.assigned,
    ie.granted_by_role,
    ie.source AS entitlement_source,
    ie.assignment_id AS entitlement_assignment_id,
    ie.assigner AS entitlement_assigner,
    ie.created AS entitlement_created,
    ie.modified AS entitlement_modified,
    b.id AS role_id,
    b.name AS role_name,
    b.display_name AS role_display_name,
    b.type AS role_type,
    NULL AS role_requestable,
    ir.id AS identity_request_id,
    ir.name AS access_request_id,
    ir.type AS request_type,
    ir.requester_display_name AS requested_by,
    ir.created AS request_date,
    ir.completion_status AS request_status,
    iri.operation AS request_operation,
    iri.provisioning_state AS provisioning_state,

    CASE
        WHEN ie.granted_by_role = TRUE
             AND b.id IS NOT NULL
             AND ir.id IS NOT NULL
             AND (
                    iri.name = b.name
                    OR iri.value = b.name
                    OR iri.value = b.id
                 )
        THEN 'REQUESTED ROLE'

        WHEN ie.granted_by_role = TRUE
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

        WHEN (ie.granted_by_role = FALSE OR ie.granted_by_role IS NULL)
             AND ie.assigned = TRUE
             AND UPPER(COALESCE(ie.source,'')) = 'LCM'
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

        WHEN (ie.granted_by_role = FALSE OR ie.granted_by_role IS NULL)
             AND (ie.assigned = FALSE OR ie.assigned IS NULL)
        THEN 'TARGET ASSIGNED / AGGREGATED'

        WHEN (ie.granted_by_role = FALSE OR ie.granted_by_role IS NULL)
             AND ie.assigned = TRUE
        THEN 'DIRECT IIQ ASSIGNMENT - REVIEW'

        ELSE 'REVIEW'
    END AS assignment_type,

    CASE
        WHEN ie.granted_by_role = TRUE
             AND b.id IS NOT NULL
             AND ir.id IS NOT NULL
        THEN CONCAT(
             'Entitlement is provided by role: ',
             b.name,
             '. Matching LCM request found.'
        )

        WHEN ie.granted_by_role = TRUE
             AND b.id IS NOT NULL
        THEN CONCAT(
             'Entitlement is provided by role: ',
             b.name,
             '. No matching LCM role request found.'
        )

        WHEN ie.assigned = TRUE
             AND UPPER(COALESCE(ie.source,'')) = 'LCM'
        THEN 'Entitlement was directly assigned through Lifecycle Manager.'

        WHEN (ie.assigned = FALSE OR ie.assigned IS NULL)
             AND (ie.granted_by_role = FALSE OR ie.granted_by_role IS NULL)
        THEN 'Entitlement is present on the target and was detected during aggregation.'

        ELSE 'Assignment source requires additional review.'
    END AS assignment_reason

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

LEFT JOIN spt_identity_request ir
    ON ir.id = iri.identity_request_id

WHERE
(
    ie.granted_by_role = FALSE
    OR ie.granted_by_role IS NULL
    OR (
        ie.granted_by_role = TRUE
        AND pc.elt IS NOT NULL
        AND pc.elt LIKE CONCAT('%', ie.value, '%')
    )
)

ORDER BY
    i.name,
    app.name,
    ie.value,
    b.name,
    ir.created DESC
