# v1.5 lane/usage working branch
- This starter is isolated from roles/01 and 02. Do not merge into main until their schema and service interfaces agree.
- Java 21; Spring Boot 4.2.0 and ojdbc17, aligned with role 01's declared versions. Web MVC and technology-specific test starters replace Boot 3 dependencies. Verify both java -version and mvn -version report JDK 21 before compiling.
- Demo: mvn test; mvn spring-boot:run; http://localhost:8080/lanes, admin / local-demo-only (local loopback only). Set DEMO_ADMIN_PASSWORD to override. Oracle uses active MEMBER account with ADMIN role and BCrypt password_hash; team auth integration pending.
- Oracle initialization is disabled. Run sql/00_check_oracle.sql read-only first. sql/03_v15_schema_fresh_only.sql is ONLY for a separate empty test schema, not a migration of live data. Identity IDs assumed.
- Provisional: 14:00–midnight, no early start, 1–120 minute extension per request, early finish retains booking, NO_SHOW releases allocation, failed reallocation retains booking and flags manager; no automatic cancellation/recovery. Active failure ends existing usage without rewriting history; all-unused blocked until no-show policy agreed.
- Shared lock: all ten lanes ascending, then member and reservation. All scheduling writers must use the same order; reservation and closing teams not updated in this branch. Closing table/guard not implemented pending approval.
- The committed Boot 4.2.0 upgrade has not been built or tested against actual Oracle or H2 in the GitHub editing environment. Run tests before merging. Older docs describe the pre-v1.5 starter; use this document and new SQL as the branch baseline.
- Remaining: team agreement about reallocation for partially used booking, repair recovery, start/attendance and extension rules, no-show allocation, closing, production auth, migration of existing data, integration and concurrent load tests.


- If arrivals exceed capacity before the first usage, confirm attendance and add WAITING assignments using /usages/assign-lane; once capacity is sufficient, start usage. In-use additional lanes are assigned and started together.
