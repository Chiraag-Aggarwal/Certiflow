package com.certiflow;

import java.util.Locale;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;

/** Administrative decisions use privileged server checks, never profile edits. */
public final class AccountManagementView {
    private final VBox view;
    private final VBox accounts = new VBox(14);
    private final Label feedback = AccountView.feedback("accounts-feedback");
    private final TextField search = new TextField();

    public AccountManagementView() {
        if (App.user().role() != AppData.Role.ADMIN) throw new IllegalStateException("Only administrators can manage account access.");
        search.setId("accounts-search"); search.setPromptText("Find an account by name, email, department or institution ID"); search.setMinWidth(0);
        search.textProperty().addListener((observable, before, after) -> render());
        accounts.setId("accounts-list"); accounts.setMinWidth(0);
        VBox rules = new VBox(10,
                Ui.muted("New users choose Student or Recruiter / Viewer. Everyone uses the same sign-in."),
                Ui.muted("Administrators manage Student, Viewer and Teacher access. Your original administrator can also grant or remove Admin access for other verified accounts. The original administrator account is protected."),
                Ui.muted("Identity verification confirms the name, department and institution ID you reviewed. Portfolio achievements and biographies remain self-reported."));
        rules.setMinWidth(0);
        TitledPane permissions = new TitledPane("How access and identity checks work", rules);
        permissions.setExpanded(false); permissions.setAnimated(false); permissions.setMinWidth(0);
        permissions.getStyleClass().add("access-guidance");
        VBox filters = Ui.card(PageComponents.section("accounts", "People & permissions", "Find an account, check its identity and choose the right access."),
                PageComponents.tag(App.data().canManageAdmins() ? "Administrator delegation available" : "Standard account management", "review-route"),
                search, permissions, feedback);
        filters.getStyleClass().add("review-filter");
        view = Ui.page("Account access", "Accounts", filters, accounts);
        if (!App.data().adminCapabilitiesAvailable()) Ui.feedback(feedback,"Administrator-promotion permissions could not be checked. Refresh this page to try again.",false);
        render();
    }

    private void render() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        var matches = App.data().users().stream().filter(user ->
            (user.name() + " " + user.email() + " " + user.department() + " " + user.displayId()).toLowerCase(Locale.ROOT).contains(query)).toList();
        accounts.getChildren().clear();
        if (matches.isEmpty()) { accounts.getChildren().add(WorkspaceCards.empty("No matching accounts",
                "Try another name, email, department or institution ID.", "accounts")); return; }
        accounts.getChildren().add(Ui.muted(matches.size() + " matching account" + (matches.size() == 1 ? "" : "s")));
        GridPane cards = Ui.grid(2, 350);
        accounts.getChildren().add(cards);
        matches.stream().limit(50).forEach(user -> {
            VBox card = Ui.card(PageComponents.tag(Ui.roleName(user.role()), "review-route"),
                PageComponents.section("profile", user.name(), user.email()),
                Ui.text((user.department().isBlank() ? "Department / organisation not provided" : user.department()) + " · ID: " + user.displayId()),
                PageComponents.tag(user.identityVerified() ? "Identity checked by an administrator" : "Identity details have not been checked",
                        user.identityVerified() ? "review-good" : "review-warn"));
            card.getStyleClass().add("access-card");
            if (!user.id().equals(App.user().id()) && (user.role() != AppData.Role.ADMIN || App.data().canManageAdmins())) {
                ComboBox<AppData.Role> roleChoice = new ComboBox<>();
                roleChoice.getItems().addAll(AppData.Role.STUDENT,AppData.Role.VIEWER,AppData.Role.TEACHER);
                if (App.data().canManageAdmins()) roleChoice.getItems().add(AppData.Role.ADMIN);
                roleChoice.setValue(user.role());roleChoice.setMinWidth(0);roleChoice.setMaxWidth(Double.MAX_VALUE);
                roleChoice.setId("account-role-"+user.id());roleChoice.setAccessibleText("Account access for "+user.name());
                roleChoice.setConverter(new javafx.util.StringConverter<>() {
                    @Override public String toString(AppData.Role role) { return role==null?"":Ui.roleName(role); }
                    @Override public AppData.Role fromString(String text) { return null; }
                });
                Button change = Ui.secondary("Update account access", () -> {
                    AppData.Role next=roleChoice.getValue();
                    if(next==null||next==user.role())return;
                    String prompt = next == AppData.Role.ADMIN
                        ? "Make " + user.name() + " (" + user.email() + ") an administrator? They will be able to review NOCs, access student records and manage Student, Viewer and Teacher roles. Only your original administrator account can appoint other admins."
                        : next == AppData.Role.TEACHER
                        ? "Have you checked institutional records and confirmed that " + user.name() + " (" + user.email() + ") is a teacher? This gives access to the shared request queue, their assigned submissions and the student roster."
                        : "Change " + user.name() + " (" + user.email() + ") to " + Ui.roleName(next) + "? This removes their staff access. Existing records will be preserved.";
                    if (user.role() == AppData.Role.ADMIN && next != AppData.Role.ADMIN) prompt = "Remove administrator access from " + user.name() + " (" + user.email() + ") and give them " + Ui.roleName(next) + " access? Existing records will be preserved.";
                    if (!Ui.confirm(prompt)) return;
                    Background.run(accounts, () -> App.data().setAccountRole(user.id(), next), updated -> {
                        Ui.feedback(feedback, updated.name() + " now has " + Ui.roleName(updated.role()).toLowerCase(Locale.ROOT) + " access.", true); render();
                    }, error -> Ui.feedback(feedback, error.getMessage(), false));
                });
                change.setId("change-role-" + user.id());
                change.disableProperty().bind(roleChoice.valueProperty().isEqualTo(user.role()));
                Button verify = Ui.secondary("Verify identity", () -> {
                    if (!Ui.confirm("Confirm that you checked " + user.name() + "'s name, department and institution ID against institutional records. This does not verify their biography or portfolio claims.")) return;
                    Background.run(accounts, () -> App.data().verifyIdentity(user.id()), updated -> {
                        Ui.feedback(feedback, "Identity verified for " + updated.name() + ".", true); render();
                    }, error -> Ui.feedback(feedback, error.getMessage(), false));
                });
                verify.setId("verify-identity-" + user.id()); verify.setDisable(user.identityVerified());
                VBox controls = new VBox(10, Ui.text("Account access"), roleChoice, Ui.grid(2, 135, change, verify));
                controls.setMinWidth(0);
                controls.getStyleClass().add("review-decision");
                card.getChildren().add(controls);
            } else card.getChildren().add(PageComponents.tag(user.id().equals(App.user().id())?"Your account · access protected":"Administrator access protected", "review-total"));
            cards.getChildren().add(card);
        });
        if (matches.size() > 50) accounts.getChildren().add(Ui.muted("Showing the first 50 matches. Narrow your search to find a specific account."));
    }
    public VBox getView() { return view; }
}
