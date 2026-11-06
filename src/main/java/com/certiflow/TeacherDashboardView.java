package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Pending requests, including working review actions and a combined search. */
public class TeacherDashboardView {
    private final VBox view;
    private final TextField search = new TextField();
    private final GridPane requestsList = Ui.grid(2, 350);
    private final Label count = Ui.muted("");

    public TeacherDashboardView() {
        search.setPromptText("Search student name, student ID, request ID or type");
        search.setId("request-search");
        search.setMinWidth(0);
        search.setAccessibleText("Search pending applications");
        requestsList.setId("request-card-grid");
        search.textProperty().addListener((observable, before, after) -> refresh());
        VBox filters = Ui.card(PageComponents.section("requests", "Review queue", "Find an application and help the student take their next step."),
                search, count);
        filters.getStyleClass().add("review-filter");
        view = Ui.page("Student NOC requests", "Requests", filters, requestsList);
        refresh();
    }

    private void refresh() {
        List<AppData.NocRequest> pending = App.data().requests().stream()
                .filter(r -> r.status() == AppData.Status.PENDING).toList();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        List<AppData.NocRequest> visible = pending.stream().filter(r -> matches(r, query)).toList();
        count.setText("Showing " + visible.size() + " of " + pending.size() + " pending requests");
        requestsList.getChildren().clear();
        for (AppData.NocRequest request : visible) requestsList.getChildren().add(requestCard(request));
        if (visible.isEmpty()) requestsList.getChildren().add(WorkspaceCards.empty(
                pending.isEmpty() ? "You're all caught up" : "No matching applications",
                pending.isEmpty() ? "New applications available to you will appear here when students submit them."
                : "Try a different student, ID or application type, or clear the search.", "requests"));
    }

    private VBox requestCard(AppData.NocRequest request) {
        AppData.User student = App.data().users().stream().filter(u -> u.id().equals(request.studentId())).findFirst().orElse(null);
        String identity = student == null ? request.studentName() : student.name() + " · ID: " + student.displayId();
        TextField note = new TextField();
        note.setPromptText("Decision note (required to reject)");
        note.setId("decision-note-" + request.id());
        note.setMinWidth(0);
        note.setAccessibleText("Decision note for " + request.type());
        Button approve = Ui.button("Approve", () -> decide(request, AppData.Status.APPROVED, note));
        approve.setId("approve-" + request.id());
        Button reject = Ui.secondary("Reject", () -> decide(request, AppData.Status.REJECTED, note));
        reject.setId("reject-" + request.id());
        reject.getStyleClass().add("danger-button");
        GridPane actions = Ui.grid(2, 110, approve, reject);
        VBox reason = Ui.card(PageComponents.tag("APPLICATION REASON", "review-caption"), Ui.text(request.reason()));
        reason.getStyleClass().add("review-detail");
        VBox decision = new VBox(10, Ui.text("Your decision"),
                Ui.muted("Decision notes are shared with the student. Use Private remarks for separate feedback."), note, actions);
        decision.setMinWidth(0);
        decision.getStyleClass().add("review-decision");
        VBox card = Ui.card(Ui.status(request.status()), PageComponents.section("noc", request.type(), identity),
                Ui.muted(student != null && student.identityVerified() ? "Current identity checked by an administrator" : "Identity details are self-reported; check institutional records"),
                PageComponents.tag(request.recipientId().isBlank() ? "Shared staff queue" : "Assigned to " + App.data().contactName(request.recipientId()), "review-route"),
                Ui.muted("Submitted " + Ui.date(request.submittedAt())), Ui.muted("Reference · " + request.id()),
                reason, Ui.messageContact("Message student", request.studentId()),
                PrivateRemarks.forRequest(request), decision);
        card.getStyleClass().addAll("review-record", "review-pending");
        return card;
    }

    private void decide(AppData.NocRequest request, AppData.Status status, TextField note) {
        if (status == AppData.Status.REJECTED && note.getText().isBlank()) {
            Ui.error("Please enter a reason before rejecting this request.");
            note.requestFocus();
            return;
        }
        String action = status == AppData.Status.APPROVED ? "Approve" : "Reject";
        if (!Ui.confirm(action + " " + request.id() + " for " + request.studentName() + "?")) return;
        AppData.User actor = App.user();
        String decisionNote = note.getText();
        Background.run(view, () -> { App.data().decide(actor, request.id(), status, decisionNote); return true; }, ignored -> {
            refresh();
            Ui.toast(request.id() + (status == AppData.Status.APPROVED ? " approved." : " rejected.") + " The student can now see your decision.");
        }, ex -> Ui.error("The decision could not be saved. " + ex.getMessage()));
    }

    private static boolean matches(AppData.NocRequest request, String query) {
        String profile = App.data().users().stream().filter(u -> u.id().equals(request.studentId())).map(u -> u.name() + " " + u.studentNumber()).findFirst().orElse("");
        return (profile + " " + request.studentName() + " " + request.studentId() + " " + request.id()
                + " " + request.type() + " " + request.reason()).toLowerCase(Locale.ROOT).contains(query);
    }

    public VBox getView() { return view; }
}
