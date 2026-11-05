# Certiflow

A Java 17 / JavaFX application for student clearances, staff reviews, and portfolios with optional community and public sharing. Accounts, email verification, profiles, applications, documents, and messages use Supabase PostgreSQL, Auth, and Storage. An internet connection is required for account and record operations.

## Open the prepared Desktop app

Create or refresh the **Certiflow** Desktop shortcut by running the installer from this folder with PowerShell 7, then double-click **Certiflow** on the Desktop:

```powershell
pwsh -NoProfile -File .\install-desktop-shortcut.ps1
```

The installer creates `Certiflow.lnk` on the current Windows user's Desktop and packages `app-icon.png` into a Windows `app-icon.ico`; it does not copy the project or change the original logo. `fix_icon.ps1` is a compatibility wrapper for the same installer. Windows PowerShell 5 blocks script files on the prepared PC; PowerShell 7 (`pwsh`) runs the installer without changing execution policy.

The shortcut opens the current compiled app directly through bundled `.tools/jdk-17/bin/javaw.exe`, with JavaFX/module paths, class paths, and the project working directory already set. It starts no console or helper script and does not rebuild source. After editing code, use **Desktop → Certiflow-main → Run-Certiflow.bat**, **run.bat** beside this README, or `build.bat compile` to rebuild from the prepared offline dependency cache. Keep `.tools`, `lib`, and `target/classes` with this prepared copy. Supabase account and record operations still require the internet.

Everyone uses one sign-in page with their real email and password. Certiflow reads the role from the server and opens the appropriate workspace automatically. There are no built-in accounts or sample records. Choose **Create an account** and select **Student** or **Recruiter / Viewer**. Students can manage their work and NOCs; viewers open **Discover** to browse opted-in publications. Neither signup choice grants staff access. Teachers receive access from an administrator; the existing reserved administrator account is preserved.

Registration opens **Verify your email** automatically with your email filled in; paste the code from your inbox into **Verification code** and press **Verify email**. You can also open verification from **Already have a code? Verify email** on registration or **Verify email / resend code** on sign-in. Returning to sign-in preserves the email. Use a password of 12–128 characters.

The student/staff ID is optional. Enter your institution's roll number or employee ID if you have one; otherwise leave it blank. Profiles show **Not provided** for missing IDs. The verified email identifies your sign-in account.

## Run a GitHub clone

Install Java 17 and Maven 3.9+, copy `certiflow.properties.example` to `certiflow.properties`, and enter your project's URL and publishable key. Then run:

```sh
mvn test
mvn javafx:run
```

Only the project URL and **publishable** or legacy **anon** key belong in the client. The app rejects secret/service-role keys. The connection file is ignored by Git. `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` environment variables can also supply configuration. A different configuration file can be selected with `-Dcertiflow.config=...`.

The `.tools` and `lib` folders are not committed; they support the prepared Desktop copy. A normal Maven clone obtains its own dependencies. JavaFX tests require a graphical desktop session but do not show windows.

## Database and email setup

See [database/README.md](database/README.md). The current connected project has already been initialized; do not rerun its initial migration.

- PostgreSQL stores real profiles, NOC applications, and portfolio metadata.
- Supabase Auth manages passwords, verified email, sessions, and recovery. Session tokens and passwords are never saved in local files.
- Row-level security isolates students' records. Staff can review requests and view the roster. Viewer accounts can browse shared publications and edit their own profiles, but cannot access private student records, submit NOCs, approve requests, or assign roles.
- Documents are uploaded to a private bucket. Owners can download their documents after signing in. The 25 MB limit and supported file types are checked in the app and storage configuration.
- Email verification and password recovery require a working SMTP sender. Templates in `database/email-templates` display codes that can be entered directly in the app.
- Existing local `state.xml` records are not imported. The app does not seed accounts, requests, names, addresses, or statistics.

## Navigation and records

- **Portfolios:** create projects, certificates, achievements, experience, and document entries. Edit their title, category, description, and web link; select multiple supporting files; rename or remove attachments. A failed batch keeps only failed files for retry. Existing individual uploads remain available. Entries are student-provided, not independently verified achievements.
- **NOC applications:** choose from 17 application types. Leave the recipient empty for the shared staff queue, or search registered staff and choose one teacher. Assigned applications are visible to the student, selected teacher, and administrators.
- **Profiles:** all roles can edit name, department, optional institution ID, programme/teaching area, biography, skills, and website. Email and access role are protected. Editing identity fields clears any previous identity verification.
- **Account access:** administrators use **Menu → Accounts** to choose Student, Recruiter / Viewer, or Teacher access for another email-verified account. The original administrator can also select **Admin**, press **Update account access**, and confirm the change; only that account can grant or remove other administrators' access. The original administrator is protected, and nobody can change their own role. Newly appointed admins can manage Student, Viewer and Teacher access but cannot appoint more admins. Use **Verify identity** only after checking institutional records. Signup still permits only Student or Viewer, and the server ignores forged staff metadata.
- **Portfolio sharing:** an entry without a recipient is visible to its owner and administrators. Choose registered staff to share that entry and its attachments. Teachers use **Menu → Portfolios** to review entries shared with them.
- **Messages:** open **Menu → Messages** or a dashboard shortcut. The inbox shows existing conversations, recent messages, unread counts, and search. Students and viewers can contact teachers/administrators; staff can contact registered, email-verified accounts. Only participants can read a conversation, including administrators. History loads 50 messages at a time; **Load older messages** has no total history limit. The page checks for updates every 8 seconds while open. Senders can edit their own messages; edited messages are labelled, and a conflicting edit must be refreshed. Old conversations remain readable if a role change prevents new messages.
- **Chat settings:** choose Enter-to-send, compact spacing, larger text, inbox previews, and auto-follow. Preferences are saved per account on this device. Ctrl+Enter sends; Shift+Enter inserts a line break. Unsent drafts survive contact switching within the page, not app closure, and are never written to disk. Messages remain limited to 4,000 characters and 30 sends per minute.
- **Community and public links:** on your own portfolio entry or NOC, choose **Sharing & public preview**. Select community-only, a public web link, or both; review the display name, title, summary, optional web link, and individually selected supporting files before publishing. Nothing is published automatically. **Menu → Community** shows publications shared with signed-in members. Public links open a read-only browser page without requiring Certiflow or an account.
- **Publication privacy:** sharing publishes a separate preview. It excludes account emails, institution IDs, private descriptions, NOC reasons, staff notes, recipients, and conversations. NOC type and status come from the original application. Students can publish self-described work but cannot forge approval status. Set the audience to **Private** to revoke sharing; republishing produces a new link. Copies already downloaded by viewers cannot be recalled.
- **Recruiter / Viewer workspace:** Discover offers search and filters over community publications, selected supporting files, and public links. The profile form includes an optional organisation and professional interests. This account type does not verify a company. Whole-profile public pages and direct recruiter-to-student contact requests are not implemented; viewers can currently contact staff.

The dashboards show actual recent applications, portfolio highlights, profile completion, and useful next steps. Completion measures filled fields; it does not verify a person's claims or confer staff access.

The menu stays at the top. **Back** is in the page area below the header; it uses page history and resets on logout. At wide sizes, hover at the left edge or press Alt+M to open navigation. At narrow sizes, main routes remain in the bottom bar. Escape closes the drawer. **Profile** stays at the bottom of the menu directly above **Log out**, and appearance controls remain inside the menu.

The interface displays the original `logo.png` with its proportions preserved. Window/taskbar artwork uses the separate `app-icon.png`; the Desktop shortcut uses the generated `app-icon.ico`. Updating the app icon does not replace or distort the original in-app logo.

Use **Refresh** to retrieve current records and other users' decisions. Network operations run in the background with visible feedback. Report export writes CSV files using real loaded records. Clearances are protected against duplicate pending submissions and concurrent double decisions. Teacher routing and chat privacy are checked in PostgreSQL, not just hidden in the interface.

Four palettes and a dark appearance are available. Layouts adapt to laptop and narrow window sizes. This remains a JavaFX desktop app; it is not an Android or iOS package.

## Local files and checks

- `src/main`: application code and artwork.
- `install-desktop-shortcut.ps1`: run with PowerShell 7 to build the Windows icon and create or update the current user's `Certiflow.lnk`, which launches bundled `javaw.exe` directly.
- `fix_icon.ps1`: compatibility wrapper that reruns the shortcut installer.
- `src/test`: isolated HTTP contract, JavaFX interface, and CSV tests. Fixture accounts exist only inside local test servers, never in the running app or live database.
- `database`: schema, permissions, rollback-only access checks, and mail templates.
- `data/appearance.properties`: local colour choices.
- `data/chat-<account-id>.properties`: local chat preferences only.
- `data/downloads`: documents explicitly opened from your account.
- `data/shared-downloads`: supporting files explicitly downloaded from community publications.
- `docs`: static public-link viewer, hosted at https://chiraag-aggarwal.github.io/Certiflow/ using GitHub Pages from `main` / `docs`. It contains only the Supabase publishable key, never a server secret, and does not list publications without a link.
- `reports`: exported reports.
- `logs`: build and runtime diagnostics.
- `previews/cloud`: current sign-in previews. Older preview folders record previous interface versions.
- `previews/chat-sharing`: chat, narrow-window, and sharing verification previews. Populated local test screenshots use isolated fixtures; those records do not exist in the live app.

Run `build.bat` for the automated suite or `build.bat compile` for compilation only. Both use the prepared offline dependency cache; the application itself still needs the internet for Supabase. Results are saved in `target/surefire-reports`.

Free hosting has quotas and inactivity limits. Gmail SMTP is suitable for a small initial group; larger deployments should use a dedicated transactional mail service and review hosting/storage requirements.
