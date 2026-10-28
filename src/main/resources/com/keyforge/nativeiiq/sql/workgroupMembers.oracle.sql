SELECT
    wg.name AS "workgroup_name",
    u.name AS "user_name",
    u.display_name AS "display_name",
    u.firstname AS "first_name",
    u.lastname AS "last_name",
    u.email AS "email",
    CASE
        WHEN u.id IS NULL THEN 'No Members'
        WHEN u.inactive = 1 THEN 'Disabled'
        ELSE 'Active'
    END AS "user_status"
FROM spt_identity wg
LEFT JOIN spt_identity_workgroups iw
    ON wg.id = iw.workgroup
LEFT JOIN spt_identity u
    ON iw.identity_id = u.id
WHERE wg.workgroup = 1
  AND (
      CAST(? AS NUMBER(19)) IS NULL
      -- Long.MIN_VALUE keeps all-null timestamp sets older than every representable modifiedAfter bound.
      OR GREATEST(NVL(wg.modified, -9223372036854775808), NVL(wg.created, -9223372036854775808),
                  NVL(u.modified, -9223372036854775808), NVL(u.created, -9223372036854775808)) > CAST(? AS NUMBER(19))
  )
-- Member removals cannot be detected incrementally because the removed membership edge is deleted.
ORDER BY
    wg.name,
    u.inactive DESC,
    u.display_name,
    wg.id,
    u.id,
    iw.idx
