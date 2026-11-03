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
        javafx.scene.control.Button back = Ui.iconButton("back", "Return to role selection", App::showOptionView);
        back.setId("return-button");
        back.setDisable(!canReturn);
        HBox header = new HBox(back);
        header.setPadding(new Insets(12, 16, 0, 16));
        root.getChildren().addAll(header, paper);

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
        HBox logo = new HBox(Ui.logo(82));
        logo.setAlignment(Pos.CENTER);
        logo.setMaxWidth(100);
        logo.getStyleClass().add("auth-logo-tile");
        Label title = label("Every request.\nOne clear path.", "auth-hero-title");
        Label subtitle = label("Submit documents, request a clearance, and follow each decision from one place.", "auth-hero-subtitle");
        VBox copy = new VBox(16, title, subtitle);
        VBox steps = new VBox(18,
            step("01", "Keep documents together", "Your portfolio, ready when you need it."),
            step("02", "Send a clear request", "Submit an NOC application in a few steps."),
            step("03", "See where it stands", "Track approvals and feedback as they arrive."));
        javafx.scene.image.ImageView books = AcademicArt.books(190);
        HBox artRow = new HBox(24, logo, books);
        artRow.setAlignment(Pos.CENTER_LEFT);
        intro.getChildren().addAll(artRow, copy, steps);
        return intro;
    }

    private static VBox compactBrand() {
        HBox logo = new HBox(Ui.logo(50));
        logo.setAlignment(Pos.CENTER);
        logo.getStyleClass().add("auth-compact-logo");
        VBox wordmark = new VBox(3, label("Certiflow", "auth-brand-name"), label("Student certifications", "auth-brand-caption"));
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
