Goal (incl. success criteria):
- Xu ly cac bug CRITICAL trong BUG_REPORT_2026-04-07.md theo tung bug.

Constraints/Assumptions:
- Apply all rules under C:\Users\yasuo\Desktop\restaurant-server\rule.
- Update the ledger every turn; replies begin with Ledger Snapshot (Goal + Now/Next + Open Questions).
- Apply continuity-ledger-rule.mdc for every request.
- Work only within C:\Users\yasuo\Desktop\restaurant-server.
- Replies are in Vietnamese.
- Do not run DB or migration or server commands autonomously; ask the user to run.
- Do not run Prisma CLI; the user will run all Prisma CLI commands.

Key decisions:
- BUG-02 da xu ly bang X-Service-Token + strip tai gateway.
- BUG-03 da bo fallback JWT secret hardcode.
- BUG-04 da chan public rotate key khi ban dang co active session.
- BUG-01 dung @Transactional(isolation = Isolation.SERIALIZABLE) cho createReservation.

State:
  - Done:
    - Da them @Transactional(isolation = Isolation.SERIALIZABLE) vao createReservation trong TableService.
    - Compile pass table-service sau khi sua.
  - Now:
    - San sang user verify luong dat ban concurrent.
  - Next:
    - Tong ket cac bug CRITICAL da xu ly va chot buoc test nghiem thu.

Open questions (UNCONFIRMED if needed):
- None.

Working set (files/ids/commands):
- table-service/src/main/java/com/restaurant/tableservice/service/TableService.java
- .maven/apache-maven-3.9.6/bin/mvn.cmd -q -DskipTests compile
- CONTINUITY.md
