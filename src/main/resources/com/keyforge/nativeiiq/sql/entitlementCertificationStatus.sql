WITH certification_history AS
(
    SELECT
        ce.target_id,
        ce.target_name,
        ce.native_identity,

        ci.exception_application,
        ci.exception_attribute_name,
        ci.exception_attribute_value,

        c.id AS certification_id,
        c.name AS certification_name,

        ca.decision_date,
        ca.actor_name AS certifier,
        ca.status AS certification_decision,

        ROW_NUMBER() OVER
        (
            PARTITION BY
                ce.target_id,
                ci.exception_application,
                ce.native_identity,
                ci.exception_attribute_name,
                ci.exception_attribute_value

            ORDER BY
                ca.decision_date DESC NULLS LAST,
                c.created DESC
        ) AS rn

    FROM spt_certification_entity ce

    INNER JOIN spt_certification c
        ON c.id = ce.certification_id

    INNER JOIN spt_certification_item ci
        ON ci.certification_entity_id = ce.id

    LEFT JOIN spt_certification_action ca
        ON ca.id = ci.action

    WHERE
        ci.exception_application IS NOT NULL
        AND ci.exception_attribute_name IS NOT NULL
        AND ci.exception_attribute_value IS NOT NULL
)

SELECT
    i.name AS user_id,

    i.display_name AS user_name,

    mgr.name AS manager_user_id,

    mgr.display_name AS manager_name,

    app.name AS application_name,

    ie.native_identity AS account_name,

    ie.name AS entitlement_attribute,

    ie.value AS entitlement_value,

    to_timestamp(ie.created / 1000.0)
        AS entitlement_assigned_date,

    ch.certification_name
        AS last_certification_name,

    CASE
        WHEN ch.decision_date IS NOT NULL
        THEN to_timestamp(ch.decision_date / 1000.0)
        ELSE NULL
    END AS last_certified_date,

    ch.certifier
        AS certifier,

    ch.certification_decision
        AS certification_decision,

    CASE
        WHEN ch.certification_id IS NULL
        THEN 'YES'
        ELSE 'NO'
    END AS never_certified

FROM spt_identity_entitlement ie

INNER JOIN spt_identity i
    ON i.id = ie.identity_id

LEFT JOIN spt_identity mgr
    ON mgr.id = i.manager

LEFT JOIN spt_application app
    ON app.id = ie.application

LEFT JOIN certification_history ch
    ON ch.target_id = i.id

    AND ch.exception_application = app.name

    AND ch.exception_attribute_name = ie.name

    AND ch.exception_attribute_value = ie.value

    AND
    (
        ch.native_identity = ie.native_identity
        OR ch.native_identity IS NULL
    )

    AND ch.rn = 1

ORDER BY
    never_certified DESC,
    i.name,
    app.name,
    ie.native_identity,
    ie.name,
    ie.value
