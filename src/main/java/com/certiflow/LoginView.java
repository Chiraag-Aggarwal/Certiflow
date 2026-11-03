package com.certiflow;

import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginView {
    private final VBox view;

    public LoginView(String role, String emoji) {
        AppData.Role selectedRole = AppData.Role.valueOf(role.toUpperCase(Locale.ROOT));
        String username = role.toLowerCase(Locale.ROOT);
        VBox content = new VBox(18);
        content.setMinWidth(0);

        VBox heading = new VBox(8,
            AuthLayout.label(role + " login", "auth-form-title"),
            AuthLayout.label("Welcome back. Sign in to continue.", "auth-form-subtitle"));

        TextField userField = new TextField();
        userField.setId("login-username");
        userField.setPromptText("Enter your username");
        userField.setAccessibleText("Username");
        userField.setMinWidth(0);
        userField.getStyleClass().add("auth-input");
        PasswordField passwordField = new PasswordField();
        passwordField.setId("login-password");
        passwordField.setPromptText("Enter your password");
        passwordField.setAccessibleText("Password");
        passwordField.setMinWidth(0);
        passwordField.getStyleClass().add("auth-input");
        Label usernameLabel = AuthLayout.label("Username", "auth-field-label");
        usernameLabel.setLabelFor(userField);
        Label passwordLabel = AuthLayout.label("Password", "auth-field-label");
        passwordLabel.setLabelFor(passwordField);
        VBox fields = new VBox(16,
            new VBox(7, usernameLabel, userField),
            new VBox(7, passwordLabel, passwordField));

        Label error = AuthLayout.label("", "auth-error");
        error.setId("login-error");
        error.setManaged(false);
        error.setVisible(false);

        Button login = new Button("Login");
        login.setId("login-submit");
        login.getStyleClass().add("auth-login-button");
        login.setMinHeight(48);
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        Runnable submit = () -> {
            String message = null;
            if (userField.getText().isBlank() || passwordField.getText().isEmpty())
                message = "Enter both a username and password.";
            else if (!App.login(selectedRole, userField.getText(), passwordField.getText()))
                message = "Incorrect username or password for " + role + ".";
            if (message != null) {
                error.setText(message);
                error.setVisible(true);
                error.setManaged(true);
            }
        };
        login.setOnAction(event -> submit.run());
        passwordField.setOnAction(event -> submit.run());

        Label credentials = AuthLayout.label("Demo username: " + username + "\nDemo password: " + username + "123", "auth-demo-credentials");
        VBox demo = new VBox(7, AuthLayout.label("TRY THE LOCAL DEMO", "auth-demo-title"), credentials);
        demo.getStyleClass().add("auth-demo-box");
        Hyperlink help = new Hyperlink("Login help");
        help.setMinHeight(44);
        help.getStyleClass().add("auth-help");
        help.setOnAction(event -> Ui.info("This is the local demo version.\n\nUse " + username + " / " + username + "123 for this role.\n\nOnline accounts and password recovery are not connected."));
        content.getChildren().addAll(heading, fields, error, login, demo, help);
        view = AuthLayout.create(content, true);
    }

    public VBox getView() { return view; }
}
