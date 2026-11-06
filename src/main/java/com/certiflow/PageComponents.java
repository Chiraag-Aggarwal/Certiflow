package com.certiflow;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Small, shared page details keep forms and record collections visually consistent. */
final class PageComponents {
    private PageComponents() {}

    static HBox section(String icon, String heading, String detail) {
        StackPane symbol = new StackPane(Icons.of(icon));
        symbol.getStyleClass().add("section-symbol");
        symbol.setMinSize(42, 42);
        symbol.setPrefSize(42, 42);
        symbol.setMaxSize(42, 42);
        VBox copy = new VBox(5, Ui.title(heading));
        if (detail != null && !detail.isBlank()) copy.getChildren().add(Ui.muted(detail));
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox header = new HBox(12, symbol, copy);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinWidth(0);
        header.getStyleClass().add("section-heading");
        return header;
    }

    static Label tag(String text, String style) {
        Label tag = Ui.text(text);
        tag.getStyleClass().add("detail-tag");
        if (style != null && !style.isBlank()) tag.getStyleClass().add(style);
        tag.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return tag;
    }

    static HBox pageHeading(String title, String route) {
        String eyebrow = switch (route) {
            case "Profile" -> "MAKE IT YOURS";
            case "Portfolios" -> "WORK WORTH SHARING";
            case "NOC", "Requests" -> "YOUR NEXT OPPORTUNITY";
            case "History" -> "EVERY DECISION, IN ONE PLACE";
            case "Students" -> "PEOPLE & POTENTIAL";
            case "Export" -> "FROM RECORDS TO INSIGHT";
            case "Accounts" -> "A TRUSTED WORKSPACE";
            default -> "YOUR WORKSPACE";
        };
        String description = switch (route) {
            case "Profile" -> "Let your interests, experience and ambitions introduce you.";
            case "Portfolios" -> App.user().role().isStaff()
                ? "Explore the work shared with you and leave useful feedback."
                : "Give your projects, achievements and experience a place to grow.";
            case "NOC" -> "Start an application, choose your teacher and follow each step.";
            case "Requests" -> "Give each application a clear decision and a helpful next step.";
            case "History" -> "Revisit completed reviews and the feedback behind them.";
            case "Students" -> "Find students and connect them with the right guidance.";
            case "Export" -> "Choose the records you need, preview them and save a report.";
            case "Accounts" -> "Give the right people the right access, with clear safeguards.";
            default -> "Keep your academic work moving.";
        };
        Label label = Ui.muted(eyebrow);
        label.getStyleClass().add("page-kicker");
        Label heading = Ui.title(title);
        heading.getStyleClass().add("page-title");
        VBox copy = new VBox(6, label, heading, Ui.muted(description));
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        StackPane symbol = new StackPane(Icons.of(route.toLowerCase(java.util.Locale.ROOT)));
        symbol.getStyleClass().add("page-heading-symbol");
        symbol.setMinSize(54, 54);
        symbol.setPrefSize(54, 54);
        symbol.setMaxSize(54, 54);
        HBox header = new HBox(18, copy, symbol);
        header.setMinWidth(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-introduction");
        return header;
    }
}
