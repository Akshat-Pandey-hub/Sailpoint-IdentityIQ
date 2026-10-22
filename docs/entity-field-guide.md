# KeyForge IIQ: Entity Field Guide

Covers the core identity-governance tables in the `iiq_native` schema. For each one: what it is in SailPoint, what IIQ's own UI shows, what every column means, a real record, whether our tool's UI should show it, and the problems the data reveals.

- **Data:** `D:\iiq_native.sql` (pg_dump; extraction runs 2026-10-06 and 2026-10-09).
- **UI comparison:** live IIQ at `preview.keyforge.ai/identityiq`, viewed 2026-10-16.
- **Note:** IIQ ran an Identity Refresh on 2026-10-15, after these extractions. Re-extract before trusting the numbers in a demo.

**UI legend:**
- **Show:** goes in lists, cards and headers.
- **Detail:** goes on a drill-down or "more info" panel.
- **Hide:** stays in the DB, never shown.

---

## 0. Columns that are always hidden

These appear on most tables. They are ours, not SailPoint's. Never put them in the UI.

| Column(s) | What it is |
|---|---|
| `<entity>id` (the uuid primary key, e.g. `userid`, `entitlementid`) | Our key, derived from the IIQ id. Use it for joins only. |
| `source_id` | IIQ's internal 32-hex id. Use it only to build a "Open in IIQ" deep link. |
| `record_hash`, `source_hash` | Change-detection hashes (ours / IIQ's) |
| `source_system`, `source_interface`, `source_object_type`, `src_system`, `src_interface`, `src_object_type`, `src_object_id`, `src_natural_key` | Lineage: where and how the row was read |
| `extraction_run_id`, `extracted_at` | Which run wrote the row. Optionally show one "Data as of" date per page. |
| `is_deleted`, `deleted_at` | Our soft delete. Use as a filter (`is_deleted = false`), never as a column. |

---

## How the entities fit together

```
                        kf_application (6)
                     /         |          \
          kf_account (94)   kf_entitlement (37)   kf_role_entitlement (1)
          (Link)            (ManagedAttribute)        |
             |    \              |                 kf_role (4) ── kf_role_hierarchy (1)
             |   kf_account_entitlement (410)         |
             |   (what the account HAS on target)     |
             |                                        |
         kf_identity (68) ── kf_identity_role (12) ───┘
             |    \
             |   kf_identity_entitlement (424)  (IIQ's per-identity access record, with "how granted")
             |   kf_entitlement_assignment (440) (SQL report: access + the request that touched it)
             |
         kf_workgroup (6) ── kf_workgroup_member (2) / kf_workgroup_members (6)
```

**Join keys:**
- `identity_id = kf_identity.source_id`
- `application_id = kf_application.source_id`
- `link_id = kf_account.source_id`
- `role_id` / `source_role_id = kf_role.source_id`. Watch out: some tables store `role_id` as a uuid and `source_role_id` as hex.
- Entitlements join to the catalog on `(application_name, attribute = attribute_name, value = attribute_value)`.

---

## 1. Identity: `kf_identity` (68 rows)

See the chat walkthrough for the full table. In short:

- **Show:** `displayable_name`, `name`, `first_name`, `last_name`, `email`, `inactive` (as status), `type`, `manager_name`, `correlated` (as "HR-linked / Orphan"), `score`, `attributes.department`, `attributes.status`.
- **Detail:** `created_at`, `modified_at`, `last_refresh`, `last_login`, `capabilities`, `controlled_scopes`, `administrator_name`, `is_protected`, `role_requests`, `mitigation_expirations`.
- **Hide:** `display_name`, `full_name`, the embedded jsonb lists (`accounts`, `assigned_roles`, `detected_roles`, `role_assignments`, `role_detections`; use the relationship tables instead), `manager_status`, `is_needs_refresh`, `is_correlated_overridden`, `is_workgroup`, `auth_*`, `pending_refresh_workflow`, `password_expiration`, `auth_lock_start`, `use_by`.

**IIQ UI (View Identity):** Attributes shows only User Name, First Name, Last Name, Email, Manager, Type. The other tabs are Entitlements, Application Accounts, Policy, History, Risk, Activity, User Rights and Events.

---

## 2. Entitlement: `kf_entitlement` (37 rows)

**SailPoint object:** `sailpoint.object.ManagedAttribute`, the **Entitlement Catalog**. One row is one grantable thing on one application: an LDAP group, an Entra group, and so on. It describes the entitlement itself, not who has it.

**IIQ UI:**
- **Entitlement Catalog list** columns: Application, Attribute, Display Name, Value, Type, Description, Owner, Requestable, Classifications, Modified.
- **Edit page tabs:**
  - *Standard Properties:* Application, Type, Attribute, Value, Display Value, Requestable, Elevated Access, Description, Owner.
  - *Object Properties:* the group's attributes on the target.
  - *Members:* identities that hold it.
  - *Access:* permissions.
  - *Classifications.*

**Real records:**
- *LDAP, good catalog entry:* `application_name = corp directory-LDAP-Target`, `attribute = posixgroups`, `value = cn=devs,ou=rocktar,dc=moli,dc=org`, `display_name = devs`, `type = posixgroup`, `requestable = t`, `aggregated = t`, `descriptions = {en_US: "Standard department access"}`, `attributes = {cn: devs, dn: …, gidNumber: 5001, memberUid: …}`. In IIQ, the Members tab shows 10 holders.
- *Entra, poor catalog entry:* `application_name = EntraTarget`, `attribute = groups`, `value = 01ade120-f080-4096-aeb0-7fc3e023a1cc`, `display_name = null`, so `displayable_name` is the GUID. `aggregated = f`, no description, no owner.

| Column | Real values | Meaning | In IIQ UI | Our UI |
|---|---|---|---|---|
| `displayable_name` | `devs`, or a GUID | Display name if set, else the value | "Display Name" column | **Show** (main label) |
| `value` | `cn=devs,…` / GUID | Exact value stored on the account in the target system | "Value" | **Show** (secondary, small text) |
| `display_name` | set on only 5/37 | Explicit display name | "Display Value" field | Hide (use `displayable_name`) |
| `application_name` / `application_id` | EntraTarget 15, EntraAuth 15, LDAP 7 | Owning application | "Application" | **Show** |
| `attribute` | `groups` 30, `posixgroups` 7 | Account attribute that carries this entitlement | "Attribute" | **Show** |
| `type` | `group` 30 / `posixgroup` 7 | Schema object type | "Type" | **Show** |
| `descriptions` | `{en_US: "Standard department access"}` | Localized descriptions (plain `description` is always null) | "Description" | **Show** (`en_US`) |
| `owner_name` / `owner_id` | `Adminq` on 2 rows | Entitlement owner (approver or certifier); can be a workgroup | "Owner" | **Show** (gap: 35/37 have no owner) |
| `requestable` | all true | Can be requested in Access Requests | "Requestable" | **Show** |
| `iiq_elevated_access` | all false | Privileged-access flag | "Elevated Access" | **Show** as a badge when true |
| `classification_names` / `_display_names` / `classifications` | empty | Tags such as "Privileged" or "SOX" | "Classifications" | **Show** when present |
| `aggregated` | true 5 / false 32 | true = read from the target by group aggregation; false = auto-created from an account value | Not shown | Detail ("Source: Group aggregation / Seen on accounts") |
| `attributes` | `{cn, dn, gidNumber, memberUid, sysDescriptions}` | The group object's own attributes on the target | "Object Properties" tab | Detail |
| `created_at` / `modified_at` / `last_refresh` | | Catalog timestamps | "Modified" | Detail |
| `is_inactive` | all false | Disabled in the catalog | Not shown | Show as a badge when true |
| `native_identity` | same as `value` | The group's id on the target | No | Hide |
| `reference_attribute`, `full_name` | duplicates | Copies of `attribute` / `value` | No | Hide |
| `is_group`, `is_group_type`, `is_permission`, `uncorrelated`, `is_auto_promotion`, `is_differencable` | | IIQ internal type and processing flags | No | Hide |
| `permissions`, `target_permissions`, `inheritance`, `associations`, `member_attribute`, `purview`, `ma_uuid`, `instance`, `name`, `last_target_aggregation` | all empty | Unused on this instance | No | Hide |

**Related:**
- Who holds it: `kf_account_entitlement`, `kf_identity_entitlement`.
- Which roles grant it: `kf_role_entitlement`.
- Why a person has it: `kf_entitlement_assignment`.
- Last certified: `kf_entitlement_certification_status`.

**Findings and fixes:**
1. **The 30 Entra groups have no names.** They are GUIDs only, `aggregated = false`, no description. IIQ's own catalog shows the same GUIDs. **Fix in IIQ:** run *Account Group Aggregation* on EntraTarget and EntraAuth. Their group schema has `displayName`, so names will fill in.
2. **Every Entra group appears twice** (EntraAuth and EntraTarget): the same tenant is registered as two applications. Counts double: 390 Entra entitlement rows = 13 people × 15 groups × 2. **Our UI:** pick one application, or merge rows by `value`.
3. **LDAP `devs` and `ops` each exist twice**: the DN form (aggregated, named) and a bare short-name form (`devs`, `aggregated = f`). Some accounts carry both values. **Fix:** clean up the bare values on the LDAP side, or normalize to the DN in our UI.
4. No owners or classifications on 35 of 37 entries, so certifications and approvals have no owner to route to.

---

## 3. Role: `kf_role` (4 rows)

**SailPoint object:** `sailpoint.object.Bundle`.
- **Business** roles are what people get (assigned by rule or request).
- **IT** roles bundle the actual entitlements.

**IIQ UI (Role Management → Role Viewer):** Name, Display Name, Owner, Scope, Type, Description, Classifications, Elevated Access, "This role is enabled", Role Statistics, Assignment Rule (script), Direct Entitlements, Inherited Roles, Inherited Entitlements.

**Real records:**

| name | displayable_name | type | assignment rule (from `selector`) | grants |
|---|---|---|---|---|
| Engineering-Base | Engineering-Base | business | `department == "Engineering"` | **nothing** (no entitlements) |
| IT Operations Employee | IT Operations Employee | business | `department == "IT Operations"` | **nothing** |
| HR Employee | HR Employee | business | `department == "HR"` | inherits HR IT Role → `posixgroups = hr` |
| hritrole | HR IT Role | it | none | `posixgroups containsAll cn=hr,…` |

| Column | Real values | Meaning | In IIQ UI | Our UI |
|---|---|---|---|---|
| `displayable_name` | HR IT Role | Display name, else name | Tree label | **Show** |
| `name` | hritrole | Unique role name | "Name" | Show (secondary) |
| `type` / `role_type_definition` | business 3 / it 1 | Role type | "Type" | **Show** |
| `descriptions` | `{en_US: "Birthright role for Engineering employees"}` | Description (plain `description` is always null) | "Description" | **Show** |
| `owner_name` | `spadmin` | Owner's identity **name**. IIQ shows the display name "Molly J". | "Owner" | **Show**, joined to `kf_identity.displayable_name` |
| `selector` / `has_selector` | Beanshell script on `department` | Assignment rule: who gets the role automatically | "Assignment Rule" | **Show** as plain words ("Auto-assigned when department = Engineering"). Hide the raw script. |
| `selector_summary` | null | Readable rule summary (only for filter-type rules) | No | Use when present |
| `applications` | `["corp directory-LDAP-Target"]` on hritrole | Applications the role provisions to | No | Detail |
| `iiq_elevated_access` | false | Privileged role | "Elevated Access" | Show as a badge |
| `activation_date` / `deactivation_date` | null | Scheduled enable / disable | No | Detail when set |
| `created_at` / `modified_at` | | | No | Detail |
| `risk_score_weight` | 0 | Risk weight | No | Detail |
| `allow_duplicate_accounts`, `allow_multiple_assignments`, `auto_promotion`, `differencable`, `merge_templates`, `or_profiles`, `pending_delete`, `activity_enabled`, `assignment_id`, `attributes`, `monitored_applications`, `scorecard`, `full_name`, `display_name` | | Role-engine settings and duplicates | No | Hide |

**Related:**
- Parent and child roles: `kf_role_hierarchy`.
- Entitlements it grants: `kf_role_entitlement`.
- Who has it: `kf_identity_role`.

**Findings and fixes:**
1. **Two of the three birthright roles grant no access.** Engineering-Base (9 people) and IT Operations Employee (2) have no IT role and no entitlements. People "have" the role, but it provisions nothing. **Fix in IIQ:** add required IT roles (for example devs or ops).
2. **No enabled/disabled column.** IIQ shows "This role is enabled" (`Bundle.isDisabled()`), and `scope` and `classifications` are also not extracted. **Fix in our extractor:** add `disabled`, `scope` and `classifications`.

---

## 4. Role hierarchy: `kf_role_hierarchy` (1 row)

**What it is:** edges between roles, read from `Bundle.getInheritance()`, `getRequirements()` and `getPermits()`.
- `relationship_type` is `INHERITANCE`, `REQUIREMENT` or `PERMIT`.
- Read each row as: **`role_name` → (relationship) → `related_role_name`**.

**Real record:** `role_name = HR Employee`, `relationship_type = INHERITANCE`, `related_role_name = hritrole`. HR Employee inherits everything HR IT Role grants.

**IIQ UI:** HR Employee shows "Inherited Roles" and "Inherited Entitlements" (`posixgroups = hr`, from HR IT Role). In the Top Down tree, HR Employee is nested under HR IT Role.

| Column | Meaning | Our UI |
|---|---|---|
| `role_name` / `role_id` / `source_role_id` | The role that has the relationship | **Show** |
| `relationship_type` | INHERITANCE = includes its access; REQUIREMENT = must also have; PERMIT = may also request | **Show** ("inherits", "requires", "permits") |
| `related_role_name` / `related_role_id` / `related_role_source_id` | The other role | **Show** |
| lineage columns | | Hide |

**Use in our UI:** a role tree, and an effective-access view: a role's own entitlements plus those of every role it inherits or requires.

---

## 5. Role-Entitlement: `kf_role_entitlement` (1 row)

**What it is:** which entitlements a role grants, read from the role's **profiles** (`Bundle.getProfiles()`). A profile is "on application X, the account must match filter F".

**Real record:**
- `role_name = hritrole`, `application_name = corp directory-LDAP-Target`, `entitlement_type = PROFILE_CONSTRAINT`
- `attribute_name = posixgroups`, `filter_operation = CONTAINS_ALL`, `filter_value = ["cn=hr,ou=rocktar,dc=moli,dc=org"]`
- `filter_expression = posixgroups.containsAll({"cn=hr,…"})`

**IIQ UI:** HR IT Role → Direct Entitlements: Application, Property, Value.

| Column | Meaning | Our UI |
|---|---|---|
| `role_name` | Role granting the access | **Show** |
| `application_name` | Target application | **Show** |
| `attribute_name` | Entitlement attribute ("Property" in IIQ) | **Show** |
| `filter_value` | The value(s) required. Join to `kf_entitlement.value` to get the display name (`hr`). | **Show** (via the catalog name) |
| `filter_operation` | CONTAINS_ALL / EQ / … | Detail |
| `entitlement_type` | PROFILE_CONSTRAINT (filter) vs PROFILE_PERMISSION (permission) | Hide (a word in Detail at most) |
| `filter_expression` | Raw IIQ filter text | Hide |
| `attribute_value` | Used for single-value EQ filters; null here | Use when set |
| `permission_*` | For permission-type profiles; empty here | Hide unless present |
| `profile_ordinal`, `constraint_path`, `entitlement_id` | Position in the role definition | Hide |

---

## 6. Application: `kf_application` (6 rows)

**SailPoint object:** `sailpoint.object.Application`, a connected system.

**IIQ UI (Application Definition):** tabs are Details (Name, Owner, Revoker, Type, Description, Authoritative, Case Insensitive, Native Change Detection, Maintenance), Configuration (connection settings and schema), Correlation, Accounts, Risk, Activity Data Sources, Rules and Password Policy.

**Real records:**

| name | type | authoritative | provisioning | aggregates | account correlation |
|---|---|---|---|---|---|
| FlatFile-HR-Source | DelimitedFile (SFTP csv) | **yes** | no | account | `employeeId = employeeId` |
| EntraAuth | Azure Active Directory | **yes** | yes | account, group | `employeeId = uid` |
| EntraTarget | Azure Active Directory | no | yes | account, group | `employeeId = uid` |
| FlatFile | DelimitedFile | **yes** | no | account | **none** |
| App_Admin_Directory | JDBC | no | yes | account | **none** |
| corp directory-LDAP-Target | OpenLDAP - Direct | no | yes | account, group | `employeeId = uid` |

| Column | Real values | Meaning | In IIQ UI | Our UI |
|---|---|---|---|---|
| `name` | | Application name | Name | **Show** |
| `type` | DelimitedFile / Azure Active Directory / JDBC / OpenLDAP - Direct | Connector type | Application Type | **Show** |
| `authoritative` | 3 true | Source of truth that creates identities (HR) | Authoritative Application | **Show** ("Source of truth" badge) |
| `owner_name` | spadmin | Application owner | Owner (shows "Molly J") | **Show**, via identity display name |
| `description` / `descriptions` | all null | | Description | Show (all empty) |
| `supports_provisioning` | 4 true | IIQ can write changes to it | No | **Show** ("Read-only / Read-write") |
| `aggregation_types` | account / account, group | What IIQ reads from it | No | **Show** |
| `in_maintenance` | false | Maintenance window active | Maintenance Enabled | Show as a badge when true |
| `account_correlation_config` | XML: `employeeId = uid` | How accounts are matched to identities | Correlation tab | **Detail**, summarized as "Matches account `uid` to identity `employeeId`" |
| `schemas` | Account and group attribute lists, entitlement flags | What data the app carries | Configuration → Schema | **Detail** (account attributes, entitlement attributes) |
| `features_string` | AUTHENTICATE, PROVISIONING, PASSWORD, ENABLE… | Connector capabilities | No | Detail (as chips) |
| `remediators` | spadmin | Who handles manual revocations | Revoker | Detail |
| `case_insensitive`, `native_change_detection_enabled`, `activity_enabled`, `sync_provisioning`, `supports_*`, `logical`, `composite`, `authentication_resource`, `manages_other_apps` | | Connector behavior flags | Some on Details | Hide |
| `connector` | `sailpoint.connector.AzureADConnector` | Java class | No | Hide |
| `attributes` | host, file path, clientID, delta tokens, `<redacted>` secrets | **Connection configuration** | Configuration tab | **Hide, always.** Credentials are already redacted by our mapper, but hosts and tokens are still sensitive. |
| all `*_rule` / `*_rule_id`, `provisioning_config`, `manager_correlation_*`, `*_account_filter`, `dependencies`, `secondary_owners`, `profile_class`, `proxied_name`, `cluster` | all empty | Rule hooks and advanced configuration | Rules tab | Hide (Detail only if set) |
| `icon` | enterpriseIcon… | IIQ icon name | Yes | Hide (use our own icons) |
| `score` | 0 | Application risk score | Risk tab | Detail |

**Related:** `kf_account` (accounts), `kf_entitlement` (catalog), `kf_role.applications` and `kf_role_entitlement` (roles that provision here).

**Findings and fixes:**
1. **Three authoritative sources with no shared key.** HR (12 people), EntraAuth (13) and FlatFile (10) each create their own identities, so nobody exists in more than one. EntraAuth correlates on `uid`, but the Entra schema has no `uid` attribute.
2. **App_Admin_Directory has no correlation configured**, so all 24 of its accounts became orphan identities. FlatFile has no correlation either.
3. **Manager correlation is not configured on the HR source.** The HR csv has a `manager` column (for example "Wei Tanaka"), but `manager_correlation_rule` and `manager_correlation_filter` are empty, so no identity has a manager. **Fix in IIQ:** set Manager Correlation on FlatFile-HR-Source.
4. Entra appears twice: EntraAuth and EntraTarget point at the same tenant.

---

## 7. Account: `kf_account` (94 rows)

**SailPoint object:** `sailpoint.object.Link`. One user account on one application, linked to one identity.

**IIQ UI:** Identity → Application Accounts lists Application, Account Name, Status, Last Refresh. Expanding a row shows the account's attributes.

**Real records (Liam Nguyen):**
- *HR account:* `application_name = FlatFile-HR-Source`, `native_identity = App_IDJ0001001`, `attributes = {status: Active, manager: "Wei Tanaka", hireDate: 2025-09-20, jobTitle: Coordinator, department: Engineering, …}`.
- *LDAP account:* `application_name = corp directory-LDAP-Target`, `native_identity = uid=App_IDJ0001001,ou=rocktar,dc=moli,dc=org`, `display_name = Liam Nguyen`, `manually_correlated = t`, `has_entitlements = t`, `entitlement_attributes = {posixgroups: [cn=devs,…, devs]}`.

| Column | Real values | Meaning | In IIQ UI | Our UI |
|---|---|---|---|---|
| `application_name` | 6 apps | | Application | **Show** |
| `displayable_name` | Liam Nguyen | Account display name | Account Name | **Show** |
| `native_identity` | DN / Entra objectId / employeeId | Unique account id on the target | Expanded detail | **Show** (secondary) |
| `identity_name` / `identity_id` | | Owning identity | Page context | **Show** (link to identity) |
| `disabled` | 2 true | Account disabled on the target | Status (Active / Disabled) | **Show** |
| `locked` | all false | Locked on the target | Status | **Show** |
| `manually_correlated` | 13 true (LDAP) | An admin linked this account by hand | No | **Show** as a badge ("Manually linked") |
| `last_refresh` | | Last time aggregation saw this account | Last Refresh | **Show** |
| `created_at` | | First time IIQ saw the account | No | Detail |
| `significant_modified` / `prior_significant_modified` | | Last real change to the account's attributes | No | Detail ("Last changed") |
| `attributes` | full attribute map | Everything the target returned | Expanded detail | **Detail**: pick per-app keys (for HR: jobTitle, department, manager, status, hireDate) |
| `entitlement_attributes` | `{posixgroups: [...]}` | Entitlement-bearing attributes only | No | Hide (use `kf_account_entitlement`) |
| `has_entitlements` | 38 true | Has at least one entitlement | No | Hide (derivable) |
| `iiq_disabled`, `iiq_locked` | | IIQ's copies of disabled / locked | No | Hide (duplicates) |
| `display_name` | | Duplicate | No | Hide |
| `composite`, `component_ids`, `link_uuid`, `instance`, `permissions`, `target_permissions`, `attribute_metadata`, `last_target_aggregation` | empty | Unused | No | Hide |
| `modified_at` | | IIQ object timestamp | No | Hide (use `significant_modified`) |

**Findings and fixes:**
1. The HR account holds `manager`, `jobTitle`, `hireDate` and `termDate`. **None of them reach the identity** (no identity mapping). **Fix in IIQ:** add Identity Mappings.
2. The two terminated employees (`App_IDJ0001005`, `App_IDJ0001009`) have their LDAP account `disabled = t`, but **still hold the LDAP group**. See §10.

---

## 8. Identity-Role: `kf_identity_role` (12 rows)

**What it is:** who holds which role. Read from `Identity.getRoleAssignments()` (`ASSIGNED`) and `getRoleDetections()` (`DETECTED`).

**IIQ UI:** Identity → Entitlements → Roles grid shows Name, Description, Classifications, **Assigned By**, **Allowed By**, **Acquired**, Application, Account Name.

**Real record:** `identity_name = App_IDJ0001001`, `role_name = Engineering-Base`, `relationship_type = ASSIGNED`, `targets = [{applicationName: corp directory-LDAP-Target, nativeIdentity: uid=App_IDJ0001001,…}]`, `promoted_soft_permit = t`. `assigner`, `assigned_date`, `source`, `start_date` and `end_date` are all **null**.

| Column | Meaning | Our UI |
|---|---|---|
| `identity_name` / `identity_id` | Who | **Show** |
| `role_name` / `role_id` | Which role | **Show** |
| `relationship_type` | ASSIGNED (given) vs DETECTED (inferred from entitlements they already have) | **Show** ("Assigned / Detected") |
| `source` | Rule / LCM / UI / … | **Show** ("How": birthright rule, request, manual). Null here; use the fallback below. |
| `assigner`, `assigned_date` | Who assigned it and when | **Show** when present |
| `start_date` / `end_date` | Sunrise / sunset | **Show** when present |
| `targets` | Which account(s) the role provisions to | Detail |
| `negative` | Explicitly denied role | Show as a badge when true |
| `manual` | Manually assigned | Detail |
| `detection_date`, `detection_assignment_ids` | For DETECTED rows | Detail |
| `comments` | | Detail |
| `assignment_id` | Assignment key | Hide |
| `future_assignment`, `promoted_soft_permit` | Role-engine flags | Hide |

**Fallback for "how":** `kf_identity_entitlement` has the same role as a row with `attribute_name = assignedRoles` and `source = Rule`, `assignment_id`, `created_at = 2026-08-03`. Use that to fill "Assigned by rule on 2026-08-03" while `kf_identity_role.source` is null.

---

## 9. Identity-Entitlement: `kf_identity_entitlement` (424 rows)

**SailPoint object:** `sailpoint.object.IdentityEntitlement`. IIQ's own per-identity record of each piece of access (entitlements **and** role assignments), with *how it was granted* and its request and certification links. This is what feeds IIQ's Identity → Entitlements tab.

**IIQ UI:** Identity → Entitlements (Direct): Attribute, Entitlement, Classifications, Application, Account Name, Instance, Last Certification, Last Certification Date, **Has pending request**, **Request ID**.

**Real records:**
- *Found on target:* `App_IDJ0001001`, `application_name = corp directory-LDAP-Target`, `attribute_name = posixgroups`, `attribute_value = devs`, `type = Entitlement`, `source = Aggregation`, `connected = t`, `aggregation_state = Connected`.
- *Requested:* `App_IDJ0001002`, `cn=managers,…`, `source = LCM`, `assigned = t`, `assigner = Molly J`, `request_item_id = 7f00…19db`, `aggregation_state = Disconnected`.
- *Role row:* `App_IDJ0001001`, `attribute_name = assignedRoles`, `attribute_value = Engineering-Base`, `source = Rule`, no application.

| Column | Real values | Meaning | Our UI |
|---|---|---|---|
| `identity_name` | 26 identities | Who | **Show** |
| `application_name` | null for role rows | Where | **Show** |
| `attribute_name` | groups 390 / posixgroups 22 / **assignedRoles 12** | Entitlement attribute. `assignedRoles` means this row is a role, not an entitlement. | **Show**; split the list into Roles vs Entitlements |
| `attribute_value` | | The value. Join to the catalog for its name. | **Show** (catalog `displayable_name`) |
| `display_name` | account display name | **Account** name (not the entitlement name) | Show as "Account" |
| `native_identity` | | Account id | Detail |
| `source` | Aggregation 409 / Rule 12 / LCM 3 | **How it was granted:** found on target / birthright rule / access request | **Show** ("How granted"). The most useful column here. |
| `assigned` | 3 true | Explicitly assigned (requested), so IIQ will keep re-provisioning it | **Show** ("Assigned" vs "Found") |
| `granted_by_role` | all false | Comes from a role | **Show** when true |
| `assigner` | The Administrator / Molly J | Who assigned it | **Show** |
| `aggregation_state` / `connected` | Connected 410 / **Disconnected 2** | Disconnected = IIQ thinks they should have it, but it is **not on the target** | **Show** as a red badge |
| `request_item_id` / `pending_request_item_id` | 3 / 0 | Link to the access request | **Show** (link) |
| `pending_certification_item_id` / `certification_item_id` | 45 / 0 | In an open certification | **Show** ("Under review") |
| `start_date` / `end_date` | empty | Sunrise / sunset | Show when set |
| `allowed` | false | Allowed by a certification exception | Show as a badge when true |
| `created_at` | | When IIQ first recorded the access | Detail ("Since") |
| `value_list`, `type`, `source_object`, `assignment_id`, `annotation`, `assignment_note`, `source_assignable_roles`, `source_detected_roles`, `instance`, `modified_at` | | Duplicates or internal | Hide |

**Findings:**
1. **2 Disconnected access records.** `EMP00001` and `App_IDJ0001002` were granted `managers` by request (Success in IIQ), but the group is not on their LDAP account. This matches `kf_provisioning_txn`, where all 104 transactions are Failed. All of them target corp directory-LDAP-Target, with these errors:
   - 46 Failed with no error text.
   - 10 ConnectorException.
   - 10 ObjectNotFound (`uid=App_IDJ0001011,…` does not exist).
   - 13 "Invalid name: App_IDJ00010xx". IIQ sent the bare uid instead of the DN.
   - 1 "department: attribute type undefined".
   - 1 "No revoke attribute specified".
2. 45 entitlements sit in an open certification (`pending_certification_item_id`), matching the 45 items in `kf_certification_item`.

---

## 10. Account-Entitlement: `kf_account_entitlement` (410 rows)

**What it is:** what each account **actually has on the target**, flattened from `Link.getEntitlementAttributes()` (plus permissions). It is the raw truth from aggregation and carries no "why".

**IIQ UI:** not a page of its own. It's the entitlement list inside an expanded account, and the Members tab of a catalog entry.

**Real record:** `link_id = 7f00…19f6` (Liam's LDAP account), `identity_name = App_IDJ0001001`, `application_name = corp directory-LDAP-Target`, `attribute_name = posixgroups`, `attribute_value = cn=devs,ou=rocktar,dc=moli,dc=org`.

| Column | Meaning | Our UI |
|---|---|---|
| `identity_name` / `identity_id` | Owner of the account | **Show** |
| `application_name` / `native_identity` / `link_id` | Which account | **Show** (app + account name) |
| `attribute_name` / `attribute_value` | The entitlement. Join to the catalog for its name. | **Show** |
| `type` | ATTRIBUTE / PERMISSION / TARGET_PERMISSION (null = attribute) | Hide unless permissions exist |
| `permission_*` | Rights / target for permission-type access; empty here | Hide unless present |
| `instance`, `application_id` | | Hide |

**Account-Entitlement vs Identity-Entitlement:**
- `kf_account_entitlement` = "what the **target** says". 410 rows: groups 390 + posixgroups 20.
- `kf_identity_entitlement` = "what **IIQ** says, and why". 424 rows: 410 + 12 role rows + 2 Disconnected.

The difference between the two is the reconciliation story:
- In IIQ but not on the target: **should have, doesn't** (failed provisioning).
- On the target and `source = Aggregation`: **has it, never requested** (possible excess access).

**Real leaver gap:** terminated `App_IDJ0001005` still has `posixgroups = cn=devs` on a disabled LDAP account. IIQ's devs Members tab lists them too.

---

## 11. Entitlement-Assignment: `kf_entitlement_assignment` (440 rows)

**What it is:** **not a SailPoint object.** It's the result of our business SQL report (Query 1), run verbatim on IIQ's own tables (`spt_identity_entitlement` joined to `spt_identity_request*` and role tables) through IIQ's JDBC connection.
- It explains each piece of access in plain words: `assignment_type` and `assignment_reason`.
- It attaches the **request** that touched it.
- `source_interface = native_iiq_jdbc`.

**Real records:**
- `user_id = App_IDJ0001001`, `entitlement_value = cn=devs,…`, `entitlement_source = Aggregation`, `assignment_type = TARGET ASSIGNED / AGGREGATED`, `assignment_reason = "Entitlement is present on the target and was detected during aggregation."`
- `user_id = App_IDJ0001002`, `entitlement_value = cn=managers,…`, `assignment_type = DIRECT ENTITLEMENT REQUEST`, `access_request_id = 0000000009`, `request_type = AccessRequest`, `request_operation = Add`, `request_status = Success`, `provisioning_state = Finished`, `requested_by = Molly J`.

| Column | Meaning | Our UI |
|---|---|---|
| `user_name` / `user_id` | Display name / identity name | **Show** |
| `application_name`, `account_name` | Where | **Show** |
| `entitlement_attribute`, `entitlement_value` | What | **Show** (catalog name) |
| `assignment_type` | TARGET ASSIGNED / AGGREGATED, DIRECT ENTITLEMENT REQUEST, (role-based types when roles grant access) | **Show** ("How granted") |
| `assignment_reason` | One-sentence explanation | **Show** as a tooltip or sub-text |
| `entitlement_source`, `assigned`, `granted_by_role`, `entitlement_assigner` | Raw source flags | Detail |
| `access_request_id`, `request_type`, `request_operation`, `requested_by`, `request_date`, `request_status`, `provisioning_state` | The request that touched this access | **Show** in a "Request history" sub-list |
| `role_*` | The granting role; empty here because no role grants these entitlements | Show when present |
| `entitlement_created` / `entitlement_modified` / `request_date` | **Epoch milliseconds stored as text** (e.g. `1753586846258`) | Show, **after converting to a date** |
| `id`, `identity_id`, `identity_request_id`, `entitlement_assignment_id` | Keys | Hide |

**Warning: rows are access × request, not unique access.** `App_IDJ0001005 / cn=devs` appears **9 times**: 9 leaver "Remove" requests. Some say Success/Finished, others Incomplete/Failed or Pending/Failed, yet the group is still there. `App_IDJ0001009 / cn=ops` is the same.
- **Our UI:** group by `(identity, application, attribute, value)` for the access list. Show the requests as that item's history.
- **This is the clearest leaver-failure evidence we have:** 9 removal attempts, the group still present.

---

## 12. Workgroup: `kf_workgroup` (6 rows)

**SailPoint object:** `sailpoint.object.Identity` with `workgroup = true`. A named group of identities used as an owner, approver or certifier, and to hand out admin capabilities.

**IIQ UI:**
- *Group Configuration → Workgroups list:* Name, Description, Modified.
- *Edit Workgroup:* Name, Owner, **Description**, Group Email, Notification Setting, Capabilities, Members.

**Real records:**

| name | owner | description (IIQ UI) | description (ours) | capabilities | members |
|---|---|---|---|---|---|
| AdminCap | spadmin | **spadminq** | **null** | 43 (full admin) | spadmin |
| Adminq | spadmin | **spadminq** | **null** | 43 | spadmin |
| RapidSetup Error Notification | spadmin | | null | RapidSetupConfigurationReadOnly | none |
| RapidSetup No Manager | none | | null | | none |
| AttributeSyncWorkGroup | none | | null | | none (`systemUser = true`) |
| SelfRegistrationWorkGroup | none | | null | | none (`systemUser = true`) |

| Column | Meaning | In IIQ UI | Our UI |
|---|---|---|---|
| `displayable_name` / `name` | Workgroup name | Name | **Show** |
| `owner_name` | Owner identity name (IIQ shows "Molly J") | Owner | **Show**, via identity display name |
| `description` | Description | Description | **Show**. **Currently always null: extractor bug, see below.** |
| `email` | Group email | Group Email | Show |
| `notification_option` | Both / GroupEmailOnly / MembersOnly / Disabled | Notification Setting | Detail |
| `capabilities` | Admin rights every member inherits | Capabilities | **Show** (count + list; flag full-admin groups) |
| `attributes.systemUser` | true = IIQ system workgroup | No | Use it to hide system workgroups by default |
| `inactive`, `type`, `is_workgroup`, `display_name`, `attributes` (other keys), `created_at`, `modified_at` | | Some | Hide (`modified_at` in Detail) |

**Related:**
- Members: `kf_workgroup_member`.
- What it owns: `kf_entitlement.owner_name = Adminq` (2 entitlements). It can also own applications, roles and policies.

**Findings and fixes:**
1. **Extractor bug:** `NativeWorkgroupMapper` reads the description from `attributes.description`, but IIQ stores it elsewhere. The UI shows "spadminq" while we store null. **Fix in our code**, then re-run `extract-native-workgroup-db`.
2. Two workgroups (AdminCap, Adminq) each grant all 43 admin capabilities to spadmin. Duplicate admin paths are worth flagging in an access review.

---

## 13. Workgroup members: two tables

| | `kf_workgroup_member` (2 rows) | `kf_workgroup_members` (6 rows) |
|---|---|---|
| Source | Java API, `Identity.getWorkgroups()` (`native_iiq_java_api`) | Business SQL report on `spt_identity_workgroups` (`native_iiq_jdbc`) |
| Shape | **One row per real membership** (edge table with ids) | **One row per workgroup**, with member details flattened in; empty groups get a placeholder row (`user_status = No Members`) |
| Real rows | AdminCap ← spadmin, Adminq ← spadmin | AdminCap/spadmin/Molly J/Active, Adminq/spadmin/…, plus 4 × "No Members" |
| Keys | `workgroup_id`, `identity_id` (uuid) | Names only (`workgroup_name`, `user_name`) |
| Extracted | 2026-10-09 | 2026-10-06 |

**IIQ UI:** Edit Workgroup → Members shows Name, First Name, Last Name.

**Columns:**
- `kf_workgroup_member`:
  - **Show:** `member_name`, `first_name`, `last_name`, `member_is_workgroup` (nested workgroup badge).
  - **Hide:** ids, `src_*`, `record_hash`.
- `kf_workgroup_members`:
  - **Show:** `workgroup_name`, `display_name`, `email`, `user_status`.
  - **Hide:** `id` and the lineage columns.

**Recommendation for our UI:** drive member lists from **`kf_workgroup_member`**, which has real ids and joins cleanly to `kf_identity`. Left-join from `kf_workgroup` to show empty groups. Keep `kf_workgroup_members` as the report-style export, or retire it. The two tables hold the same facts.

---

## 14. Screens we can build

| Screen | Built from | What it answers |
|---|---|---|
| **Identity profile** | identity + account + identity_entitlement + identity_role + entitlement catalog | Who is this person, and what can they access? |
| **"Why does X have Y?"** | `kf_identity_entitlement.source` + `kf_entitlement_assignment.assignment_type/reason` + request history | Requested / birthright rule / found on target, with the request trail |
| **Entitlement catalog (readable)** | `kf_entitlement` + holder counts from `kf_account_entitlement` | What exists, who owns it, how many hold it. Flag unnamed Entra GUIDs and missing owners. |
| **Role explorer** | role + role_hierarchy + role_entitlement + identity_role | What each role grants (including inherited) and who has it. Flag roles that grant nothing. |
| **Application overview** | application + account counts + correlation summary | Authoritative or not, read or write, how accounts link, orphan count |
| **Leaver check** | identity `attributes.status = Terminated` + account `disabled` + account_entitlement + entitlement_assignment requests | Terminated but still holding access, and how many removal attempts failed |
| **Should-have vs has** | identity_entitlement (Disconnected) vs account_entitlement | Granted in IIQ but missing on target, and present on target but never requested |
| **Admin rights review** | identity.capabilities + workgroup.capabilities + workgroup_member | Who effectively has admin, and through which workgroup |

## 15. Fix list

**In IIQ (then re-aggregate, refresh and re-extract):**
1. Account Group Aggregation on EntraTarget and EntraAuth, so the Entra groups get names.
2. Manager Correlation on FlatFile-HR-Source (`manager` column).
3. Identity Mappings for `jobTitle`, `hireDate`, `termDate` and `manager`; map `status = Terminated` to inactive.
4. Correlation for App_Admin_Directory and FlatFile, to fix the 24 + orphan identities.
5. Give Engineering-Base and IT Operations Employee real IT roles.
6. Fix LDAP provisioning. All 104 provisioning transactions failed on corp directory-LDAP-Target:
   - IIQ sends a bare uid where the connector needs a DN ("Invalid name").
   - Accounts don't exist (ObjectNotFound).
   - The provisioning policy writes `department`, which isn't in the LDAP schema.
   - There's no revoke attribute.

   Until this is fixed, leaver removals and access requests never land.
7. Owners and classifications on catalog entries.

**In our code:**
1. Workgroup `description` is read from the wrong place (`NativeWorkgroupMapper`).
2. Role extractor: add `disabled`, `scope` and `classifications`.
3. `kf_entitlement_assignment`: store the dates as `timestamptz` instead of epoch-ms text, or convert them in the API/UI layer.
4. UI layer: dedupe the Entra apps and the LDAP DN/short-name pairs, and group entitlement-assignment rows by access.
