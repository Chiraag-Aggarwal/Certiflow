package com.certiflow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Width-driven layouts share the same controls and state at every breakpoint. */
public final class Ui {
    public static final double DESKTOP_BREAKPOINT = 960;
    private Ui() {}

    public static VBox centeredPage(Node content, String style) {
        VBox wrapper = new VBox(content);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(20));
        wrapper.setMinWidth(0);
        wrapper.setMinHeight(Region.USE_PREF_SIZE);
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinSize(0, 0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dashboard-scroll-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox root = new VBox(scroll);
        root.getStyleClass().add(style);
        return root;
    }

    public static VBox page(String heading, String active, Node... content) {
        VBox root = new VBox();
        root.setMinSize(0, 0);
        root.getStyleClass().addAll("app-page", "role-" + App.user().role().name().toLowerCase(Locale.ROOT));

        VBox sidebar = new VBox(18);
        sidebar.setId("desktop-sidebar");
        sidebar.getStyleClass().add("app-sidebar");
        VBox desktopNavigation = new VBox(6);
        VBox shortcuts = new VBox(6);
        if (App.user().role() != AppData.Role.STUDENT) {
            Button students = nav("Students", active, App::showTeacherStudentList);
            students.setText("Student roster");
            Button reports = nav("Export", active, App::showTeacherReportExport);
            reports.setText("Reports");
            shortcuts.getChildren().addAll(students, reports);
        }
        Label role = muted(roleName(App.user().role()) + " account");
        VBox accountCard = new VBox(6, text(App.user().name()), role);
        accountCard.getStyleClass().add("sidebar-account");
        Label hint = muted("Alt+M opens · Esc closes");
        hint.getStyleClass().add("drawer-hint");
        Button closeDrawer = iconButton("close", "Close navigation", () -> {});
        closeDrawer.setId("navigation-close");
        Region brandSpace = new Region();
        HBox.setHgrow(brandSpace, Priority.ALWAYS);
        HBox sidebarBrand = row(brand(), brandSpace, closeDrawer);
        javafx.scene.control.TitledPane appearance = appearancePanel();
        VBox sidebarContent = new VBox(16, sidebarBrand, desktopNavigation, shortcuts, appearance, hint);
        sidebarContent.setMinWidth(0);
        ScrollPane sidebarScroll = new ScrollPane(sidebarContent);
        sidebarScroll.setId("sidebar-scroll");
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setMinSize(0, 0);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.getStyleClass().add("dashboard-scroll-pane");
        VBox.setVgrow(sidebarScroll, Priority.ALWAYS);
        Button logout = secondary("Log out", App::logout);
        logout.setId("logout-button");
        VBox footer = new VBox(12, accountCard, logout);
        sidebar.getChildren().addAll(sidebarScroll, footer);
        appearance.expandedProperty().addListener((observable, before, after) -> {
            if (after) javafx.application.Platform.runLater(() -> sidebarScroll.setVvalue(1));
        });

        Button menu = iconButton("menu", "Open navigation • Alt+M", () -> {});
        menu.setId("navigation-toggle");
        Button goBack = iconButton("back", "Return to previous page", App::goBack);
        goBack.setId("return-button");
        goBack.disableProperty().bind(App.canReturnProperty().not());
        Region headerSpace = new Region();
        HBox.setHgrow(headerSpace, Priority.ALWAYS);
        Label account = muted(App.user().name());
        account.getStyleClass().add("topbar-account");
        HBox topbar = row(goBack, menu, brand(), headerSpace, account);
        topbar.setId("app-topbar");
        topbar.getStyleClass().add("app-topbar");
        topbar.setMinHeight(Region.USE_PREF_SIZE);

        Label pageTitle = title(heading);
        pageTitle.getStyleClass().add("page-title");
        VBox pageHeading = new VBox(pageTitle);
        pageHeading.getStyleClass().add("page-heading");
        VBox body = new VBox(24, pageHeading);
        body.setId("page-content");
        body.setMinWidth(0);
        body.setMaxWidth(1320);
        body.getChildren().addAll(content);
        VBox contentWrapper = new VBox(body);
        contentWrapper.setMinWidth(0);
        contentWrapper.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(contentWrapper);
        scroll.setId("page-scroll");
        scroll.setFitToWidth(true);
        scroll.setMinSize(0, 0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("dashboard-scroll-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        HBox mobileNavigation = new HBox(4);
        mobileNavigation.setId("mobile-navigation");
        mobileNavigation.setAlignment(Pos.CENTER);
        mobileNavigation.setMinHeight(Region.USE_PREF_SIZE);
        mobileNavigation.getStyleClass().add("app-mobile-nav");
        List<Button> navigation = new ArrayList<>();
        if (App.user().role() == AppData.Role.STUDENT) {
            navigation.add(nav("Home", active, App::showStudentDashboard));
            navigation.add(nav("Portfolios", active, App::showStudentPortfolio));
            navigation.add(nav("NOC", active, App::showStudentNoc));
            navigation.add(nav("Profile", active, App::showStudentProfile));
        } else {
            navigation.add(nav("Home", active, App::showTeacherHome));
            navigation.add(nav("Requests", active, App::showTeacherDashboard));
            navigation.add(nav("History", active, App::showTeacherHistory));
            navigation.add(nav("Profile", active, App::showTeacherProfile));
        }
        javafx.scene.layout.StackPane paper = new javafx.scene.layout.StackPane(new AcademicBackdrop(), scroll);
        paper.setMinSize(0, 0);
        VBox.setVgrow(paper, Priority.ALWAYS);
        VBox main = new VBox(topbar, paper, mobileNavigation);
        main.setMinSize(0, 0);
        HoverDrawer shell = new HoverDrawer(main, sidebar, menu);
        shell.setId("navigation-shell");
        closeDrawer.setOnAction(event -> shell.close(true));
        VBox.setVgrow(shell, Priority.ALWAYS);
        root.getChildren().add(shell);
        ToastNotice notice = new ToastNotice();
        shell.getChildren().add(notice);
        root.getProperties().put("toast-notice", notice);
        mobileNavigation.heightProperty().addListener((observable, before, after) ->
            javafx.scene.layout.StackPane.setMargin(notice, new Insets(16, 16, mobileNavigation.isManaged() ? after.doubleValue() + 16 : 16, 16)));

        boolean[] wasDesktop = {true};
        boolean[] initialized = {false};
        Runnable adapt = () -> {
            double width = root.getWidth();
            boolean desktop = width >= DESKTOP_BREAKPOINT;
            boolean compact = width < 600;
            root.pseudoClassStateChanged(PseudoClass.getPseudoClass("compact"), compact);
            shell.setDesktop(desktop);
            mobileNavigation.setVisible(!desktop);
            mobileNavigation.setManaged(!desktop);
            javafx.scene.layout.StackPane.setMargin(notice, new Insets(16, 16, desktop ? 16 : mobileNavigation.getHeight() + 16, 16));
            account.setVisible(width >= 700);
            account.setManaged(width >= 700);
            desktopNavigation.setVisible(desktop);
            desktopNavigation.setManaged(desktop);
            body.setPadding(new Insets(compact ? 24 : 34, compact ? 16 : 36, 32, compact ? 16 : 36));
            body.setSpacing(compact ? 20 : 24);
            topbar.setSpacing(compact ? 8 : 14);
            topbar.setPadding(new Insets(compact ? 12 : 16, compact ? 12 : 28, compact ? 12 : 16, compact ? 12 : 28));
            if (!initialized[0] || wasDesktop[0] != desktop) {
                desktopNavigation.getChildren().clear();
                mobileNavigation.getChildren().clear();
                if (desktop) desktopNavigation.getChildren().addAll(navigation);
                else mobileNavigation.getChildren().addAll(navigation);
                for (Button button : navigation) {
                    button.setAlignment(desktop ? Pos.CENTER_LEFT : Pos.CENTER);
                    button.setContentDisplay(desktop ? javafx.scene.control.ContentDisplay.LEFT : javafx.scene.control.ContentDisplay.TOP);
                    button.setGraphicTextGap(desktop ? 12 : 4);
                    button.setPrefWidth(desktop ? Region.USE_COMPUTED_SIZE : 0);
                }
                initialized[0] = true;
                wasDesktop[0] = desktop;
            }
        };
        root.widthProperty().addListener((observable, previous, current) -> adapt.run());
        adapt.run();
        return root;
    }


    private static javafx.scene.control.TitledPane appearancePanel() {
        VBox options = new VBox(14);
        options.setMinWidth(0);
        options.getStyleClass().add("appearance-options");
        List<javafx.scene.control.ToggleButton> choices = new ArrayList<>();
        for (UiSettings.Palette palette : UiSettings.Palette.values()) {
            javafx.scene.control.ToggleButton choice = new javafx.scene.control.ToggleButton(palette.label);
            choice.setId("palette-" + palette.key());
            choice.setAccessibleText(palette.label + " colour palette");
            choice.getStyleClass().addAll("palette-choice", "swatch-" + palette.key());
            choice.setMinSize(0, 44);
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setSelected(palette == UiSettings.palette.get());
            choice.setOnAction(event -> {
                UiSettings.palette.set(palette);
                choice.setSelected(true);
                UiSettings.save();
            });
            choices.add(choice);
        }
        javafx.beans.value.ChangeListener<UiSettings.Palette> listener = (observable, before, after) -> {
            for (int i = 0; i < choices.size(); i++) choices.get(i).setSelected(UiSettings.Palette.values()[i] == after);
        };
        options.getProperties().put("palette-choice-listener", listener);
        UiSettings.palette.addListener(new javafx.beans.value.WeakChangeListener<>(listener));
        javafx.scene.control.CheckBox dark = new javafx.scene.control.CheckBox("Dark appearance");
        dark.setId("dark-appearance");
        dark.setMinHeight(44);
        dark.setSelected(UiSettings.dark.get());
        dark.setOnAction(event -> { UiSettings.dark.set(dark.isSelected()); UiSettings.save(); });
        options.getChildren().addAll(muted("Choose your colours"), grid(2, 86, choices.toArray(Node[]::new)), dark);
        javafx.scene.control.TitledPane pane = new javafx.scene.control.TitledPane("Colours & appearance", options);
        pane.setId("appearance-panel");
        pane.getStyleClass().add("appearance-panel");
        pane.setExpanded(false);
        pane.setAnimated(false);
        pane.setMinWidth(0);
        return pane;
    }

    public static Button iconButton(String icon, String description, Runnable action) {
        Button button = secondary("", action);
        button.setGraphic(Icons.of(icon));
        button.setAccessibleText(description);
        button.setTooltip(new javafx.scene.control.Tooltip(description));
        button.getStyleClass().add("icon-button");
        button.setMinSize(44, 44);
        button.setPrefSize(44, 44);
        button.setMaxSize(44, 44);
        return button;
    }

    public static void feedback(Label label, String message, boolean success) {
        label.getStyleClass().removeAll("feedback-success", "feedback-error");
        label.getStyleClass().add(success ? "feedback-success" : "feedback-error");
        label.setText(message == null ? "The action could not be completed." : message);
        label.setAccessibleText(label.getText());
        label.setVisible(true);
        label.setManaged(true);
    }

    public static void toast(String message) {
        if (App.window() == null || App.window().getScene() == null) return;
        Object notice = App.window().getScene().getRoot().getProperties().get("toast-notice");
        if (notice instanceof ToastNotice toast) toast.show(message);
    }

    private static Button nav(String label, String active, Runnable action) {
        Button button = secondary(label, () -> { if (!label.equals(active)) action.run(); });
        button.setId("nav-" + label.toLowerCase(Locale.ROOT));
        button.setGraphic(Icons.of(label.toLowerCase(Locale.ROOT)));
        button.setGraphicTextGap(12);
        button.getStyleClass().add("app-nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(false);
        HBox.setHgrow(button, Priority.ALWAYS);
        if (label.equals(active)) button.getStyleClass().add("app-nav-active");
        return button;
    }

    private static Node brand() {
        Label mark = new Label("");
        mark.setGraphic(Icons.of("graduate"));
        mark.getStyleClass().add("app-brand-mark");
        Label name = new Label("Certiflow");
        name.getStyleClass().add("app-brand-name");
        HBox brand = new HBox(10, mark, name);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMinWidth(Region.USE_PREF_SIZE);
        return brand;
    }

    /** Equal-width columns collapse using the actual content width, not screen resolution. */
    public static GridPane grid(int maximumColumns, double minimumColumnWidth, Node... children) {
        return new AdaptiveGrid(maximumColumns, minimumColumnWidth, children);
    }
    public static GridPane columns(Node primary, Node secondary) {
        GridPane grid = grid(2, 370, primary, secondary);
        grid.getStyleClass().add("app-columns");
        return grid;
    }
    public static GridPane actions(Node... children) {
        GridPane grid = grid(3, 185, children);
        grid.getStyleClass().add("app-actions");
        return grid;
    }
    public static VBox statistic(String label, String value, String detail, String styleClass) {
        Label number = title(value);
        number.getStyleClass().add("stat-number");
        Label caption = muted(label);
        caption.getStyleClass().add("stat-caption");
        VBox card = card(caption, number);
        card.getStyleClass().addAll("app-statistic", styleClass);
        if (detail != null && !detail.isBlank()) card.getChildren().add(muted(detail));
        return card;
    }
    public static VBox welcome(String heading, String subtitle) {
        VBox copy = section(heading, subtitle);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        ImageView books = AcademicArt.books(142);
        HBox artwork = new HBox(6, AcademicArt.sparkle(), books);
        artwork.setAlignment(Pos.CENTER);
        artwork.setMouseTransparent(true);
        HBox line = new HBox(20, copy, artwork);
        line.setFillHeight(false);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setMinWidth(0);
        VBox welcome = new VBox(line);
        welcome.getStyleClass().add("welcome-banner");
        welcome.widthProperty().addListener((observable, before, after) -> {
            boolean wide = after.doubleValue() >= 560;
            books.setFitWidth(wide ? 142 : 72);
            artwork.getChildren().get(0).setVisible(wide);
            artwork.getChildren().get(0).setManaged(wide);
        });
        return welcome;
    }
    public static VBox section(String heading, String subtitle) {
        VBox section = new VBox(7, title(heading));
        if (subtitle != null && !subtitle.isBlank()) section.getChildren().add(muted(subtitle));
        return section;
    }

    private static final class AdaptiveGrid extends GridPane {
        private final int maximum;
        private final double minimum;
        private int columns = -1;
        AdaptiveGrid(int maximum, double minimum, Node... children) {
            if (maximum < 1 || minimum <= 0) throw new IllegalArgumentException("Invalid grid dimensions.");
            this.maximum = maximum;
            this.minimum = minimum;
            setMinWidth(0);
            setMaxWidth(Double.MAX_VALUE);
            setHgap(16);
            setVgap(16);
            setAlignment(Pos.TOP_LEFT);
            getStyleClass().add("adaptive-grid");
            widthProperty().addListener((InvalidationListener) observable -> reflow());
            getChildren().addListener((ListChangeListener<Node>) change -> { columns = -1; reflow(); });
            getChildren().addAll(children);
            reflow();
        }
        private void reflow() {
            int count = Math.max(1, Math.min(maximum,
                (int) Math.floor((getWidth() + getHgap()) / (minimum + getHgap()))));
            count = Math.min(count, Math.max(1, getChildren().size()));
            if (count == columns) return;
            columns = count;
            getColumnConstraints().clear();
            for (int i = 0; i < count; i++) {
                ColumnConstraints column = new ColumnConstraints();
                column.setMinWidth(0);
                column.setPercentWidth(100.0 / count);
                column.setHgrow(Priority.ALWAYS);
                column.setFillWidth(true);
                getColumnConstraints().add(column);
            }
            for (int i = 0; i < getChildren().size(); i++) {
                Node child = getChildren().get(i);
                GridPane.setColumnIndex(child, i % count);
                GridPane.setRowIndex(child, i / count);
                GridPane.setHgrow(child, Priority.ALWAYS);
                GridPane.setFillWidth(child, true);
                GridPane.setFillHeight(child, false);
                GridPane.setHalignment(child, HPos.LEFT);
                GridPane.setValignment(child, VPos.TOP);
                if (child instanceof Region region) {
                    region.setMinWidth(0);
                    region.setMaxWidth(Double.MAX_VALUE);
                }
            }
            requestLayout();
        }
    }

    public static Node logo(double height) {
        var resource = Ui.class.getResource("/logo.png");
        if (resource == null) return title("Certiflow");
        ImageView logo = new ImageView(new Image(resource.toExternalForm()));
        logo.setPreserveRatio(true);
        logo.setFitHeight(height);
        return logo;
    }
    public static String roleName(AppData.Role role) {
        return switch (role) { case STUDENT -> "Student"; case TEACHER -> "Teacher"; case ADMIN -> "Admin"; };
    }
    public static Label title(String value) {
        Label label = text(value);
        label.getStyleClass().add("app-heading");
        return label;
    }
    public static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("app-text");
        return label;
    }
    public static Label muted(String value) {
        Label label = text(value);
        label.getStyleClass().add("app-muted");
        return label;
    }
    public static Button button(String label, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().add("app-button");
        button.setMinWidth(0);
        button.setMinHeight(44);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(true);
        button.setOnAction(event -> action.run());
        return button;
    }
    public static Button secondary(String label, Runnable action) {
        Button button = button(label, action);
        button.getStyleClass().add("app-secondary");
        return button;
    }
    public static VBox card(Node... children) {
        VBox box = new VBox(14, children);
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("app-card");
        return box;
    }
    public static HBox row(Node... children) {
        HBox box = new HBox(12, children);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        return box;
    }
    public static Label status(AppData.Status status) {
        Label label = new Label(status.name());
        label.getStyleClass().addAll("status-pill", switch (status) {
            case PENDING -> "pill-review"; case APPROVED -> "pill-accepted"; case REJECTED -> "pill-rejected";
        });
        return label;
    }
    public static String date(LocalDateTime time) {
        return time == null ? "—" : time.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH));
    }
    private static Alert alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        if (App.window() != null) alert.initOwner(App.window());
        alert.setTitle("Certiflow");
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "The action could not be completed." : message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if (App.window() != null) alert.getDialogPane().setPrefWidth(Math.min(480, Math.max(280, App.window().getWidth() - 40)));
        return alert;
    }
    public static void error(String message) { alert(Alert.AlertType.ERROR, message).showAndWait(); }
    public static void info(String message) { alert(Alert.AlertType.INFORMATION, message).showAndWait(); }
    public static boolean confirm(String message) {
        return alert(Alert.AlertType.CONFIRMATION, message).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
