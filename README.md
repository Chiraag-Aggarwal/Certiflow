# Certiflow

A Java 17 / JavaFX desktop application for trying student NOC submissions and staff review on this computer.

## Run from a GitHub clone

Install a Java 17 JDK and Maven 3.9 or newer, then run these commands in the cloned repository:

```sh
mvn test
mvn javafx:run
```

Maven downloads the project dependencies on the first run. The JavaFX interface tests require a graphical desktop session. The application creates local demo data on first launch.

The `.tools` and `lib` folders are part of the prepared Desktop copy and are not committed. The offline `run.bat` and `build.bat` launchers below apply to that copy; use the Maven commands above for a fresh GitHub clone.

## Run the prepared Desktop copy

Double-click **Run-Certiflow.bat** in the outer Desktop folder, or **run.bat** beside this README.

The launcher compiles the current source using the bundled Java and Maven, then opens the app. No system Java installation or internet connection is needed. Keep the `.tools` and `lib` folders with the project. Close an existing Certiflow window before reopening an updated build.

## Laptop and phone-sized layouts

The interface adapts to the available window width. At laptop widths (960 pixels and above), related panels sit side by side. Navigation stays hidden to give your work more room. Hover at the left edge, click the menu button, or press Alt+M to reveal it. Move away to hide it; Escape, the close button, and a click outside also dismiss it. Keyboard focus keeps the panel open while you use its links. Below that, primary navigation stays at the bottom; the menu still opens for colour settings, staff shortcuts and logout. Forms and records stack at narrow widths. Cards fill the available space, text wraps, and resizing preserves a typed draft.

The starting window fits the usable screen area. Narrow the desktop window to preview the phone layout. This is the JavaFX desktop application; an Android or iOS package is not included.

The Student, Teacher and Admin screens share a warm notebook-style background with a small book illustration, a document sticker and a gentle animated sparkle. Open the menu, then **Colours & appearance**, to choose **Forest**, **Plum**, **Terracotta**, or **Teal**. A dark appearance is available in the same panel. Choices are saved locally and changing colours preserves the current form.

The top-left return arrow opens the previous page. It is disabled when there is no previous page, and history is cleared on logout so it cannot return to another account. Repeated role/page labels and the reduced-motion control have been removed. Success notices can be dismissed, and NOC/portfolio validation appears beside the form without losing your input.

Screenshots of the latest interface and its colour palettes are saved in `previews/personalized`. Earlier previews remain in their existing folders.

## Demo accounts

Choose the matching role on the first screen.

| Role | Username | Password |
| --- | --- | --- |
| Student | student | student123 |
| Teacher | teacher | teacher123 |
| Admin | admin | admin123 |

These are shared local demo accounts, not an online authentication system. Blank credentials, incorrect credentials, and credentials for another role are rejected. The initial data contains three sample pending requests; Mark and David are sample roster entries, not additional login accounts.

## Try the workflow

1. Log in as Student, open NOC, choose an application type and enter a reason.
2. Submit it. The application appears in the student's history and in the Teacher/Admin pending requests.
3. Log out, log in as Teacher, then open Requests. Approve the request, or reject it with a reason.
4. Check History and its search/status filter. Log back in as Student to see the same decision.
5. Close and reopen the app. Requests, decisions and imported documents remain saved.
6. On Portfolios, choose a PDF, supported image, or text file and add it. Imports are copied into the project, with a 25 MB limit.
7. Teacher/Admin can choose Export report from Home and save a real CSV file for this week or all records.

One pending request per student and type is allowed. The form explains validation errors without discarding entered text.

## Where files are kept

- `src/main`: application source, logo and styles.
- `src/test`: automated regression tests.
- `src/main/resources/art`: local illustration and animated GIF; no network access is needed.
- `art-src`: illustration prompt and the Java source for rebuilding the decorative GIF.
- `data/appearance.properties`: local palette and light/dark choices.
- `data/state.xml`: locally saved requests, decisions and portfolio index.
- `data/documents`: imported document copies.
- `reports`: default CSV export destination.
- `logs/build.log` and `logs/runtime.log`: startup/build diagnostics.
- `.tools`: bundled Java 17, Maven and offline dependency cache.
- `lib`: runtime JavaFX and Ikonli libraries.
- `../backups`: ZIP of the original source from before these fixes.

Back up the complete `data` folder while the app is closed to preserve saved work. A second app instance using the same data is blocked. Unreadable saved data is reported instead of being silently reset.

## Build and test

From a command prompt in this folder:

```bat
build.bat
build.bat compile
```

The default command runs the regression suite; the second compiles only. Results are saved under `target/surefire-reports`. Both use the bundled dependencies in offline mode. The launcher also compiles before starting, so source edits are picked up next time.

## Current scope

The app runs locally. Admin provides the request-review, history, roster and report tools. Profiles display the signed-in demo account. Online account registration/recovery, editable profiles and remote notifications are not connected. Report export is CSV; the old screen's PDF download was only a console message and did not create a file.
