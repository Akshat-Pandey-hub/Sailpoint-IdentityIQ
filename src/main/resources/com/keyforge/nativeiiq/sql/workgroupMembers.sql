SELECT
    wg.name AS workgroup_name,
    u.name AS user_name,
    u.display_name AS display_name,
    u.firstname AS first_name,
    u.lastname AS last_name,
    u.email AS email,
    CASE
        WHEN u.id IS NULL THEN 'No Members'
        WHEN u.inactive = TRUE THEN 'Disabled'
        ELSE 'Active'
    END AS user_status
FROM spt_identity wg
LEFT JOIN spt_identity_workgroups iw
    ON wg.id = iw.workgroup
LEFT JOIN spt_identity u
    ON iw.identity_id = u.id
WHERE wg.workgroup = TRUE
ORDER BY
    wg.name,
    u.inactive DESC,
    u.display_name
