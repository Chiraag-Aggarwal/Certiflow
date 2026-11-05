# Administrator delegation, menu and Desktop launcher — 5 November 2026

- Added original-administrator controls in **Menu → Accounts**: choose **Admin**, press **Update account access**, and confirm to promote another email-verified account. The original administrator can also remove another admin's access. Ordinary administrators keep Student, Recruiter / Viewer and Teacher controls; the founding account and self-role changes remain protected.
- Installed migration 008 with server-derived delegation capability, ordered actor/target row locks, confirmed-account checks, and private immutable role-change history. Authority is anchored to the original bootstrap user ID; no existing account or reserved Gmail was changed by the migration. Live rollback-only verification passed for grants, revocation, permission boundaries, audit records and preserved ownership.
- Moved **Profile** to the menu footer directly above **Log out** for every role.
- Preserved the original in-app `logo.png` and its aspect ratio. Separate `app-icon.png` and `app-icon.ico` assets provide window/taskbar and Windows shortcut icons.
- Added `install-desktop-shortcut.ps1` to create or refresh **Certiflow.lnk** on the current user's Desktop without duplicating the project. It packages the icon at multiple Windows sizes. Run the installer with PowerShell 7 (`pwsh`); Windows PowerShell 5 blocks script files on the prepared PC. No execution policy is changed. `fix_icon.ps1` calls the same installer.
- The Desktop shortcut targets bundled `javaw.exe` directly with the JavaFX/module paths, class paths and project working directory. It opens the current compiled build without a console or helper script. Existing `Run-Certiflow.bat` / `run.bat` launchers rebuild source when needed; Supabase operations still require an internet connection.
- Validation: the complete local automated suite passed all 82 tests. Live migration 008 and its rollback checks passed; the original administrator remains ADMIN with `can_manage_admins=true`, and no test fixtures remain. Double-clicking the Desktop shortcut successfully opened the branded sign-in screen. The existing menu test also passed with open-drawer previews at 390 and 1366 pixels. Live database and Desktop launch proof are saved in `previews/admin-branding`; drawer previews are in `previews/chat-sharing`.

# Unified sign-in, viewer accounts, chat and sharing — 5 November 2026

- Replaced role-selection login pages with one email/password form. The server profile selects Student, Recruiter / Viewer, Teacher, or Admin workspace; a client-supplied role hint cannot grant access.
- Signup offers Student or Recruiter / Viewer only, followed immediately by email verification. Viewer organisation details are optional. Existing administrator and teacher permissions are preserved; the designated Gmail account was verified to remain a confirmed ADMIN.
- Added a viewer Discover workspace for student-published projects, achievements, selected files and NOC summaries. Viewers can edit their own profile and contact staff, but cannot view private student data, create student submissions, approve requests, or assign roles.
- Rebuilt Messages with a searchable recent-conversation inbox, unread counts, 50-message history pages, sender-only editing with conflict detection, read/edited indicators, per-account settings, and drafts retained while switching conversations. The composer stays visible while history scrolls on phone-sized and laptop windows.
- Added explicit Community / public-link / both sharing choices with a reviewed preview and individually selected project files. Existing content is private by default. NOC reasons, staff notes, account email and institution IDs are excluded; published NOC status comes from the original record.
- Published the static read-only link viewer at https://chiraag-aggarwal.github.io/Certiflow/. Only `docs` was committed and pushed for hosting, using the repository's configured human identity. Remaining application changes are saved in the existing Desktop project.
- Installed migrations 005–007. Live rollback-only checks passed for history/editing, publication privacy/revocation/storage scope, viewer restrictions, preserved teacher access, and unchanged administrator bootstrap. No test accounts, messages or publications remain in the live project.
- Validation: all 74 automated tests passed; the final interface-only check also passed all 18 tests after colour polish. Populated chat viewport checks cover 320, 390, 768 and 1366 pixels; saved previews are in `previews/chat-sharing`. Public viewer home, invalid-link and unavailable-link states were checked in the browser.
- Current scope: Discover lists opted-in item publications. Whole-profile public pages, verified-company badges, and direct recruiter-to-student contact requests are separate future features. Viewer signup alone never verifies a company.

# Portfolio, profile and communication updates — 5 November 2026

- Added editable portfolio entries for projects, certificates, achievements, experience and documents, with multiple files, links, rename/delete controls and failed-file retries.
- Expanded NOC applications to 17 types and added real staff recipient selection. PostgreSQL restricts assigned applications, projects and downloads to their intended viewers.
- Expanded profile editing for all roles, protected email/role fields, administrator-only teacher access controls, and identity checks that reset after identity edits.
- Registration continues directly into a clearly labelled verification step, with the email filled in and the code field focused. Institution ID is optional.
- Added useful dashboard next steps, profile completion and portfolio highlights using real records.
- Added private in-app conversations, staff search, contact-switching drafts, read receipts and active-page refresh. Requests and projects provide direct conversation shortcuts.
- Added database migrations 003 and 004. The second fixes a storage/attachment policy recursion found during live checks; existing records remain intact.
- Validation: 50 local automated tests and live rollback-only collaboration access checks, including role escalation, assignment isolation, storage access, identity resets, private messages and message rate limits. No test records are retained in Supabase.

# Real accounts and PostgreSQL — 5 November 2026

- Back moved below the header, away from the menu button, across all roles.
- Removed runtime sample accounts, hardcoded passwords, profile identities and seeded requests.
- Connected Supabase Auth, PostgreSQL tables and private portfolio storage through HTTPS using a public client key.
- Added verified email registration, email-code verification and password recovery, server-assigned roles, profile editing, session refresh, and logout.
- Added database-enforced ownership, role checks, immutable decisions, duplicate-request prevention, and a private one-time administrator bootstrap.
- Network operations run off the JavaFX thread; pages include an explicit Refresh control.
- SMTP credentials are stored only in Supabase. Connection configuration is excluded from Git.
- Historical local records are not imported into the real database.

The sections below describe the earlier local version and are retained as change history.

# Basic working fixes — 3 November 2026

## Fixed

- Empty or arbitrary credentials no longer enter the app. Login validates the selected role and shows an inline error.
- Admin opens a working overview with request review, history, student roster, reports and its own account profile.
- Student NOC forms submit real local records. Required fields and duplicate pending requests are checked.
- Teacher approval and rejection update the same saved record used by student history and summary counts. Rejection requires a reason.
- Request, history, roster and portfolio searches work. Status filters combine with search correctly.
- Dashboard statistics and displayed names come from saved records and the signed-in account.
- Student quick actions, navigation, profile and logout work. Back buttons no longer accidentally log staff out.
- Scrollable content is separated from the navigation bar. Navigation remains reachable at the minimum window size.
- Portfolios import supported files, save a local copy and open saved documents.
- Report export creates a real UTF-8 CSV file. It replaces the previous simulated PDF download.
- Requests, decisions and portfolio references survive restart. Failed writes report an error and do not pretend the change was saved.
- The Desktop launcher compiles source using bundled Java 17/Maven and an offline cache. No global Java setting was changed.

## Validation

32 automated tests passed with no failures, errors or skipped tests:

- 13 data tests: login, role restrictions, validation, duplicate requests, decisions, restart persistence, corrupt data, failed-save handling and document imports.
- 6 export tests: CSV quoting, Unicode, formula-safe values, empty reports, date ranges and safe replacement.
- 13 JavaFX integration tests: actual login and submission controls, routes/logout/search, teacher-to-student decision visibility after reloading, Admin navigation, and 10 dashboard routes at 640 × 560.

The JavaFX integration tests use hidden test windows and temporary data. They do not change your normal app data. Full test results are in target/surefire-reports after running build.bat.

## Scope for this review

This remains a local demo with three documented sample logins. There is no online identity server, user registration, password recovery or notification service. Profiles are read-only. PDF generation is not implemented; report export is labelled CSV. These limitations are stated in the app/README instead of being presented as functioning buttons.

An original-source ZIP is preserved in the outer folder's backups directory.

## Responsive interface update

- Laptop sidebar, wider content area, flexible summary cards, and side-by-side forms/history panels.
- Phone-sized single-column layouts, persistent bottom navigation, wrapped content and at least 44-pixel action targets.
- Responsive welcome/login screens, preserving complete role descriptions and clear form labels.
- Readable heading/statistic sizes, scrollable navigation for short laptop windows, and a starting window that fits the screen.
- Layout checks cover 320–1440 pixel widths, portrait/landscape, and short laptop windows; draft fields survive resizing.
- Representative views are rendered from the actual JavaFX controls in `previews/responsive`.

## Academic interface update

- Shared interface across Student, Teacher and Admin, with clearer cards, status pills, readable fields, and vector navigation icons. Colour choices are described below.
- Desktop sidebar opens on left-edge hover or through the menu button; delayed dismissal avoids flickering while crossing into the panel. It overlays the page without moving or clearing a form.
- Alt+M, Escape, a close button, and outside-click dismissal make navigation usable without hover. Phone-sized windows retain their bottom navigation.
- Appearance choices persist in `data/appearance.properties`. The former reduced-motion control has been removed.
- Form validation stays inline. Saved decisions and CSV exports use dismissible success notices above phone navigation.
- Verified 30 tests, covering the existing workflows plus hover/menu/keyboard behavior, settings persistence, both themes for all three roles, inline errors, notification placement, and 320–1440 pixel layouts.
- Actual JavaFX previews are saved in `previews/academic`. A source backup is retained in the outer `backups` folder.

## Personalised interface update

- Removed repeated role/page breadcrumbs and workspace badges from every header.
- Replaced dark blue with Forest, Plum, Terracotta and Teal palettes, including matching charcoal-based dark variants. The palette panel is inside the menu on laptops and phones.
- Added a top-left return arrow with real page history; logout clears history, and the first page has a disabled return button.
- Removed the reduced-motion control and its stored setting from new preference writes.
- Added a scalable notebook-paper background, a local transparent book illustration, a small document sticker and a gentle 24-frame GIF. Artwork remains outside input areas.
- Verified 32 tests, including all four palettes for all three roles, saved preferences, menu access on phones, return navigation, image transparency, GIF frames/colours, responsive layouts, and existing data/export workflows.
- The custom artwork, its source/prompt, and `previews/personalized` are included in the project.
