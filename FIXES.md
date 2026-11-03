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
