package com.certiflow;

import javafx.scene.layout.VBox;

/** Account information shared by all roles reflects the signed-in account. */
public class TeacherProfileView {
    private final VBox view;
    public TeacherProfileView() {
        AppData.User user = App.user();
        VBox identity = Ui.card(AcademicArt.sticker(), Ui.section(user.name(), Ui.roleName(user.role())),
                Ui.muted("ID: " + user.id()), Ui.text(user.department()), Ui.text(user.email()));
        identity.setId("profile-identity-panel");
        VBox account = Ui.card(Ui.section("Local demo account", "Account details"),
                Ui.text("Username: " + user.username()),
                Ui.muted("These sample profiles are read-only. Requests, decisions and imported documents are saved on this computer."));
        account.setId("profile-account-panel");
        view = Ui.page("My profile", "Profile", Ui.columns(identity, account));
    }
    public VBox getView() { return view; }
}
