package com.certiflow;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Registration, email verification, and recovery use Supabase Auth. */
final class AccountView {
    private final VBox view;
    AccountView(String mode,String role) { this(mode,role,""); }
    AccountView(String mode,String role,String initialEmail) {
        VBox content = new VBox(14); content.setMinWidth(0);
        TextField email = input("Your email address","account-email");
        email.setText(initialEmail == null ? "" : initialEmail.trim());
        Label status = feedback("account-feedback");
        boolean register = mode.equals("register"), recover = mode.equals("recover");
        if (!recover) content.getChildren().add(AuthLayout.label(register ? "STEP 1 OF 2 · ACCOUNT DETAILS" : "STEP 2 OF 2 · EMAIL VERIFICATION", "auth-footnote"));
        content.getChildren().addAll(AuthLayout.label(register ? "Create your account" : recover ? "Reset your password" : "Verify your email","auth-form-title"),
            AuthLayout.label(register ? "Join as a student or recruiter / viewer. Teacher access is assigned by an administrator. Next, you’ll verify your email." : recover ? "Request a recovery code, then enter it below with a new password." : "Paste the verification code from your email below. Check your spam folder if needed.","auth-form-subtitle"),field("Email",email));
        if (register) {
            TextField name=input("Full name","account-name"), department=input("Department","account-department"), number=input("Student ID (optional)","account-number");
            ComboBox<AppData.Role> accountType = new ComboBox<>();
            accountType.setId("account-role"); accountType.setAccessibleText("Account type");
            accountType.setMinWidth(0); accountType.setMaxWidth(Double.MAX_VALUE);
            accountType.getStyleClass().add("auth-input");
            accountType.getItems().setAll(AppData.Role.STUDENT, AppData.Role.VIEWER);
            accountType.setConverter(new StringConverter<>() {
                @Override public String toString(AppData.Role value) {
                    return value == null ? "" : value == AppData.Role.VIEWER ? "Recruiter / Viewer" : "Student";
                }
                @Override public AppData.Role fromString(String value) { return null; }
            });
            accountType.setValue(AppData.Role.STUDENT);
            Label typeHelp = AuthLayout.label("Create a portfolio, submit NOC applications, and share your work.", "auth-footnote");
            VBox studentNumber = field("Student ID (optional)",number);
            Label numberHelp = AuthLayout.label("Leave blank if your institution has not assigned you an ID.","auth-footnote");
            accountType.valueProperty().addListener((observable, before, after) -> {
                boolean student = after == AppData.Role.STUDENT;
                studentNumber.setVisible(student); studentNumber.setManaged(student);
                numberHelp.setVisible(student); numberHelp.setManaged(student);
                department.setPromptText(student ? "Department" : "Organisation (optional)");
                typeHelp.setText(student ? "Create a portfolio, submit NOC applications, and share your work."
                    : "Explore work that students choose to share. This account does not review or approve applications.");
            });
            PasswordField password=password("At least 12 characters","account-password"), confirm=password("Repeat your password","account-confirm");
            Button submit=action("Create account","account-submit");
            submit.setOnAction(event -> {
                AppData.Role basicRole=accountType.getValue();
                if (basicRole != AppData.Role.STUDENT && basicRole != AppData.Role.VIEWER) { Ui.feedback(status,"Choose Student or Recruiter / Viewer.",false); return; }
                String address=email.getText(), secret=password.getText(), fullName=name.getText(), dept=department.getText(), id=basicRole == AppData.Role.STUDENT ? number.getText() : "";
                if (!secret.equals(confirm.getText())) { Ui.feedback(status,"The passwords do not match.",false); return; }
                Background.run(content, () -> { App.data().register(address,secret,fullName,dept,id,basicRole); return true; }, ignored -> {
                    password.clear(); confirm.clear(); App.showAccountView("verify","",address);
                }, error -> Ui.feedback(status,error.getMessage(),false));
            });
            content.getChildren().addAll(field("I’m joining as",accountType),typeHelp,field("Full name",name),field("Department / organisation",department),studentNumber,numberHelp,
                field("Password",password),field("Confirm password",confirm),status,submit);
            Hyperlink verify = new Hyperlink("Already have a code? Verify email");
            verify.setId("open-verification");
            verify.setOnAction(event -> App.showAccountView("verify","",email.getText()));
            content.getChildren().add(verify);
        } else {
            TextField code=input("Code from your email","account-code");
            code.sceneProperty().addListener((observable,before,after) -> { if(after != null && !email.getText().isBlank()) javafx.application.Platform.runLater(code::requestFocus); });
            Button send=action(recover ? "Send recovery code" : "Resend verification code","account-send-code");
            send.setOnAction(event -> {
                String address=email.getText();
                Background.run(content, () -> { if(recover) App.data().requestRecovery(address); else App.data().resendConfirmation(address); return true; },
                    ignored -> Ui.feedback(status,"If your account is eligible, a code will arrive in your inbox. Check your spam folder too.",true),
                    error -> Ui.feedback(status,error.getMessage(),false));
            });
            if(recover) content.getChildren().add(send);
            content.getChildren().add(field("Verification code",code));
            PasswordField password=password("At least 12 characters","account-password"), confirm=password("Repeat new password","account-confirm");
            if(recover) content.getChildren().addAll(field("New password",password),field("Confirm new password",confirm));
            Button submit=action(recover ? "Save new password" : "Verify email","account-submit");
            submit.setOnAction(event -> {
                String address=email.getText(), token=code.getText(), secret=password.getText();
                if (recover && !secret.equals(confirm.getText())) { Ui.feedback(status,"The passwords do not match.",false); return; }
                Background.run(content, () -> { if(recover) App.data().resetPassword(address,token,secret); else App.data().verifyEmail(address,token); return true; }, ignored -> {
                    password.clear(); confirm.clear(); code.clear(); Ui.feedback(status,recover ? "Password updated. You can now sign in." : "Email verified. You can now sign in.",true);
                }, error -> Ui.feedback(status,error.getMessage(),false));
            });
            content.getChildren().addAll(status,submit);
            if(!recover) content.getChildren().add(send);
        }
        Hyperlink back = new Hyperlink("Back to sign in"); back.setId("account-back-to-login");
        back.setOnAction(event -> App.showLoginView("",email.getText())); content.getChildren().add(back);
        view=AuthLayout.create(content,() -> App.showLoginView("",email.getText()));
    }
    static TextField input(String prompt,String id) { TextField field=new TextField(); decorate(field,prompt,id); return field; }
    static PasswordField password(String prompt,String id) { PasswordField field=new PasswordField(); decorate(field,prompt,id); return field; }
    private static void decorate(TextField field,String prompt,String id) { field.setId(id); field.setPromptText(prompt); field.setAccessibleText(prompt); field.setMinWidth(0); field.getStyleClass().add("auth-input"); }
    static VBox field(String title,Control field) { Label label=AuthLayout.label(title,"auth-field-label"); label.setLabelFor(field); VBox box=new VBox(6,label,field); box.setMinWidth(0); return box; }
    static Label feedback(String id) { Label label=AuthLayout.label("","auth-error"); label.setId(id); label.setVisible(false); label.setManaged(false); return label; }
    static Button action(String text,String id) { Button button=new Button(text); button.setId(id); button.getStyleClass().add("auth-login-button"); button.setMinHeight(44); button.setMaxWidth(Double.MAX_VALUE); return button; }
    VBox getView() { return view; }
}
