# POC Use-Case Status — Internal (meetings / tracking)

**Not for external sharing.** Status of the 16 use cases against the current IdentityIQ instance and native extraction layer.

Legend: **COMPLETE** (source facts extracted & validated) · **IMPLEMENTED / VALIDATION PENDING** · **BLOCKED (no instance data)** · **EXTERNAL / ENV-DEPENDENT**.

---

## A. COMPLETE — source-ready
| UC | Notes |
|----|-------|
| **REP010** Same description across entitlements | `kf_entitlement` description/app/attr/value/owner all extracted; detection is downstream analysis. |
| **REP011** Descriptions contain people names | Entitlement description + `kf_identity` name population extracted. |
| **TF-AM4** Entitlement description quality | Entitlement description extracted. |
| **UC-ENT-010** Privileged entitlement access path | Full identity→account→entitlement + role/request/approval/provisioning provenance + `iiqElevatedAccess` extracted. |
| **GIA-006** Multiple accounts, same entitlement | `kf_account_entitlement` + identity/account/app extracted. |
| **A-IGA-004** Orphan accounts | Link correlation/manually-correlated/state + app owner extracted. **Caveat:** confirm the authoritative service-account marker so real service accounts aren't flagged. |

## B. IMPLEMENTED / near-complete — need config evidence or live validation
| UC | Extracted | Remaining (NOT another big extractor) |
|----|-----------|----------------------------------------|
| **REP002** Approval group has no users | Workgroup + `kf_workgroup_member` + workflow config | Prove the actual **entitlement → approval-flow/workgroup applicability** (not a stored native relation — LCM/policy config). Also **verify `kf_workgroup_member` is populated live**. |
| **REP004** Level 2 empty | Workflow + approval config | Establish deployed **Level 1 / Level 2 semantics** from configuration (Phase-3 Debug review of the LCM workflow). |
| **REP017** Common people across approval workgroups | Workgroup/membership + approval-workgroup config | **Live membership validation** + agreed **concentration threshold** (stakeholder). |
| **REP020** Privileged without L2 approval | Privileged flag + approval config | Deployed **L2 interpretation** + confirm authoritative privileged classification. |
| **TF-DEL-001** Delimited app prioritization | App config/owner **+ new `kf_task_result` / `kf_task_schedule`** (native TaskResult/TaskSchedule) | **Implementation complete; live run pending.** Native build green (678/678 tests), packaged; not yet live-extracted. |

## C. BLOCKED — current IIQ instance has no data (one cert scenario unblocks all three)
| UC | State |
|----|-------|
| **UC-AR-004** Origin path for removed items | Extraction chain built; certification data empty in the instance. |
| **UC-ENT-007** Never-reviewed entitlements | Entitlement side exists; certification history/scope insufficient. |
| **TF-AM2** Access removal control | Provisioning/current-access side built; certification removal-decision side missing. |

> **Unblocker:** one targeted certification (1 identity, ≥1 item, 1 approve + 1 revoke, sign-off) populates `kf_certification`/`entity`/`item`/`archive` and validates all three.

## D. EXTERNAL / environment-dependent
| UC | Dependency |
|----|-----------|
| **UC-AR-008** Future leaver correlation | Authoritative future-leaver/termination attribute; if not in IIQ, from HR/lifecycle. **Do not infer from `inactive`.** |
| **TF-DEL-007** STS file submission schedule | Expected schedule may come from IIQ TaskSchedule/TaskResult; **actual file arrival is external** unless STS persists it into IIQ. |

---

## One-line rollup
- **Done (6):** REP010, REP011, TF-AM4, UC-ENT-010, GIA-006, A-IGA-004*
- **Working / near (5):** REP002, REP004, REP017, REP020, TF-DEL-001 (live run pending)
- **Blocked by missing cert data (3):** UC-AR-004, UC-ENT-007, TF-AM2
- **External/business (2):** UC-AR-008, TF-DEL-007

## Key technical notes for meetings
- **No JDBC needed** to fill the TaskResult/TaskSchedule gap — those objects are fully available via the native Java API. No verified JDBC candidate exists at this time.
- Remaining gaps are **configuration interpretation** (REP002/004/017/020), **missing certification data** (UC-AR-004/ENT-007/TF-AM2), **authoritative lifecycle data** (UC-AR-008), or **external STS evidence** (TF-DEL-007) — none are native-API defects.
- **Next actions:** (1) live-run `extract-native-task-result-db` + `extract-native-task-schedule-db`; (2) confirm `kf_workgroup_member` populated; (3) create one certification test scenario; (4) Phase-3 Debug review of the deployed LCM workflow for L1/L2.
