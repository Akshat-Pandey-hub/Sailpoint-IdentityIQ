SELECT
    i.name AS "user_id",
    i.display_name AS "user_name",

    a.name AS "application_name",

    ie.native_identity AS "account_name",

    ie.name AS "entitlement_attribute",
    ie.value AS "entitlement_value",

    TO_CHAR(TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL(ie.created / 1000, 'SECOND'),
            'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"') AS "entitlement_assigned_date",
    TO_CHAR(TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL(ie.modified / 1000, 'SECOND'),
            'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"') AS "entitlement_modified_date",

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
    END AS "never_certified"

FROM spt_identity_entitlement ie

INNER JOIN spt_identity i
    ON i.id = ie.identity_id

INNER JOIN spt_application a
    ON a.id = ie.application

WHERE
(
    CAST(? AS NUMBER(19)) IS NULL
    -- Long.MIN_VALUE keeps all-null timestamp sets older than every representable modifiedAfter bound.
    OR GREATEST(NVL(ie.created, -9223372036854775808), NVL(ie.modified, -9223372036854775808),
                NVL(i.modified, -9223372036854775808), NVL(a.modified, -9223372036854775808)) > CAST(? AS NUMBER(19))
    OR EXISTS (
        SELECT 1
        FROM spt_certification_item ci2
        INNER JOIN spt_certification_entity ce2
            ON ce2.id = ci2.certification_entity_id
        WHERE (ce2.target_id = i.id OR ce2.target_name = i.name)
          AND ci2.exception_application = a.name
          AND (ce2.native_identity = ie.native_identity OR ce2.native_identity IS NULL)
          AND (
              ce2.created > CAST(? AS NUMBER(19)) OR ce2.modified > CAST(? AS NUMBER(19))
              OR ci2.created > CAST(? AS NUMBER(19)) OR ci2.modified > CAST(? AS NUMBER(19))
          )
    )
)

ORDER BY
    "never_certified" DESC,
    i.name,
    a.name,
    ie.native_identity,
    ie.name,
    ie.value,
    ie.id
