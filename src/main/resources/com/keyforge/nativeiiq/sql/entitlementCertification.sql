SELECT
    i.name AS user_id,
    i.display_name AS user_name,

    a.name AS application_name,

    ie.native_identity AS account_name,

    ie.name AS entitlement_attribute,
    ie.value AS entitlement_value,

    to_timestamp(ie.created / 1000.0) AS entitlement_assigned_date,
    to_timestamp(ie.modified / 1000.0) AS entitlement_modified_date,

    CASE
        WHEN EXISTS
        (
            SELECT 1

            FROM spt_certification_item ci

            INNER JOIN spt_certification_entity ce
                ON ce.id = ci.certification_entity_id

            WHERE
                (
                    ce.target_id = i.id
                    OR ce.target_name = i.name
                )

                AND ci.exception_application = a.name

                AND ci.exception_attribute_name = ie.name

                AND ci.exception_attribute_value = ie.value

                AND
                (
                    ce.native_identity = ie.native_identity
                    OR ce.native_identity IS NULL
                )
        )
        THEN 'NO'
        ELSE 'YES'
    END AS never_certified

FROM spt_identity_entitlement ie

INNER JOIN spt_identity i
    ON i.id = ie.identity_id

INNER JOIN spt_application a
    ON a.id = ie.application

ORDER BY
    never_certified DESC,
    i.name,
    a.name,
    ie.native_identity,
    ie.name,
    ie.value
