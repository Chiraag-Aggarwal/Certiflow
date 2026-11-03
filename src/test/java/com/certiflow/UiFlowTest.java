package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real JavaFX controls and routes, without showing windows or injecting desktop input. */
class UiFlowTest {
    @TempDir Path temporary;
    private Stage stage;
    private AppData data;
    private double viewportWidth = 640;
    private double viewportHeight = 560;

    @BeforeAll
    static void startToolkit() throws Exception {
        System.setProperty("prism.order", "sw");
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(15, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    @BeforeEach
    void setUp() throws Exception {
        data = new AppData(temporary.resolve("data"));
        onFx(() -> {
            UiSettings.load(data.dataDirectory());
            stage = new Stage();
            Scene scene = new Scene(new VBox(), 640, 560);
            scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());
            var authStyles = App.class.getResource("/auth.css");
            if (authStyles != null) scene.getStylesheets().add(authStyles.toExternalForm());
            stage.setScene(scene);
            setAppField("primaryStage", stage);
            setAppField("data", data);
            setAppField("currentUser", null);
            App.showOptionView();
            assertFalse(stage.isShowing(), "Integration tests must never display a desktop window");
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        onFx(() -> {
            if (stage != null) {
                assertFalse(stage.isShowing(), "A test unexpectedly displayed its window");
                stage.close();
                stage.setScene(null);
            }
            setAppField("currentUser", null);
            setAppField("primaryStage", null);
            setAppField("data", null);
            return null;
        });
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @Test
    void loginRejectsEmptyInvalidAndWrongRoleCredentialsBeforeEnteringStudentHome() throws Exception {
        onFx(() -> {
            button("role-student").fire();
            button("login-submit").fire();
            assertTrue(control("login-error", Label.class).isVisible());
            assertEquals("Enter both a username and password.", control("login-error", Label.class).getText());
            assertThrows(IllegalStateException.class, App::user);

            control("login-username", TextField.class).setText("student");
            control("login-password", PasswordField.class).setText("wrong");
            button("login-submit").fire();
            assertTrue(control("login-error", Label.class).getText().startsWith("Incorrect username"));
            assertThrows(IllegalStateException.class, App::user);

            control("login-username", TextField.class).setText("teacher");
            control("login-password", PasswordField.class).setText("teacher123");
            button("login-submit").fire();
            assertThrows(IllegalStateException.class, App::user, "Teacher credentials must not enter a student session");

            control("login-username", TextField.class).setText("student");
            control("login-password", PasswordField.class).setText("student123");
            button("login-submit").fire();
            assertEquals(AppData.Role.STUDENT, App.user().role());
            assertHasLabel("Student Home");
            assertNotNull(button("nav-noc"));
            return null;
        });
    }

    @Test
    void studentSubmitsThroughRealControlsAndCanSearchNavigateAndLogOut() throws Exception {
        onFx(() -> {
            login("student");
            int previous = data.requestsFor(App.user().id()).size();
            button("nav-noc").fire();
            combo("noc-type").setValue("Internship");
            control("noc-reason", TextArea.class).setText("Summer placement certificate for the university internship.");
            button("noc-submit").fire();

            List<AppData.NocRequest> requests = data.requestsFor(App.user().id());
            assertEquals(previous + 1, requests.size());
            AppData.NocRequest added = requests.stream().filter(r -> r.type().equals("Internship")).findFirst().orElseThrow();
            assertEquals(AppData.Status.PENDING, added.status());
            assertTrue(control("noc-feedback", Label.class).isVisible());
            assertTrue(control("noc-feedback", Label.class).getText().contains(added.id()));
            assertTrue(control("noc-reason", TextArea.class).getText().isEmpty());
            assertNull(combo("noc-type").getValue());

            control("noc-search", TextField.class).setText(added.id().toLowerCase());
            assertHasLabel("1 application");
            assertHasLabel("Internship");
            combo("noc-status-filter").setValue("Rejected");
            assertHasLabel("0 applications");
            combo("noc-status-filter").setValue("Pending");
            assertHasLabel("1 application");

            button("nav-portfolios").fire();
            assertHasLabel("My Portfolios");
            assertNotNull(control("portfolio-title", TextField.class));
            button("nav-profile").fire();
            assertHasLabel("Sarah Jenkins");
            assertHasLabel("Username: student");
            button("nav-home").fire();
            assertHasLabel("Student Home");
            textButton("Log out").fire();
            assertThrows(IllegalStateException.class, App::user);
            assertNotNull(button("role-student"));
            assertFalse(stage.isShowing());
            return null;
        });
        assertTrue(new AppData(data.dataDirectory()).requestsFor("STU001").stream()
                .anyMatch(request -> request.type().equals("Internship")), "Submitted view data must survive reopening");
    }

    @Test
    void teacherSearchAndHistoryAgreeWithDecisionAndStudentStatusAfterReload() throws Exception {
        onFx(() -> {
            login("teacher");
            button("nav-requests").fire();
            assertHasLabel("Showing 3 of 3 pending requests");
            control("request-search", TextField.class).setText("sArAh");
            assertHasLabel("Showing 1 of 3 pending requests");
            control("request-search", TextField.class).setText("not-a-student");
            assertHasLabel("Showing 0 of 3 pending requests");

            AppData.NocRequest request = data.requestsFor("STU001").get(0);
            control("request-search", TextField.class).setText(request.id());
            assertHasLabel("Showing 1 of 3 pending requests");
            assertNotNull(button("approve-" + request.id()));
            assertNotNull(button("reject-" + request.id()));
            // Model decision avoids the desktop confirmation dialog while testing its resulting views.
            data.decide(App.user(), request.id(), AppData.Status.APPROVED, "All requirements verified.");
            App.showTeacherDashboard();
            assertHasLabel("Showing 2 of 2 pending requests");
            button("nav-history").fire();
            assertHasLabel("Showing 1 of 1 decisions");
            assertHasLabel("APPROVED");
            assertHasLabel("Decision note: All requirements verified.");
            combo("history-filter").setValue("Rejected");
            assertHasLabel("Showing 0 of 1 decisions");
            combo("history-filter").setValue("Approved");
            control("history-search", TextField.class).setText(request.id().toLowerCase());
            assertHasLabel("Showing 1 of 1 decisions");

            App.logout();
            data = new AppData(data.dataDirectory());
            setAppField("data", data);
            login("student");
            assertHasLabel("APPROVED");
            assertHasLabel("Teacher's note: All requirements verified.");
            button("nav-noc").fire();
            combo("noc-status-filter").setValue("Approved");
            assertHasLabel("1 application");
            assertHasLabel("APPROVED");
            return null;
        });
    }

    @Test
    void adminLoginHasWorkingStaffLandingAndNavigation() throws Exception {
        onFx(() -> {
            login("admin");
            assertEquals(AppData.Role.ADMIN, App.user().role());
            assertHasLabel("Admin overview");
            button("nav-requests").fire();
            assertHasLabel("Showing 3 of 3 pending requests");
            button("nav-history").fire();
            assertHasLabel("Showing 0 of 0 decisions");
            button("nav-profile").fire();
            assertHasLabel("Local Administrator");
            assertHasLabel("Username: admin");
            textButton("Log out").fire();
            assertNotNull(button("role-admin"));
            return null;
        });
    }

    @Test
    void navigationStaysWithinMinimumSceneAndScrollableBodyOnEveryDashboardRoute() throws Exception {
        onFx(() -> {
            login("student");
            for (Runnable route : List.<Runnable>of(App::showStudentDashboard, App::showStudentNoc,
                    App::showStudentPortfolio, App::showStudentProfile)) {
                route.run();
                assertNavigationFits("home", "portfolios", "noc", "profile");
            }
            App.logout();
            login("teacher");
            for (Runnable route : List.<Runnable>of(App::showTeacherHome, App::showTeacherDashboard,
                    App::showTeacherHistory, App::showTeacherStudentList, App::showTeacherReportExport,
                    App::showTeacherProfile)) {
                route.run();
                assertNavigationFits("home", "requests", "history", "profile");
            }
            assertFalse(stage.isShowing());
            return null;
        });
    }

    @Test
    void dashboardsAdaptAcrossPhoneTabletLandscapeAndLaptopViewports() throws Exception {
        onFx(() -> {
            double[][] sizes = {{320, 640}, {360, 800}, {390, 844}, {768, 1024},
                    {844, 390}, {960, 540}, {1366, 600}, {1366, 768}, {1440, 900}};
            String[] studentNames = {"student-home", "student-noc", "student-portfolio", "student-profile"};
            List<Runnable> studentRoutes = List.of(App::showStudentDashboard, App::showStudentNoc,
                    App::showStudentPortfolio, App::showStudentProfile);
            String[] staffNames = {"teacher-home", "teacher-requests", "teacher-history", "teacher-students",
                    "teacher-report", "teacher-profile"};
            List<Runnable> staffRoutes = List.of(App::showTeacherHome, App::showTeacherDashboard,
                    App::showTeacherHistory, App::showTeacherStudentList, App::showTeacherReportExport,
                    App::showTeacherProfile);
            for (double[] size : sizes) {
                resize(size[0], size[1]);
                assertTrue(App.login(AppData.Role.STUDENT, "student", "student123"));
                for (int i = 0; i < studentRoutes.size(); i++) {
                    studentRoutes.get(i).run();
                    assertResponsiveDashboard(studentNames[i], "home", "portfolios", "noc", "profile");
                    captureRepresentative(studentNames[i]);
                }
                App.logout();
                assertTrue(App.login(AppData.Role.TEACHER, "teacher", "teacher123"));
                for (int i = 0; i < staffRoutes.size(); i++) {
                    staffRoutes.get(i).run();
                    assertResponsiveDashboard(staffNames[i], "home", "requests", "history", "profile");
                    captureRepresentative(staffNames[i]);
                }
                App.logout();
            }
            return null;
        });
    }

    @Test
    void roleSelectionAndLoginFormsFitPhoneAndLaptopWithoutHorizontalClipping() throws Exception {
        onFx(() -> {
            for (double[] size : new double[][] {{320, 568}, {320, 640}, {390, 844}, {844, 390}, {1366, 768}, {1440, 900}}) {
                resize(size[0], size[1]);
                App.showOptionView();
                assertHorizontalFit("role picker");
                for (String role : List.of("student", "teacher", "admin")) assertTouchTarget(button("role-" + role));
                for (Node node : descendants(root())) {
                    if (node instanceof Label label && label.getStyleClass().contains("auth-role-description")) {
                        assertTrue(label.getHeight() >= label.prefHeight(label.getWidth()) - 1,
                                "Role description should wrap without clipping: " + label.getText());
                    }
                }
                captureRepresentative("roles");
                for (String role : List.of("Student", "Teacher", "Admin")) {
                    App.showLoginView(role, "");
                    assertHorizontalFit(role + " login");
                    assertTouchTarget(button("login-submit"));
                    assertTouchTarget(control("login-username", TextField.class));
                    assertTouchTarget(control("login-password", PasswordField.class));
                    if (role.equals("Student")) captureRepresentative("login");
                }
            }
            return null;
        });
    }

    @Test
    void resizingTheSameScreenKeepsTheDraftAndNavigationUsable() throws Exception {
        onFx(() -> {
            resize(390, 844);
            assertTrue(App.login(AppData.Role.STUDENT, "student", "student123"));
            App.showStudentNoc();
            Parent originalRoot = root();
            TextArea reason = control("noc-reason", TextArea.class);
            ComboBox<String> type = combo("noc-type");
            type.setValue("Internship");
            reason.setText("Please issue an NOC for my summer internship. I have completed the required coursework.");
            for (double[] size : new double[][] {{1366, 768}, {360, 800}, {1440, 900}, {390, 844}}) {
                resize(size[0], size[1]);
                assertSame(originalRoot, root(), "Resizing must not replace the page or discard form state");
                assertSame(reason, control("noc-reason", TextArea.class));
                assertSame(type, combo("noc-type"));
                assertEquals("Internship", type.getValue());
                assertTrue(reason.getText().startsWith("Please issue an NOC"));
                assertResponsiveDashboard("NOC draft after resizing", "home", "portfolios", "noc", "profile");
                captureRepresentative("noc-draft");
            }
            return null;
        });
    }


    @Test
    void drawerRevealsOnHoverAndHidesAfterLeavingWithoutChangingDraft() throws Exception {
        onFx(() -> {
            resize(1366, 768);
            assertTrue(App.login(AppData.Role.STUDENT, "student", "student123"));
            App.showStudentNoc();
            control("noc-reason", TextArea.class).setText("Keep this internship draft.");
            HoverDrawer drawer = control("navigation-shell", HoverDrawer.class);
            assertFalse(drawer.isOpen());
            assertFalse(control("desktop-sidebar", VBox.class).isVisible());
            fireMouse(control("sidebar-edge", Region.class), javafx.scene.input.MouseEvent.MOUSE_ENTERED);
            assertTrue(drawer.isOpen());
            fireMouse(control("sidebar-edge", Region.class), javafx.scene.input.MouseEvent.MOUSE_EXITED);
            fireMouse(control("desktop-sidebar", VBox.class), javafx.scene.input.MouseEvent.MOUSE_ENTERED);
            return null;
        });
        Thread.sleep(450);
        onFx(() -> {
            assertTrue(control("navigation-shell", HoverDrawer.class).isOpen(), "Crossing from the edge to the panel must not flicker closed");
            captureRepresentative("drawer-open");
            assertTrue(control("navigation-shell", HoverDrawer.class).isOpen());
            fireMouse(control("desktop-sidebar", VBox.class), javafx.scene.input.MouseEvent.MOUSE_EXITED);
            return null;
        });
        Thread.sleep(450);
        onFx(() -> {
            HoverDrawer drawer = control("navigation-shell", HoverDrawer.class);
            assertFalse(drawer.isOpen(), "The drawer should hide after the pointer leaves");
            assertFalse(control("desktop-sidebar", VBox.class).isVisible());
            assertEquals("Keep this internship draft.", control("noc-reason", TextArea.class).getText());
            button("navigation-toggle").fire();
            assertTrue(drawer.isOpen());
            button("navigation-close").fire();
            assertFalse(drawer.isOpen());
            button("navigation-toggle").fire();
            drawer.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
            assertFalse(drawer.isOpen());
            drawer.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.M, false, false, true, false));
            assertTrue(drawer.isOpen());
            fireMouse(control("noc-reason", TextArea.class), javafx.scene.input.MouseEvent.MOUSE_PRESSED);
            assertFalse(drawer.isOpen(), "Clicking outside closes the overlay");
            drawer.reveal(false);
            resize(390, 844);
            assertFalse(drawer.isOpen());
            assertTrue(effectivelyVisible(button("navigation-toggle")));
            button("navigation-toggle").fire();
            assertTrue(drawer.isOpen(), "The colour menu must also open on phones");
            button("navigation-close").fire();
            assertTrue(effectivelyVisible(button("nav-noc")));
            assertEquals("Keep this internship draft.", control("noc-reason", TextArea.class).getText());
            return null;
        });
    }


    @Test
    void palettesPersistAndAllThreeRolesStayUsableInLightAndDarkAppearance() throws Exception {
        for (UiSettings.Palette palette : UiSettings.Palette.values()) {
            for (boolean dark : List.of(false, true)) {
                onFx(() -> {
                    UiSettings.palette.set(palette);
                    UiSettings.dark.set(dark);
                    for (AppData.Role role : AppData.Role.values()) {
                        String name = role.name().toLowerCase(java.util.Locale.ROOT);
                        for (double[] size : new double[][] {{390, 844}, {1366, 768}}) {
                            resize(size[0], size[1]);
                            assertTrue(App.login(role, name, name + "123"));
                            assertEquals(dark, root().getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("dark")));
                            assertTrue(root().getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass(palette.key())));
                            assertResponsiveDashboard(name + " theme", role == AppData.Role.STUDENT
                                    ? new String[] {"home", "portfolios", "noc", "profile"}
                                    : new String[] {"home", "requests", "history", "profile"});
                            captureRepresentative(name + "-" + palette.key() + (dark ? "-dark" : "-light"));
                            if (dark && palette == UiSettings.Palette.FOREST) {
                                if (role == AppData.Role.STUDENT) App.showStudentNoc(); else App.showTeacherDashboard();
                                assertHorizontalFit("Dark form");
                                if (role != AppData.Role.STUDENT) {
                                    Button reject = (Button) root().lookup(".danger-button");
                                    assertEquals(javafx.scene.paint.Color.web("#f4a2b3"), reject.getTextFill());
                                }
                                captureRepresentative(name + "-dark-form");
                            }
                        }
                    }
                    return null;
                });
            }
        }
        onFx(() -> {
            resize(390, 844);
            App.logout();
            login("student");
            App.showStudentNoc();
            Parent original = root();
            TextArea reason = control("noc-reason", TextArea.class);
            reason.setText("Keep this draft when I change colours.");
            button("navigation-toggle").fire();
            var pane = control("appearance-panel", javafx.scene.control.TitledPane.class);
            pane.setExpanded(true);
            root();
            control("sidebar-scroll", ScrollPane.class).setVvalue(1);
            root();
            assertReachableInViewport(control("palette-plum", javafx.scene.control.ToggleButton.class), "Palette choice");
            control("palette-plum", javafx.scene.control.ToggleButton.class).fire();
            assertEquals(UiSettings.Palette.PLUM, UiSettings.palette.get());
            var dark = control("dark-appearance", javafx.scene.control.CheckBox.class);
            if (dark.isSelected()) dark.fire();
            assertFalse(UiSettings.dark.get());
            assertSame(original, root(), "Switching palettes must not replace or clear a form");
            assertEquals("Keep this draft when I change colours.", reason.getText());
            assertNull(root().lookup("#appearance-menu"), "There must be no appearance control in the top-right header");
            captureRepresentative("colour-menu");
            UiSettings.palette.set(UiSettings.Palette.FOREST);
            UiSettings.dark.set(true);
            UiSettings.load(data.dataDirectory());
            assertEquals(UiSettings.Palette.PLUM, UiSettings.palette.get());
            assertFalse(UiSettings.dark.get());
            return null;
        });
    }

    @Test
    void returnButtonFollowsPageHistoryAndNeverCrossesAnAccountBoundary() throws Exception {
        onFx(() -> {
            resize(390, 844);
            login("student");
            assertTrue(button("return-button").isDisabled());
            assertInsideViewport(button("return-button").localToScene(button("return-button").getLayoutBounds()), "Top-left return");
            App.showStudentNoc();
            App.showStudentPortfolio();
            assertFalse(button("return-button").isDisabled());
            button("return-button").fire();
            assertNotNull(control("noc-reason", TextArea.class));
            button("return-button").fire();
            assertHasLabel("Student Home");
            assertTrue(button("return-button").isDisabled());
            App.showStudentProfile();
            App.logout();
            login("teacher");
            assertTrue(button("return-button").isDisabled(), "Signing in must start fresh page history");
            App.showTeacherDashboard();
            App.showTeacherHistory();
            button("return-button").fire();
            assertNotNull(control("request-search", TextField.class));
            button("return-button").fire();
            assertHasLabel("Teacher home");
            App.logout();
            App.showLoginView("Admin", "");
            button("return-button").fire();
            assertNotNull(button("role-admin"));
            return null;
        });
    }

    @Test
    void decorativeAssetsAreLocalTransparentAndAnimated() throws Exception {
        try (var input = App.class.getResourceAsStream("/art/study-books.png")) {
            assertNotNull(input);
            BufferedImage illustration = ImageIO.read(input);
            assertTrue(illustration.getColorModel().hasAlpha());
            assertEquals(0, illustration.getRGB(0, 0) >>> 24);
        }
        try (var input = App.class.getResourceAsStream("/art/study-sparkle.gif");
                var stream = ImageIO.createImageInputStream(input)) {
            var reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(stream);
                assertEquals(24, reader.getNumImages(true), "The decorative GIF should contain its complete gentle loop");
                assertEquals(96, reader.getWidth(0));
                assertEquals(new java.awt.Color(216,168,77).getRGB(), reader.read(0).getRGB(45,48), "The animation must use the warm palette");
            } finally { reader.dispose(); }
        }
        onFx(() -> {
            login("student");
            assertTrue(control("academic-background", AcademicBackdrop.class).isMouseTransparent());
            assertNotNull(root().lookup(".study-illustration"));
            assertNotNull(root().lookup("#study-sparkle"));
            App.showStudentProfile();
            assertNotNull(root().lookup(".academic-sticker"));
            return null;
        });
    }

    @Test
    void invalidFormsShowInlineFeedbackAndSuccessNoticeCanBeDismissed() throws Exception {
        onFx(() -> {
            resize(390, 844);
            login("student");
            App.showStudentNoc();
            int previous = data.requests().size();
            button("noc-submit").fire();
            Label message = control("noc-feedback", Label.class);
            assertTrue(message.isVisible());
            assertTrue(message.getStyleClass().contains("feedback-error"));
            assertEquals(previous, data.requests().size());
            combo("noc-type").setValue("Internship");
            control("noc-reason", TextArea.class).setText("My summer internship needs an NOC.");
            button("noc-submit").fire();
            assertTrue(message.getStyleClass().contains("feedback-success"));
            assertFalse(message.getStyleClass().contains("feedback-error"));
            App.showStudentPortfolio();
            button("portfolio-save").fire();
            assertTrue(control("portfolio-feedback", Label.class).isVisible());
            assertTrue(control("portfolio-feedback", Label.class).getText().contains("Choose a document"));
            Ui.toast("Your report was saved successfully.");
            assertTrue(control("success-notice", ToastNotice.class).isVisible());
            Node notice = control("success-notice", ToastNotice.class);
            Node bottomNavigation = root().lookup("#mobile-navigation");
            assertTrue(notice.localToScene(notice.getLayoutBounds()).getMaxY() < bottomNavigation.localToScene(bottomNavigation.getLayoutBounds()).getMinY(), "A success notice must leave the phone navigation accessible");
            assertHorizontalFit("Success notice");
            captureRepresentative("inline-feedback");
            descendants(control("success-notice", ToastNotice.class)).stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow().fire();
            assertFalse(control("success-notice", ToastNotice.class).isVisible());
            return null;
        });
    }

    private static void fireMouse(Node target, javafx.event.EventType<javafx.scene.input.MouseEvent> type) {
        target.fireEvent(new javafx.scene.input.MouseEvent(type, 0, 0, 0, 0,
                javafx.scene.input.MouseButton.NONE, 0, false, false, false, false,
                false, false, false, true, false, true, null));
    }

    private void assertResponsiveDashboard(String route, String... ids) {
        Parent page = root();
        ScrollPane scroll = control("page-scroll", ScrollPane.class);
        Bounds scrollBounds = scroll.localToScene(scroll.getLayoutBounds());
        assertTrue(scroll.getHeight() >= 80, route + " needs usable scrollable height");
        assertTrue(scroll.getWidth() >= 240, route + " needs usable content width");
        boolean wide = viewportWidth >= 960;
        Node sidebar = page.lookup("#desktop-sidebar");
        Node mobileNavigation = page.lookup("#mobile-navigation");
        assertFalse(effectivelyVisible(sidebar), route + " should keep the drawer hidden until requested");
        HoverDrawer drawer = control("navigation-shell", HoverDrawer.class);
        double contentWidth = scroll.getWidth();
        if (wide) {
            assertReachableInViewport(button("navigation-toggle"), route + " menu button");
            drawer.reveal(false);
            root();
            assertTrue(effectivelyVisible(sidebar));
            assertEquals(contentWidth, scroll.getWidth(), 0.1, "Opening the drawer must not resize the content");
        }
        assertEquals(!wide, effectivelyVisible(mobileNavigation), route + " should switch the mobile navigation");
        if (!wide) { drawer.reveal(false); root(); }
        Button logout = button("logout-button");
        assertTrue(effectivelyVisible(logout));
        assertReachableInViewport(logout, route + " logout");
        if (!wide) { drawer.close(false); root(); }
        assertNull(page.lookup(".app-eyebrow"), "Repeated role/page labels must be removed");
        assertNull(page.lookup(".workspace-badge"), "The repeated workspace badge must be removed");
        assertTrue(descendants(page).stream().filter(javafx.scene.control.Labeled.class::isInstance)
                .map(javafx.scene.control.Labeled.class::cast).noneMatch(label -> "Reduce motion".equals(label.getText())));
        if (viewportWidth < 600) {
            Label heading = (Label) page.lookup(".page-title");
            assertTrue(heading.getFont().getSize() >= 22, route + " phone page heading should retain visual hierarchy");
            for (Node node : descendants(page)) {
                if (node instanceof Label number && number.getStyleClass().contains("stat-number")) {
                    assertTrue(number.getFont().getSize() >= 26, "Phone statistic numbers should remain legible");
                }
            }
        }
        for (String id : ids) {
            Button navigation = button("nav-" + id);
            Bounds bounds = navigation.localToScene(navigation.getLayoutBounds());
            assertTrue(effectivelyVisible(navigation) && !navigation.isDisabled(), route + ": " + id + " must be usable");
            assertInsideViewport(bounds, route + " navigation " + id);
            assertTouchTarget(navigation);
            if (wide) assertTrue(bounds.getMaxX() <= 260,
                    route + " navigation must remain inside its overlay: " + id);
            else assertTrue(bounds.getMinY() >= scrollBounds.getMaxY() - 1,
                    route + " bottom navigation overlaps the content: " + id);
        }
        if (viewportWidth >= 1366) {
            assertTrue(scroll.getWidth() > 920, route + " should use laptop width beyond the previous 920px container");
        }
        if (wide) drawer.close(false);
        assertPanelsReflow(page, "noc-form-panel", "noc-history-panel");
        assertPanelsReflow(page, "portfolio-form-panel", "portfolio-list-panel");
        assertPanelsReflow(page, "profile-identity-panel", "profile-account-panel");
        assertPanelsReflow(page, "report-settings-panel", "report-preview-panel");
        assertHorizontalFit(route);
    }

    private void assertPanelsReflow(Parent page, String firstId, String secondId) {
        Node first = page.lookup("#" + firstId);
        Node second = page.lookup("#" + secondId);
        if (first == null || second == null) return;
        Bounds firstBounds = first.localToScene(first.getLayoutBounds());
        Bounds secondBounds = second.localToScene(second.getLayoutBounds());
        if (viewportWidth <= 390) {
            assertTrue(secondBounds.getMinY() >= firstBounds.getMaxY() - 1,
                    firstId + " and " + secondId + " should stack on a phone");
        } else if (viewportWidth >= 1366) {
            assertTrue(secondBounds.getMinX() >= firstBounds.getMaxX() - 1,
                    firstId + " and " + secondId + " should use separate laptop columns");
        }
    }

    private void assertHorizontalFit(String label) {
        Parent page = root();
        for (Node node : descendants(page)) {
            boolean inspect = node instanceof Button || node instanceof TextField || node instanceof TextArea
                    || node instanceof ComboBox<?> || node.getStyleClass().contains("app-card")
                    || node.getStyleClass().contains("app-heading") || node.getStyleClass().contains("auth-card");
            if (!inspect || !effectivelyVisible(node)) continue;
            Bounds bounds = node.localToScene(node.getLayoutBounds());
            assertTrue(bounds.getMinX() >= -1 && bounds.getMaxX() <= viewportWidth + 1,
                    () -> label + " at " + viewportWidth + "×" + viewportHeight + " clips "
                            + node.getClass().getSimpleName() + "#" + node.getId() + " horizontally: " + bounds);
        }
        for (Node node : descendants(page)) {
            if (node instanceof ScrollPane scroll && effectivelyVisible(scroll)) {
                Bounds content = scroll.getContent().getLayoutBounds();
                assertTrue(content.getWidth() <= scroll.getViewportBounds().getWidth() + 1,
                        label + " requires horizontal scrolling despite its fit-to-width layout");
            }
        }
    }

    private void assertTouchTarget(Node node) {
        if (viewportWidth >= 960) return;
        Bounds bounds = node.getLayoutBounds();
        assertTrue(bounds.getWidth() >= 44 && bounds.getHeight() >= 44,
                () -> "Phone touch target is smaller than 44px: " + node.getClass().getSimpleName()
                        + "#" + node.getId() + " (" + bounds.getWidth() + "×" + bounds.getHeight() + ")");
    }

    private void assertInsideViewport(Bounds bounds, String description) {
        assertTrue(bounds.getMinX() >= -1 && bounds.getMinY() >= -1
                && bounds.getMaxX() <= viewportWidth + 1 && bounds.getMaxY() <= viewportHeight + 1,
                () -> description + " is clipped at " + viewportWidth + "×" + viewportHeight + ": " + bounds);
    }

    private void assertReachableInViewport(Node node, String description) {
        Bounds bounds = node.localToScene(node.getLayoutBounds());
        if (bounds.getMinY() >= -1 && bounds.getMaxY() <= viewportHeight + 1) {
            assertInsideViewport(bounds, description);
            return;
        }
        ScrollPane ancestor = null;
        for (Parent parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll) { ancestor = scroll; break; }
        }
        assertNotNull(ancestor, description + " must remain visible or reachable in a scrolling sidebar");
        double previous = ancestor.getVvalue();
        ancestor.setVvalue(1);
        root();
        assertInsideViewport(node.localToScene(node.getLayoutBounds()), description + " after scrolling");
        ancestor.setVvalue(previous);
        root();
    }

    private static boolean effectivelyVisible(Node node) {
        if (node == null || !node.isManaged()) return false;
        for (Node ancestor = node; ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible()) return false;
        }
        return true;
    }

    private void resize(double width, double height) {
        viewportWidth = width;
        viewportHeight = height;
        root();
    }

    private void captureRepresentative(String name) throws Exception {
        String directory = System.getProperty("certiflow.snapshots");
        if (directory == null || directory.isBlank()) return;
        String device = viewportWidth == 390 && viewportHeight == 844 ? "phone"
                : viewportWidth == 1366 && viewportHeight == 768 ? "laptop" : null;
        if (device == null) return;
        Path destination = Path.of(directory).resolve(name + "-" + device + ".png");
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Parent page = root();
        HoverDrawer drawer = (HoverDrawer) page.lookup("#navigation-shell");
        boolean drawerWasOpen = drawer != null && drawer.isOpen();
        // A hidden Stage does not resize its Scene when its root is resized. Give snapshot's
        // CSS/layout pass a matching Scene while keeping the exact same live control tree.
        Scene previousScene = stage.getScene();
        if (previousScene.getWidth() != viewportWidth || previousScene.getHeight() != viewportHeight) {
            List<String> stylesheets = List.copyOf(previousScene.getStylesheets());
            previousScene.setRoot(new VBox());
            Scene resizedScene = new Scene(page, viewportWidth, viewportHeight);
            resizedScene.getStylesheets().addAll(stylesheets);
            stage.setScene(resizedScene);
            root();
        }
        if (drawerWasOpen) { drawer.reveal(false); root(); }
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setViewport(new Rectangle2D(0, 0, viewportWidth, viewportHeight));
        WritableImage snapshot = page.snapshot(parameters, new WritableImage((int) viewportWidth, (int) viewportHeight));
        BufferedImage image = new BufferedImage((int) viewportWidth, (int) viewportHeight, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[(int) viewportWidth * (int) viewportHeight];
        snapshot.getPixelReader().getPixels(0, 0, (int) viewportWidth, (int) viewportHeight,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, (int) viewportWidth);
        image.setRGB(0, 0, (int) viewportWidth, (int) viewportHeight, pixels, 0, (int) viewportWidth);
        assertTrue(ImageIO.write(image, "png", destination.toFile()));
    }

    private void login(String username) {
        button("role-" + username).fire();
        control("login-username", TextField.class).setText(username);
        control("login-password", PasswordField.class).setText(username + "123");
        button("login-submit").fire();
        assertEquals(username, App.user().username());
    }

    private void assertNavigationFits(String... ids) {
        root();
        ScrollPane scroll = control("page-scroll", ScrollPane.class);
        assertTrue(scroll.getHeight() > 100, "Scrollable content must retain usable height at 640×560");
        Bounds contentBounds = scroll.localToScene(scroll.getLayoutBounds());
        for (String id : ids) {
            Button nav = button("nav-" + id);
            Bounds bounds = nav.localToScene(nav.getLayoutBounds());
            assertTrue(nav.isVisible() && !nav.isDisabled());
            assertTrue(bounds.getWidth() > 40 && bounds.getHeight() > 20, "Navigation button is too small: " + id);
            assertTrue(bounds.getMinX() >= -0.5 && bounds.getMaxX() <= 640.5, "Navigation is horizontally clipped: " + id);
            assertTrue(bounds.getMinY() >= contentBounds.getMaxY() - 0.5, "Navigation overlaps scrolling content: " + id);
            assertTrue(bounds.getMaxY() <= 560.5, "Navigation is below the minimum window: " + id);
        }
    }

    private Parent root() {
        Parent root = stage.getScene().getRoot();
        root.resize(viewportWidth, viewportHeight);
        for (int pass = 0; pass < 4; pass++) {
            root.applyCss();
            root.layout();
        }
        return root;
    }

    private <T extends Node> T control(String id, Class<T> type) {
        Node node = root().lookup("#" + id);
        assertNotNull(node, "Missing control: " + id);
        assertTrue(type.isInstance(node), "Unexpected control type for " + id);
        return type.cast(node);
    }

    private Button button(String id) { return control(id, Button.class); }

    @SuppressWarnings("unchecked")
    private ComboBox<String> combo(String id) { return (ComboBox<String>) control(id, ComboBox.class); }

    private Button textButton(String text) {
        return descendants(root()).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing button: " + text));
    }

    private void assertHasLabel(String text) {
        List<String> labels = descendants(root()).stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(Label::isVisible).map(Label::getText).toList();
        assertTrue(labels.contains(text), () -> "Missing visible label '" + text + "' in " + labels);
    }

    private static List<Node> descendants(Node node) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) nodes.addAll(descendants(child));
        }
        return nodes;
    }

    private static void setAppField(String name, Object value) throws ReflectiveOperationException {
        Field field = App.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            if (exception.getCause() instanceof Error cause) throw cause;
            throw new RuntimeException(exception.getCause());
        }
    }
}
