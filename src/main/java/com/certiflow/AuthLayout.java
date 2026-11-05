package com.certiflow;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A wide welcome panel becomes a compact masthead when the viewport narrows. */
final class AuthLayout {
    private AuthLayout() {}

    static VBox create(VBox content) { return create(content, false); }

    static VBox create(VBox content, boolean canReturn) {
        return create(content, canReturn ? App::showOptionView : null);
    }

    static VBox create(VBox content, Runnable returnToSignIn) {
        VBox root = new VBox();
        root.setId("auth-page");
        root.getStyleClass().add("auth-page");
        root.setMinWidth(0);

        VBox introduction = introduction();
        introduction.setId("auth-introduction");
        VBox compactBrand = compactBrand();
        compactBrand.setId("auth-compact-brand");
        VBox surface = new VBox(24, compactBrand, content);
        surface.setId("auth-surface");
        surface.setMinWidth(0);
        surface.setMaxWidth(Double.MAX_VALUE);
        surface.getStyleClass().add("auth-surface");
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);

        GridPane layout = new GridPane();
        layout.setId("auth-layout");
        layout.setMinWidth(0);
        layout.setMaxWidth(1120);
        layout.setAlignment(Pos.CENTER);
        layout.add(introduction, 0, 0);
        layout.add(surface, 1, 0);
        GridPane.setHgrow(surface, Priority.ALWAYS);
        GridPane.setHgrow(introduction, Priority.ALWAYS);
        GridPane.setValignment(surface, VPos.CENTER);
        GridPane.setValignment(introduction, VPos.CENTER);
        GridPane.setFillHeight(surface, false);
        GridPane.setFillHeight(introduction, false);

        VBox canvas = new VBox(layout);
        canvas.setMinWidth(0);
        canvas.setMinHeight(Region.USE_PREF_SIZE);
        canvas.setAlignment(Pos.CENTER);
        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setId("auth-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinWidth(0);
        scroll.setMinHeight(0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("auth-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        javafx.scene.layout.StackPane paper = new javafx.scene.layout.StackPane(new AcademicBackdrop(), scroll);
        paper.setMinSize(0, 0);
        VBox.setVgrow(paper, Priority.ALWAYS);
        if (returnToSignIn != null) {
            javafx.scene.control.Button back = Ui.iconButton("back", "Return to sign in", returnToSignIn);
            back.setId("return-button");
            back.setText("Back");
            back.setMinWidth(84);
            back.setPrefWidth(84);
            back.setMaxWidth(84);
            back.getStyleClass().add("page-back-button");
            surface.getChildren().add(1, back);
        }
        root.getChildren().add(paper);

        Runnable resize = () -> {
            boolean wide = root.getWidth() >= 960;
            boolean small = root.getWidth() < 480;
            introduction.setVisible(wide);
            introduction.setManaged(wide);
            compactBrand.setVisible(!wide);
            compactBrand.setManaged(!wide);
            layout.setHgap(wide ? 64 : 0);
            layout.setMaxWidth(wide ? 1120 : 520);
            GridPane.setColumnIndex(surface, wide ? 1 : 0);
            layout.getColumnConstraints().clear();
            if (wide) {
                ColumnConstraints left = column(52);
                ColumnConstraints right = column(48);
                layout.getColumnConstraints().addAll(left, right);
            } else {
                layout.getColumnConstraints().add(column(100));
            }
            canvas.setPadding(new Insets(wide ? 44 : (small ? 16 : 28)));
            surface.setPadding(new Insets(wide ? 36 : (small ? 22 : 30)));
            if (wide) root.getStyleClass().remove("auth-compact");
            else if (!root.getStyleClass().contains("auth-compact")) root.getStyleClass().add("auth-compact");
        };
        root.widthProperty().addListener((observable, before, after) -> resize.run());
        resize.run();
        return root;
    }

    private static ColumnConstraints column(double percentage) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(0);
        column.setPercentWidth(percentage);
        column.setHgrow(Priority.ALWAYS);
        column.setHalignment(HPos.LEFT);
        column.setFillWidth(true);
        return column;
    }

    private static VBox introduction() {
        VBox intro = new VBox(28);
        intro.setMinWidth(0);
        intro.getStyleClass().add("auth-introduction");
        Node artwork = Ui.logo(180); artwork.setId("auth-hero-logo");
        HBox logo = new HBox(artwork);
        logo.setAlignment(Pos.CENTER);
        logo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        logo.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        logo.getStyleClass().add("auth-logo-tile");
        Label title = label("Your work.\nReady for what’s next.", "auth-hero-title");
        Label subtitle = label("Build your portfolio, manage NOC applications, and discover student work in one place.", "auth-hero-subtitle");
        VBox copy = new VBox(16, title, subtitle);
        VBox steps = new VBox(18,
            step("01", "Show what you can do", "Bring your projects and achievements together."),
            step("02", "Keep applications moving", "Submit NOC requests and follow their progress."),
            step("03", "Discover student work", "Explore portfolios shared by their owners."));
        javafx.scene.image.ImageView books = AcademicArt.books(130);
        HBox artRow = new HBox(18, logo, books);
        artRow.setAlignment(Pos.CENTER_LEFT);
        intro.getChildren().addAll(artRow, copy, steps);
        return intro;
    }

    private static VBox compactBrand() {
        Node artwork = Ui.logoMark(40); artwork.setId("auth-compact-logo");
        HBox logo = new HBox(artwork);
        logo.setAlignment(Pos.CENTER);
        logo.setMinSize(52, 52); logo.setPrefSize(52, 52); logo.setMaxSize(52, 52);
        logo.getStyleClass().add("auth-compact-logo");
        VBox wordmark = new VBox(3, label("Certiflow", "auth-brand-name"), label("Projects, approvals & discovery", "auth-brand-caption"));
        wordmark.setMinWidth(0);
        HBox line = new HBox(12, logo, wordmark);
        line.setAlignment(Pos.CENTER_LEFT);
        return new VBox(line);
    }

    private static HBox step(String index, String title, String subtitle) {
        Label number = label(index, "auth-step-number");
        number.setMinWidth(34);
        VBox copy = new VBox(4, label(title, "auth-step-title"), label(subtitle, "auth-step-subtitle"));
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox row = new HBox(14, number, copy);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
