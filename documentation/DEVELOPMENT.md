# Developer guide

Certiflow is a Java 17 desktop application built with JavaFX. Supabase provides PostgreSQL, authentication and private file storage. The separate browser page in `docs/` displays explicitly published items; it is not the desktop application.

## Start here

Read [App.java](../src/main/java/com/certiflow/App.java) for startup and navigation, [StudentNocView.java](../src/main/java/com/certiflow/StudentNocView.java) for a complete form-to-server example, and [AppData.java](../src/main/java/com/certiflow/AppData.java) for the data model and operations. Read [database/README.md](../database/README.md) before changing access rules.

The Java files use one `com.certiflow` package. Related files follow consistent names instead of separate packages; the table below is the navigation map.

## Project map

| Location | Purpose |
| --- | --- |
| `src/main/java/com/certiflow/` | Java application code; see the class map below. |
| `src/main/resources/` | Stylesheets, original logo, separate app icons and academic illustrations. |
| `src/test/java/com/certiflow/` | JUnit tests and an isolated local HTTP backend. |
| `database/` | Ordered SQL migrations, rollback-only permission checks and email templates. |
| `docs/` | GitHub Pages public-link viewer: HTML, CSS and JavaScript. Keep this path because Pages is configured to publish it. |
| `documentation/` | This guide, the [user guide](USER_GUIDE.md) and [change history](CHANGELOG.md). |
| `tools/art/` | Source material for regenerating supporting artwork. Runtime images live in `src/main/resources/art/`. |
| `pom.xml` | Java version, Maven dependencies, test runner and JavaFX launch configuration. |
| `build.bat`, `run.bat` | Prepared Windows build/test and rebuild/launch entry points. |
| `install-desktop-shortcut.ps1` | Builds the Windows icon and creates the Desktop shortcut. |

Local folders excluded from Git have different purposes. `.tools/` and `lib/` contain the prepared machine's build/runtime dependencies. `target/` contains compiled output and test results. `data/` holds appearance/chat preferences and explicitly downloaded files. `logs/` and `previews/` hold diagnostics and optional test images. Do not treat `data/` as disposable build output. The Desktop shortcut requires `target/classes`, `.tools/` and `lib/`; after removing compiled output, rebuild before using it.

## Class map

| Area | Main files and responsibilities |
| --- | --- |
| Startup and navigation | `App`: window, single-instance lock, session routing and back history. |
| Sign-in and profiles | `LoginView`, `AccountView`, `AuthLayout`: sign-in, signup, verification and recovery. `TeacherProfileView`: shared profile editor for **all** roles despite its historical name. |
| Data and transport | `AppData`: immutable records, validation, visible-record snapshots and operations. `SupabaseApi`: HTTP, in-memory sessions, token refresh and server errors. `CloudConfig`: public client configuration. `Background`: serialized work off the JavaFX thread. |
| Student work | `StudentDashboardView`, `StudentNocView`, `StudentPortfolioView`: home, applications and portfolio editing. |
| Staff work | `TeacherHomeView`, `TeacherDashboardView`, `TeacherHistoryView`, `StaffPortfolioView`: home, review queue, decisions and assigned portfolios. `TeacherStudentListView` and `TeacherReportExportView`: roster and reports. |
| Account administration | `AccountManagementView`: server-authorized role and identity controls. |
| Messages and remarks | `MessagesView`, `ChatSettings`: participant-only chat and local preferences. `PrivateRemarks`: private feedback on a single NOC or portfolio entry. |
| Sharing and discovery | `SharingControls`: owner-reviewed publication editor. `PublicSharing`: publication RPCs and selected-file downloads. `CommunityView`: shared item feed and the viewer home. |
| Shared interface | `Ui`: page shell, controls and responsive layouts. `PageComponents`: interior-page introductions, section headings and tags. `HoverDrawer`: navigation drawer. `UiSettings`: palettes/preferences. `WorkspaceCards`, `Icons`, `AcademicArt`, `AcademicBackdrop`, `ToastNotice`: reusable presentation. |
| Other helpers | `RecipientPicker`: selects a real staff UUID, not a typed name. `ReportExporter`: CSV output from available records. |

## How an action reaches the database

```text
JavaFX event handler
    -> capture current form values and account
    -> Background.run(...)
    -> AppData operation (or PublicSharing for publications)
    -> SupabaseApi -> Auth / REST RPC / Storage
    -> PostgreSQL grants, row-level security and checked functions
    -> success or failure callback on the JavaFX thread
```

View constructors and event callbacks must not perform blocking network requests. Capture form values on the JavaFX thread, then pass the work to `Background.run`. Its single worker serializes requests, disables the busy region, and returns callbacks through `Platform.runLater`. A response for an abandoned scene root is ignored. Components with account/contact changes also check identity or request versions before applying late results; retain those checks when changing chat or remarks.

`AppData.refresh()` collects the next visible-record snapshot before replacing the current lists. These lists are a client cache of rows permitted by the server, not a local database. Use the server's returned record after a mutation so IDs, timestamps, roles and decisions remain authoritative. Report failures visibly and preserve an unsent draft where retry is useful.

## Role routing and privacy

Sign-in authenticates the email and reads the role from the stored profile. `App.showHome()` routes `STUDENT` to the student dashboard, `VIEWER` to Discover, and `TEACHER`/`ADMIN` to the staff workspace. Navigation guards improve the interface; PostgreSQL enforces access independently. A client-supplied role or a hidden button is never an authorization boundary.

| Record | Server-enforced audience |
| --- | --- |
| Assigned NOC | Its student, assigned staff member and administrators. Unassigned NOCs enter the shared staff queue. |
| Portfolio entry and files | Its student, explicitly assigned staff member and administrators. An unassigned entry is not a shared staff queue item. |
| Chat | Its two participants, including when either participant is an administrator. |
| Private remark | Verified student recipient, verified staff sender and verified administrators. Other teachers and viewers cannot read it. |
| Publication | Only a curated preview and individually selected files, according to Community / Link / Both visibility. |

The founding administrator's ability to appoint administrators comes from `account_capabilities()` and the server's bootstrap record. Other admins can manage ordinary roles but cannot grant or remove admin access. Preserve these distinctions when changing account controls.

Public sharing uses separate publication records and checked RPCs. Do not build a public response by serializing `User`, `NocRequest`, `Project`, chat or remark objects. Email addresses, institution IDs, private descriptions, NOC reasons, decision notes and private remarks are excluded from publication payloads. The browser viewer and desktop publication code must use the same allowed fields. Publishing a NOC never lets a student choose its approval status.

Storage remains a private bucket. Metadata and file-object deletion are separate operations: delete authorized metadata first, then remove the now-unreferenced object through the Storage API. Do not directly delete Supabase storage metadata in SQL. Keep upload size, file type, safe-path and owner/recipient checks in place.

## Make common changes

### Add a page

1. Create a view with `getView()` returning its JavaFX root. Use `Ui.page` for signed-in pages and shared layout helpers for narrow/wide sizes.
2. Add a route method in `App`, choose the appropriate role guard, and add the route to `goBack()`.
3. Add navigation in `Ui` only for the intended roles. Keep Profile in the menu footer.
4. Put I/O in a data operation and call it through `Background.run`; keep feedback beside the action.
5. Add a meaningful UI flow test, including the role boundary and any new narrow-layout behavior.

### Add an NOC application type

Update `AppData.REQUEST_TYPES` and add a new SQL migration updating `requests_type_check`. The current database list was expanded in migration 003; editing that old file would not update an already-installed project. The form reads the Java list automatically. Keep the values identical and retain the partial unique index that allows only one pending request of each type per student. Extend the relevant contract and database checks.

### Change database behavior

Add the next numbered migration after the latest existing one. Keep it transactional, preserve existing data, and explicitly define grants, RLS policies and RPC access. Derive the actor from `auth.uid()` and sensitive ownership/status fields from trusted rows. Privileged functions use a fixed empty search path and qualified relation names.

Write a corresponding `verify_*.sql` script with isolated fixtures and a final rollback. Test both the allowed action and nearby denial cases, including forged IDs, unrelated accounts and anonymous access. Run database checks as the database owner against the intended project. Java tests do not prove live RLS behavior. Update [database/README.md](../database/README.md) with migration order and verification instructions; apply only migrations not already installed.

### Change styling or artwork

Use `style.css` for base palettes and shared controls, `auth.css` for account screens and `chat.css` for messages. `style.css` imports `workspace.css`, `profile.css`, `student-work.css` and `review-work.css` for the interior-page components. `PageComponents` provides shared section headings and tags. Imported component styles use `.app-page` specificity so later generic card rules do not override them. Reuse palette variables and `Ui` layout helpers; test both light/dark appearance and narrow/wide layouts. Keep the original `logo.png` proportional. App/window and Windows shortcut icons are separate assets. Run the shortcut installer only when the Windows icon or shortcut needs refreshing.

The main `Stage` keeps its native decorations and resizable borders, and starts maximised. Do not force full screen during startup: it is an explicit F11/top-bar action so native minimise, restore and Windows snap remain available by default.

| Stylesheet in `src/main/resources/` | Owns |
| --- | --- |
| `style.css` | Palette variables, shared controls, navigation, base cards and home-page styles; imports the interior-page styles below. |
| `workspace.css` | Shared page introductions, section headings, tags and community publications. |
| `profile.css` | Identity card, completion meter/checklist and profile form sections. |
| `student-work.css` | Student NOC and portfolio editors, status summaries, attachments and shared portfolio cards. |
| `review-work.css` | Staff request/history cards, roster, report previews and account access. |
| `auth.css` | Sign-in, registration, verification and recovery screens. |
| `chat.css` | Conversation list, transcript, composer and chat settings. |

Keep feature-specific CSS in its owning file and shared visual building blocks in `PageComponents` or `Ui`. View classes arrange controls and handle events; data/network operations belong in `AppData` or `PublicSharing`. This keeps changes to a page easy to locate without duplicating permissions or transport code.

## Build and test

For a fresh clone, install Java 17 and Maven 3.9+, then run from the repository root:

```sh
mvn test
mvn javafx:run
```

Maven downloads dependencies on the first build. Running the app also requires `certiflow.properties` copied from the example, or `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` environment variables. Tests inject a local backend and do not need live credentials. JavaFX tests need a graphical desktop session, although their stages are not shown.

The prepared Windows copy includes an offline Maven cache and bundled Java:

```bat
build.bat
build.bat compile
run.bat
```

`build.bat` runs the suite; `compile` skips tests. `run.bat` rebuilds and launches. The Desktop shortcut launches the last compiled build without rebuilding. Offline compilation does not make live account or record operations available offline.

| Test files | What they verify |
| --- | --- |
| `AppDataTest`, `CollaborationDataTest` | Account/record contracts, validation and collaboration operations. |
| `ChatDataTest`, `PrivateRemarksTest`, `PublicSharingTest` | Messaging, private feedback and publication contracts. |
| `ReportExporterTest` | CSV content and escaping. |
| `UiFlowTest` | Navigation, form flows, account boundaries, draft handling and responsive rendering. |
| `TestBackend` | Local HTTP fixtures used by the Java tests; never production data. |
| `database/verify_*.sql` | Actual database permissions and RPC behavior, with fixture rollback. |

Surefire writes results to `target/surefire-reports`; the Windows helper writes `logs/build.log`. For optional UI snapshots with normal Maven, run `mvn -Dtest=UiFlowTest -Dcertiflow.snapshots=previews/current test`. Snapshots use test fixtures and belong outside the committed source.

Only the project URL and publishable/legacy anon key belong in the client. Keep passwords, session tokens, SMTP credentials and secret/service-role keys out of source, logs and commits. The application retains sessions in memory and rejects privileged client keys. There is no production demo-data seed.
