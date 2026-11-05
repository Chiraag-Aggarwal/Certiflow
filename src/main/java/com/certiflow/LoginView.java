package com.certiflow;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class LoginView {
    private final VBox view;
    public LoginView() { this(""); }
    public LoginView(String ignoredRole, String initialEmail) { this(initialEmail); }
    public LoginView(String initialEmail) {
        VBox content = new VBox(16);
        content.setMinWidth(0);
        TextField email = AccountView.input("Email address", "login-email");
        email.setText(initialEmail == null ? "" : initialEmail.trim());
        PasswordField password = AccountView.password("Password", "login-password");
        Label feedback = AccountView.feedback("login-error");
        Button login = AccountView.action("Sign in", "login-submit");
        login.setDefaultButton(true);
        Runnable submit = () -> {
            String address=email.getText(), secret=password.getText();
            try { AppData.email(address); if (secret.isEmpty()) throw new IllegalArgumentException("Enter your password."); }
            catch (IllegalArgumentException error) { Ui.feedback(feedback,error.getMessage(),false); return; }
            Ui.feedback(feedback,"Signing in…",true);
            Background.run(content, () -> App.data().authenticate(address,secret), account -> {
                password.clear();
                if (account.isPresent()) App.completeLogin(account.get());
                else Ui.feedback(feedback,"Sign-in could not be completed. Check your email and password, then try again.",false);
            }, error -> { password.clear(); Ui.feedback(feedback,error.getMessage(),false); });
        };
        login.setOnAction(event -> submit.run()); password.setOnAction(event -> submit.run());
        Hyperlink register = new Hyperlink("Create an account");
        register.setId("open-registration"); register.setOnAction(event -> App.showAccountView("register","",email.getText()));
        Hyperlink verify = new Hyperlink("Verify email / resend code");
        verify.setId("open-verification"); verify.setOnAction(event -> App.showAccountView("verify","",email.getText()));
        Hyperlink recover = new Hyperlink("Forgot password?");
        recover.setId("open-recovery"); recover.setOnAction(event -> App.showAccountView("recover","",email.getText()));
        content.getChildren().addAll(AuthLayout.label("Welcome back","auth-form-title"),
            AuthLayout.label("Sign in with your email. Your account opens the right workspace automatically.","auth-form-subtitle"),
            AccountView.field("Email",email),AccountView.field("Password",password),feedback,login,register,verify,recover);
        if (!App.data().configured()) { login.setDisable(true); register.setDisable(true); verify.setDisable(true); recover.setDisable(true);
            Ui.feedback(feedback,"The database connection has not been configured yet.",false); }
        view = AuthLayout.create(content);
    }
    public VBox getView() { return view; }
}
