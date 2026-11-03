package com.certiflow;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Screen;

public class App extends Application {
    private static App instance;
    private static Stage primaryStage;
    private static AppData data;
    private static AppData.User currentUser;
    private static final java.util.Deque<String> backHistory = new java.util.ArrayDeque<>();
    private static final javafx.beans.property.ReadOnlyBooleanWrapper canReturn = new javafx.beans.property.ReadOnlyBooleanWrapper(false);
    private static String currentRoute;
    private static boolean returning;
    private FileChannel lockChannel;
    private FileLock dataLock;

    @Override public void start(Stage stage) {
        instance = this;
        primaryStage = stage;
        stage.setTitle("Certiflow");
        stage.setMinWidth(360);
        stage.setMinHeight(420);
        try {
            Path directory = Path.of(System.getProperty("certiflow.data.dir", "data")).toAbsolutePath().normalize();
            Files.createDirectories(directory.getParent());
            lockChannel = FileChannel.open(directory.resolveSibling("." + directory.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            dataLock = lockChannel.tryLock();
            if (dataLock == null) throw new IOException("Certiflow is already running. Please use the existing window.");
            data = new AppData(directory);
            UiSettings.load(directory);
            try (var icon = App.class.getResourceAsStream("/logo.png")) {
                if (icon != null) stage.getIcons().add(new Image(icon));
            }
            showOptionView();
            stage.show();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Certiflow could not start");
            alert.setHeaderText("Your saved data has not been reset.");
            alert.setContentText(ex.getMessage() + "\n\nCheck folder permissions or close the other Certiflow window and try again.");
            alert.showAndWait();
            Platform.exit();
        }
    }
    @Override public void stop() throws IOException {
        if (dataLock != null && dataLock.isValid()) dataLock.release();
        if (lockChannel != null) lockChannel.close();
    }
    public static AppData data() { return data; }
    public static AppData.User user() {
        if (currentUser == null) throw new IllegalStateException("Please log in first.");
        return currentUser;
    }
    public static Stage window() { return primaryStage; }
    private static void show(Parent root) {
        UiSettings.decorate(root);
        if (primaryStage.getScene() == null) {
            var available = Screen.getPrimary().getVisualBounds();
            double width = Math.max(320, Math.min(1240, available.getWidth() - 64));
            double height = Math.max(360, Math.min(800, available.getHeight() - 110));
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("/auth.css").toExternalForm());
            primaryStage.setScene(scene);
        } else primaryStage.getScene().setRoot(root);
        if (UiSettings.animate()) {
            var fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(160), root);
            fade.setFromValue(0.75);
            fade.setToValue(1);
            fade.play();
        }
    }
    public static boolean login(AppData.Role role, String username, String password) {
        var account = data.authenticate(role, username, password);
        if (account.isEmpty()) return false;
        currentUser = account.get();
        resetNavigation();
        showHome();
        return true;
    }
    public static void logout() { currentUser = null; showOptionView(); }
    public static void showOptionView() { currentUser = null; resetNavigation(); show(new OptionView().getView()); }
    public static void showLoginView(String role, String emoji) { show(new LoginView(role, emoji).getView()); }
    public static void showHome() {
        if (currentUser == null) showOptionView();
        else if (currentUser.role() == AppData.Role.STUDENT) showStudentDashboard();
        else showTeacherHome();
    }
    private static void student(String route, Supplier<Parent> view) {
        if (currentUser == null) { showOptionView(); return; }
        if (currentUser.role() != AppData.Role.STUDENT) { Ui.error("This page is for students."); return; }
        navigate(route, view);
    }
    private static void staff(String route, Supplier<Parent> view) {
        if (currentUser == null) { showOptionView(); return; }
        if (currentUser.role() == AppData.Role.STUDENT) { Ui.error("This page is for teachers and administrators."); return; }
        navigate(route, view);
    }
    public static void showStudentDashboard() { student("student-home", () -> new StudentDashboardView().getView()); }
    public static void showStudentNoc() { student("student-noc", () -> new StudentNocView().getView()); }
    public static void showStudentPortfolio() { student("student-portfolio", () -> new StudentPortfolioView().getView()); }
    public static void showStudentProfile() { student("student-profile", () -> new TeacherProfileView().getView()); }
    public static void showTeacherHome() { staff("staff-home", () -> new TeacherHomeView().getView()); }
    public static void showTeacherDashboard() { staff("staff-requests", () -> new TeacherDashboardView().getView()); }
    public static void showTeacherHistory() { staff("staff-history", () -> new TeacherHistoryView().getView()); }
    public static void showTeacherStudentList() { staff("staff-students", () -> new TeacherStudentListView().getView()); }
    public static void showTeacherReportExport() { staff("staff-reports", () -> new TeacherReportExportView().getView()); }
    public static void showTeacherProfile() { staff("staff-profile", () -> new TeacherProfileView().getView()); }

    public static javafx.beans.property.ReadOnlyBooleanProperty canReturnProperty() { return canReturn.getReadOnlyProperty(); }

    private static void resetNavigation() {
        backHistory.clear();
        currentRoute = null;
        returning = false;
        canReturn.set(false);
    }

    private static void navigate(String route, Supplier<Parent> view) {
        if (!returning && currentRoute != null && !route.equals(currentRoute)) {
            backHistory.push(currentRoute);
            if (backHistory.size() > 30) backHistory.removeLast();
        }
        currentRoute = route;
        canReturn.set(!backHistory.isEmpty());
        show(view.get());
    }

    public static void goBack() {
        if (currentUser == null || backHistory.isEmpty()) return;
        String route = backHistory.pop();
        returning = true;
        try {
            switch (route) {
                case "student-home" -> showStudentDashboard();
                case "student-noc" -> showStudentNoc();
                case "student-portfolio" -> showStudentPortfolio();
                case "student-profile" -> showStudentProfile();
                case "staff-home" -> showTeacherHome();
                case "staff-requests" -> showTeacherDashboard();
                case "staff-history" -> showTeacherHistory();
                case "staff-students" -> showTeacherStudentList();
                case "staff-reports" -> showTeacherReportExport();
                case "staff-profile" -> showTeacherProfile();
                default -> showHome();
            }
        } finally {
            returning = false;
            canReturn.set(!backHistory.isEmpty());
        }
    }

    public static void openFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) throw new IOException("The saved document could not be found.");
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if (dot < 0 || !Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp", "txt", "csv").contains(name.substring(dot + 1)))
                throw new IOException("This file type cannot be opened from Certiflow.");
            instance.getHostServices().showDocument(path.toUri().toString());
        } catch (Exception ex) { Ui.error(ex.getMessage()); }
    }
    public static void main(String[] args) { launch(args); }
}
