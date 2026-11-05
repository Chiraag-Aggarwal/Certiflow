package com.certiflow;

import java.io.IOException;
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
        requestsList.setId("request-card-grid");
        search.textProperty().addListener((observable, before, after) -> refresh());
        view = Ui.page("Student NOC requests", "Requests",
                Ui.muted("Approve a request or explain why it needs to be rejected. Decisions are shared with the student."),
                search, count, requestsList);
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
        if (visible.isEmpty()) requestsList.getChildren().add(Ui.card(Ui.muted(pending.isEmpty()
                ? "All caught up. There are no pending requests."
                : "No requests match your search. Clear the search to show all pending requests.")));
    }

    private VBox requestCard(AppData.NocRequest request) {
        AppData.User student = App.data().users().stream().filter(u -> u.id().equals(request.studentId())).findFirst().orElse(null);
        String identity = student == null ? request.studentName() : student.name() + " · ID: " + student.displayId();
        TextField note = new TextField();
        note.setPromptText("Decision note (required to reject)");
        note.setId("decision-note-" + request.id());
        Button approve = Ui.button("Approve", () -> decide(request, AppData.Status.APPROVED, note));
        approve.setId("approve-" + request.id());
        Button reject = Ui.secondary("Reject", () -> decide(request, AppData.Status.REJECTED, note));
        reject.setId("reject-" + request.id());
        reject.getStyleClass().add("danger-button");
        GridPane actions = Ui.grid(2, 110, approve, reject);
        return Ui.card(Ui.title(request.type()),
                Ui.text(identity),
                Ui.muted(student != null && student.identityVerified() ? "Current identity checked by an administrator" : "Identity details are self-reported; check institutional records"),
                Ui.muted(request.recipientId().isBlank() ? "Shared staff queue" : "Assigned to " + App.data().contactName(request.recipientId())),
                Ui.muted(request.id() + " · Submitted " + Ui.date(request.submittedAt())),
                Ui.text(request.reason()), Ui.messageContact("Message student", request.studentId()), note, actions);
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
