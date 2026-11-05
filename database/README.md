# Supabase setup

Use a free Supabase PostgreSQL project. Apply the numbered migrations `001_initial.sql` through `008_admin_delegation.sql` in order, each once. For an existing project, apply only the migrations not already installed. Migrations run inside transactions and preserve existing accounts, requests, documents, and messages. They create no sample data.

## First administrator

Before the intended administrator registers, run this as the database owner with their actual email substituted:

```sql
insert into certiflow_private.admin_bootstrap(email) values ('YOUR_REAL_ADMIN_EMAIL');
```

The designated person registers through the app and verifies their email. The private bootstrap row can be claimed once. User-provided metadata and role selection cannot assign staff access. The currently connected project already has its designated administrator email reserved.

Administrators assign access through **Menu → Accounts**. Find an email-verified account, choose **Student**, **Recruiter / Viewer**, or **Teacher**, and press **Update account access**. After migration 008, the original administrator can also choose **Admin** and confirm the promotion, or select another role to remove another administrator's access. Grant Admin only to trusted platform staff: it allows review of student records, NOC decisions, and management of Student, Viewer and Teacher access.

`set_account_role(target_id, new_role)` keeps the same signature and accepts `STUDENT`, `VIEWER`, `TEACHER`, or `ADMIN`. Only the confirmed current ADMIN recorded in `certiflow_private.admin_bootstrap.claimed_by` can grant or remove administrator access. Other administrators retain Student/Viewer/Teacher management and cannot change an administrator. The original account is protected, and no caller can change their own role. This authority follows the stored user ID rather than a client email string; the existing bootstrap email and claimed account are unchanged by the migration.

Public signup accepts `account_type=VIEWER`; all other values create a student. Supplied staff roles and verification metadata are ignored. Later changes to authentication metadata cannot change the trusted profile role.

Administrators can mark a profile's institutional identity reviewed with `verify_identity(target_id)`. They should check the person's actual institutional details first. This verifies the displayed name, department, and student/staff ID; biographies, skills, programme, links, and portfolio claims remain self-reported. Changing name, department, student/staff ID, or account email automatically clears identity verification. Profile edits cannot change roles or the verification flag.

## Portfolio entries, recipients, and messages

`portfolio_projects` groups documents into editable Project, Certificate, Achievement, Experience, or Document entries. Entries have a title, description, optional HTTP(S) link, and optional staff recipient. Each `portfolios` row remains one file and can reference a project through `project_id`. Existing files keep their original IDs, names, storage paths, and ownership; their project remains empty until handled by the app. Every upload still uses the private 25 MB bucket.

- Students see and manage their own portfolio entries and files. Teachers see only entries specifically addressed to them. An entry without a recipient is private to its owner and administrators.
- NOC requests can name a staff recipient. The owner, assigned teacher, and administrators can read assigned requests; only the assigned staff member or an administrator can decide them. Unassigned requests remain in the shared staff review queue. One pending request of each type per student remains enforced.
- Recipients must be real, email-verified teachers or administrators. The server validates UUIDs instead of trusting typed display names. The small `contact_directory()` RPC exposes only ID, name, role, and department. Students see staff contacts; staff see confirmed accounts. Students cannot enumerate other students' profiles or emails.
- Messages are private to their sender and recipient. Students and viewers can communicate with staff; staff can communicate with any verified account. Administrators cannot read conversations they are not part of. Only the receiving account can set read receipts through `mark_messages_read(other_id)`.
- Message bodies are limited to 4,000 characters and each sender to 30 messages per minute. Sender identity, timestamps, and read receipts are server-controlled. Senders can edit only their own message body through `edit_message`, which locks the row and rejects stale original text. Direct message updates and sender impersonation remain forbidden.
- Removing a portfolio entry cascades to its attachment metadata. The app must then delete those now-unreferenced storage objects using the Storage API; SQL must not directly delete storage metadata. Removing a single file follows the same metadata-then-storage sequence. Storage policies allow owners to clean up only their own unreferenced objects.

All of these rules are enforced through database privileges, row-level security, and checked RPCs. Hiding a UI button or changing the desktop client never grants additional access. If a teacher is later demoted, administrators retain access to submissions formerly assigned to them and can resolve the requests; demotion does not silently share a private portfolio with other teachers.

Migration 004 fixes a PostgreSQL policy-expansion cycle between attachment inserts and storage reads. The storage policy uses `can_read_portfolio_file(object_name)`, an owner-executed helper with an empty search path that checks exact attachment/project rows and the caller's current database role. It permits only the file owner, an administrator, or staff assigned to that project. This avoids recursive policy expansion while preserving private access; unassigned teachers gain no file access.

## Client configuration

Migration 005 adds a private conversation inbox, stable `(sent_at, id)` cursor paging, and sender-only message edits. Paging anchors must belong to the caller's conversation. Historical conversations remain readable after role changes; current roles still control new messages. Anonymous users cannot invoke these chat RPCs.

Migration 006 adds owner-controlled `share_publications` and `share_files`. Raw publication tables are inaccessible to clients. Checked functions return only curated preview fields and selected file metadata. `community_shares` requires a signed-in account; `get_public_share` accepts an unguessable token and returns only LINK/BOTH publications. Existing rows remain unpublished. Changing to private, removing the source, or removing a selected attachment revokes the corresponding access. Republishing after revocation does not reuse an old link.

The private Storage bucket stays private. The additional publication policy allows only selected files from a current publication, and only the authenticated-object download operation; it does not permit anonymous bucket listing. Community-only files require a signed-in profile. RLS still applies to downloads even when a caller knows a file path. Downloaded copies cannot be recalled. The static viewer in `docs` uses only the publishable key and exposes no private source rows or approval controls.

Migration 007 adds a separate VIEWER role for recruiters and other visitors. One login reads the stored server role; no role hint from the client controls access. Viewers can read community snapshots and selected files, edit their own profile, and chat with staff. They cannot see private student records, approve requests, create student submissions, assign roles, or publish student work. Existing teacher/admin review permissions and the reserved administrator bootstrap remain unchanged. A former student converted to VIEWER retains owner-authorized revocation of earlier public links through `unpublish_share`.

Migration 008 adds original-administrator delegation without changing any existing role. `account_capabilities()` returns only the boolean `can_manage_admins` field for the signed-in caller; it exposes no owner identity or audit records. Role assignment locks the actor and target profiles in a consistent order and rechecks administrator authority before updating. Targets must have verified email addresses. A profile trigger protects the original administrator from demotion.

Every actual role change is recorded in the private `certiflow_private.role_change_audit` table with actor ID, target ID, previous/new role and timestamp. Database/bootstrap changes have a null actor rather than an invented identity. Client roles have no table access; update, delete and truncate are rejected. Repeating the same role creates no duplicate event, and rejected requests leave no role change or audit entry. Application administrators cannot browse or edit this audit table.

The ADMIN role remains for trusted platform staff. VIEWER registration does not verify a company. Whole-profile public pages and recruiter-to-student contact requests are separate future features; Discover currently lists opted-in item publications.

Copy `certiflow.properties.example` to `certiflow.properties`. Set the HTTPS project URL and **publishable** key from the project dashboard. Never distribute a PostgreSQL password or Supabase secret/service-role key. RLS enforces access independently of the desktop client's selected role.

## Email

Keep email confirmation enabled. Configure Authentication → Emails → SMTP Settings with your mail provider. SMTP credentials belong in Supabase's encrypted configuration, never in the Java app or repository.

For Gmail, use `smtp.gmail.com`, port `465`, the sender's full Gmail address as username, and a Google App Password created by the account owner after enabling 2-Step Verification. Gmail has sending and deliverability limits; use a dedicated transactional provider for larger deployments.

Set the confirmation subject to `Your Certiflow verification code` and the recovery subject to `Your Certiflow password reset code`. Paste the corresponding HTML files from `email-templates`. They use `{{ .Token }}` so the desktop app does not depend on browser redirect handlers.

## Verification

Run `verify_rls.sql` and `verify_collaboration.sql` as the owner. Both create isolated authorization fixtures inside a transaction and roll everything back. The first checks ownership, role escalation, forged submissions, duplicate pending requests, staff review, and missing-object rejection. The second checks assignment visibility and decisions, project/file ownership, storage download permissions, recipient changes, administrator-only roles and identity review, identity-reset behavior, private conversations, sender impersonation, and read receipts. It inserts temporary storage metadata only; it uploads no files and sends no mail. Neither script leaves sample accounts or records behind. Never remove the rollback.

After migrations 005 and 006, also run `verify_chat_history.sql` and `verify_public_sharing.sql`. They check private history, message edits and conflicts, owner-only publication, excluded private fields, community/public audience separation, selected-file scope, blocked anonymous listing, revocation, and source deletion. Both roll back all fixtures; they upload no physical files, send no mail, and publish no lasting student content.

After migration 007, run `verify_viewer_accounts.sql` to check safe signup, forged metadata rejection, viewer isolation, staff-only chat, role management, preserved teacher/admin access, and unchanged administrator bootstrap. All test accounts and records roll back.

After migration 008, run `verify_admin_delegation.sql` as the database owner. It checks original-owner promotion and revocation, ordinary administrator limits, protected/self accounts, verified targets and callers, forged metadata rejection, ID-based ownership, audit accuracy and immutability, and anonymous denial. Its temporary bootstrap owner and all role changes use isolated fixtures inside one transaction and roll back; no real account is promoted. Keep the final rollback in place.

The Java test suite uses a local HTTP server and does not access the live project. Live email delivery and initial account activation are verified by the account owner using their own inbox and password.

References: [Supabase pricing](https://supabase.com/pricing), [row-level security](https://supabase.com/docs/guides/database/postgres/row-level-security), [custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp), [Google App Passwords](https://support.google.com/accounts/answer/185833).
