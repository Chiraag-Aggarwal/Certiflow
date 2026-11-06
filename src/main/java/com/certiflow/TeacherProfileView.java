package com.certiflow;

import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Editable personal details with account permissions kept separate. */
public class TeacherProfileView {
    private final VBox view;

    public TeacherProfileView() {
        AppData.User user = App.user();
        boolean viewer = user.role() == AppData.Role.VIEWER;
        VBox identity = Ui.card();
        identity.setId("profile-identity-panel");
        identity.getStyleClass().add("profile-identity-card");
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
        VBox basics = new VBox(16,
            PageComponents.section("profile", "The essentials", viewer ? "Your name and professional background." : "Help your institution recognise you."),
            Ui.grid(2, 250, field("Full name", name), field(viewer?"Company / organisation (optional)":"Department", department),
                field(viewer?"Organisation ID (optional)":"Student / staff ID (optional)", number), field(viewer?"Job title / interests":"Programme / teaching area", programme)));
        VBox story = new VBox(16,
            PageComponents.section("portfolios", "Tell your story", "A few details make your experience easier to understand."),
            field("About me · up to 1,000 characters", bio),
            field("Skills and interests · up to 300 characters", skills));
        VBox links = new VBox(16,
            PageComponents.section("discover", "Make a connection", "Add a website or professional profile that represents you."),
            field("Website or professional profile (optional)", website));
        for (VBox section : new VBox[]{basics, story, links}) {
            section.setMinWidth(0);
            section.getStyleClass().add("profile-form-section");
        }
        Label identityNotice = Ui.muted(viewer
            ? "Your company and professional details are self-reported. Choosing Recruiter / Viewer does not verify a company or grant staff access."
            : "Your name, department and institution ID are self-reported until an administrator checks them. Editing those details removes the previous verification.");
        identityNotice.getStyleClass().add("profile-form-notice");
        VBox account = Ui.card(Ui.section("Edit your profile", viewer
            ? "Introduce yourself and the work you are interested in."
            : "Keep your background, interests and goals up to date."),
            basics, story, links, identityNotice, feedback, save);
        account.setId("profile-account-panel");
        account.getStyleClass().add("profile-editor-card");
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
        boolean viewer = user.role() == AppData.Role.VIEWER;
        Label avatar = new Label(initials(user.name()));
        avatar.setAccessibleText("Initials for " + user.name());
        avatar.getStyleClass().add("profile-avatar");
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(70, 70);
        avatar.setPrefSize(70, 70);
        avatar.setMaxSize(70, 70);
        Label name = Ui.title(user.name());
        name.getStyleClass().add("profile-display-name");
        Label role = PageComponents.tag(Ui.roleName(user.role()) + " account", "profile-role-pill");
        VBox title = new VBox(8, name, role);
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox introduction = new HBox(16, avatar, title);
        introduction.setAlignment(Pos.CENTER_LEFT);
        introduction.setMinWidth(0);
        introduction.getStyleClass().add("profile-introduction");

        Label verification = Ui.text(user.identityVerified() ? "Identity checked by an administrator" : "Identity details are self-reported");
        verification.setId("profile-verification");
        verification.getStyleClass().addAll("status-pill", user.identityVerified() ? "pill-accepted" : "pill-review");
        VBox accountDetails = new VBox(7, eyebrow("SIGN-IN EMAIL"), Ui.text(user.email()),
            Ui.muted("Verified sign-in email"), new Separator(),
            eyebrow(viewer ? "ORGANISATION ID" : "STUDENT / STAFF ID"), Ui.text(user.displayId()), verification);
        accountDetails.setMinWidth(0);
        accountDetails.getStyleClass().add("profile-account-details");

        VBox protection = new VBox(10,
            PageComponents.section("accounts", "Account protection", "Your role and verified email are protected."),
            Ui.muted("Ask your administrator about staff access. To reset your password, sign out and choose Forgot password on the sign-in screen."));
        protection.setMinWidth(0);
        protection.getStyleClass().add("profile-protection");
        panel.getChildren().setAll(introduction, accountDetails, completionCard(user), protection);
    }

    private static VBox completionCard(AppData.User user) {
        int percentage = completion(user);
        Label value = Ui.title(percentage + "%");
        value.getStyleClass().add("profile-completion-value");
        VBox heading = new VBox(5, eyebrow("PROFILE DETAILS"),
            Ui.text(percentage == 100 ? "Your introduction is complete" : "Make your profile your own"),
            Ui.muted((percentage / 20) + " of 5 details added"));
        heading.setMinWidth(0);
        HBox.setHgrow(heading, Priority.ALWAYS);
        HBox summary = new HBox(12, heading, value);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.setMinWidth(0);

        // This meter counts saved fields only. It does not measure trust or achievements.
        ProgressBar progress = new ProgressBar(percentage / 100.0);
        progress.setId("profile-completion");
        progress.setAccessibleText("Profile details: " + percentage + " percent complete");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setMinWidth(0);
        progress.setMinHeight(10);
        progress.setPrefHeight(10);
        progress.setMaxHeight(10);
        progress.getStyleClass().add("profile-completion-meter");

        String organisation = user.role() == AppData.Role.VIEWER ? "Organisation" : "Department";
        String programme = user.role() == AppData.Role.VIEWER ? "Professional interests" : "Programme / area";
        GridPane checklist = Ui.grid(2, 145,
            completionStep("Full name", user.name()), completionStep(organisation, user.department()),
            completionStep(programme, user.programme()), completionStep("About me", user.bio()),
            completionStep("Skills and interests", user.skills()));
        checklist.setHgap(10);
        checklist.setVgap(8);
        VBox card = new VBox(16, summary, progress, checklist,
            Ui.muted("Completeness only measures filled fields. It does not verify achievements or grant staff access."));
        card.setMinWidth(0);
        card.getStyleClass().add("profile-completion-card");
        return card;
    }

    private static HBox completionStep(String title, String value) {
        boolean complete = value != null && !value.isBlank();
        Label mark = new Label(complete ? "✓" : "+");
        mark.setAlignment(Pos.CENTER);
        mark.setMinSize(22, 22);
        mark.setPrefSize(22, 22);
        mark.setMaxSize(22, 22);
        mark.getStyleClass().add("profile-step-mark");
        Label label = Ui.text(title);
        label.getStyleClass().add("profile-step-label");
        HBox row = new HBox(8, mark, label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setAccessibleText(title + (complete ? ": added" : ": not added"));
        row.getStyleClass().addAll("profile-step", complete ? "profile-step-complete" : "profile-step-open");
        return row;
    }

    private static Label eyebrow(String text) {
        Label label = Ui.muted(text);
        label.getStyleClass().add("profile-eyebrow");
        return label;
    }

    private static String initials(String name) {
        String cleaned = safe(name).trim();
        if (cleaned.isEmpty()) return "?";
        String[] parts = cleaned.split("\\s+");
        String first = parts[0].substring(0, parts[0].offsetByCodePoints(0, 1));
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, parts[parts.length - 1].offsetByCodePoints(0, 1)) : "";
        return (first + last).toUpperCase(Locale.ROOT);
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
