package com.certiflow;

import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class OptionView {
    private final VBox view;

    public OptionView() {
        VBox content = new VBox(22);
        content.getChildren().addAll(
            AuthLayout.label("Please select your role", "auth-form-title"),
            AuthLayout.label("Choose the workspace that fits what you need to do.", "auth-form-subtitle"));
        VBox roles = new VBox(12);
        roles.setMinWidth(0);
        roles.getChildren().addAll(
            role("Student", "Submit applications and manage your portfolio.", "S"),
            role("Teacher", "Review requests and guide your students.", "T"),
            role("Admin", "Oversee applications, records and reports.", "A"));
        Label mode = AuthLayout.label("Local demo · sample accounts available on login", "auth-footnote");
        content.getChildren().addAll(roles, mode);
        view = AuthLayout.create(content);
    }

    private Button role(String role, String description, String initial) {
        Label badge = new Label(initial);
        badge.getStyleClass().add("auth-role-badge");
        Label name = AuthLayout.label(role, "auth-role-name");
        Label detail = AuthLayout.label(description, "auth-role-description");
        name.setMinHeight(Region.USE_PREF_SIZE);
        detail.setMinHeight(Region.USE_PREF_SIZE);
        VBox copy = new VBox(4, name, detail);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label arrow = new Label("›");
        arrow.getStyleClass().add("auth-role-arrow");
        HBox graphic = new HBox(14, badge, copy, arrow);
        graphic.setMinWidth(0);
        graphic.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setId("role-" + role.toLowerCase(Locale.ROOT));
        button.setAccessibleText(role);
        button.getStyleClass().add("auth-role-button");
        button.setGraphic(graphic);
        button.setMinWidth(0);
        button.setMinHeight(80);
        button.setMaxWidth(Double.MAX_VALUE);
        graphic.prefWidthProperty().bind(button.widthProperty().subtract(34));
        // A Button measures its graphic before the nested wrapping labels have a final width.
        // Keep the graphic tall enough for the complete description after the width is known.
        button.widthProperty().addListener((observable, before, after) -> {
            double textWidth = Math.max(30, after.doubleValue() - 34 - 38 - 12 - 28);
            double graphicHeight = Math.max(42, name.prefHeight(textWidth) + 4 + detail.prefHeight(textWidth));
            graphic.setPrefHeight(graphicHeight);
            button.setPrefHeight(Math.max(80, graphicHeight + 28));
        });
        button.setOnAction(event -> App.showLoginView(role, ""));
        return button;
    }

    public VBox getView() { return view; }
}
