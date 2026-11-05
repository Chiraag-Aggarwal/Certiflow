package com.certiflow;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Editable personal details with account permissions kept separate. */
public class TeacherProfileView {
    private final VBox view;

    public TeacherProfileView() {
        AppData.User user = App.user();
        boolean viewer = user.role()==AppData.Role.VIEWER;
        VBox identity = Ui.card();
        identity.setId("profile-identity-panel");
        renderIdentity(identity, user);

        TextField name = input(user.name(), "Full name", "profile-name");
        TextField department = input(user.department(), viewer?"Company or organisation (optional)":"Department", "profile-department");
        TextField number = input(user.studentNumber(), "Institution ID (optional)", "profile-number");
        TextField programme = input(user.programme(), viewer?"Job title or professional interests":"Course, programme or teaching area", "profile-programme");
        TextField skills = input(user.skills(), "For example: research, Java, public speaking", "profile-skills");
        TextField website = input(user.website(), "https://your-website.example", "profile-website");
        TextArea bio = new TextArea(safe(user.bio()));
        bio.setId("profile-bio"); bio.setPromptText("A short introduction, interests and current goals");
        bio.setAccessibleText("About me"); bio.setWrapText(true); bio.setPrefRowCount(4); bio.setMinWidth(0);
        Label feedback = AccountView.feedback("profile-feedback");
        Button save = Ui.button("Save profile", () -> {}); save.setId("profile-save");
        VBox account = Ui.card(Ui.section("Personal details", viewer?"Introduce yourself and the work you are interested in.":"Tell your institution a little more about you."),
            Ui.grid(2, 250, field("Full name", name), field(viewer?"Company / organisation (optional)":"Department", department),
                field(viewer?"Organisation ID (optional)":"Student / staff ID (optional)", number), field(viewer?"Job title / interests":"Programme / teaching area", programme)),
            field("About me · up to 1,000 characters", bio), field("Skills and interests · up to 300 characters", skills),
            field("Website or professional profile (optional)", website),
            Ui.muted(viewer?"Your company and professional details are self-reported. Choosing Recruiter / Viewer does not verify a company or grant staff access.":"Your name, department and institution ID are self-reported until an administrator checks them. Editing those details removes the previous verification."),
            feedback, save);
        account.setId("profile-account-panel");
        save.setOnAction(event -> {
            String newName = name.getText(), newDepartment = department.getText(), newNumber = number.getText();
            String newBio = bio.getText(), newSkills = skills.getText(), newProgramme = programme.getText(), newWebsite = website.getText();
            Background.run(account, () -> App.data().updateProfile(newName, newDepartment, newNumber, newBio, newSkills, newProgramme, newWebsite), updated -> {
                App.updatedUser(updated); renderIdentity(identity, updated); Ui.feedback(feedback, "Profile updated.", true);
            }, error -> Ui.feedback(feedback, error.getMessage(), false));
        });
        view = Ui.page("My profile", "Profile", Ui.columns(identity, account));
    }

    private static void renderIdentity(VBox panel, AppData.User user) {
        Label verification = Ui.text(user.identityVerified() ? "Identity checked by an administrator" : "Identity details are self-reported");
        verification.setId("profile-verification");
        verification.getStyleClass().addAll("status-pill", user.identityVerified() ? "pill-accepted" : "pill-review");
        ProgressBar progress = new ProgressBar(completion(user) / 100.0);
        progress.setId("profile-completion"); progress.setMaxWidth(Double.MAX_VALUE); progress.setMinWidth(0);
        panel.getChildren().setAll(AcademicArt.sticker(), Ui.section(user.name(), Ui.roleName(user.role()) + " account"),
            Ui.text(user.email()), Ui.muted("Verified sign-in email"), verification,
            Ui.muted((user.role()==AppData.Role.VIEWER?"Organisation ID: ":"Student / staff ID: ") + user.displayId()),
            Ui.text("Profile details · " + completion(user) + "% complete"), progress,
            Ui.muted("Completeness only measures filled fields. It does not verify achievements or grant staff access."),
            Ui.section("Account protection", "Your role and verified email cannot be changed in this form."),
            Ui.muted("Ask your administrator about staff access. To reset your password, sign out and choose Forgot password on the sign-in screen."));
    }

    static int completion(AppData.User user) {
        int filled = 0;
        for (String value : new String[]{user.name(), user.department(), user.programme(), user.bio(), user.skills()})
            if (value != null && !value.isBlank()) filled++;
        return filled * 20;
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static TextField input(String value, String prompt, String id) {
        TextField input = new TextField(safe(value)); input.setId(id); input.setPromptText(prompt); input.setAccessibleText(prompt); input.setMinWidth(0); return input;
    }
    private static VBox field(String title, Control input) {
        Label label = Ui.text(title); label.setLabelFor(input); VBox box = new VBox(7, label, input); box.setMinWidth(0); return box;
    }
    public VBox getView() { return view; }
}
