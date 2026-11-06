package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Creates NOC applications and displays the student's persisted decisions. */
public class StudentNocView {
    private final VBox view;
    private final VBox applications = new VBox(12);
    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Label count = Ui.muted("");

    public StudentNocView() {
        ComboBox<String> type = new ComboBox<>();
        type.setId("noc-type");
        type.getItems().addAll(AppData.REQUEST_TYPES);
        type.setPromptText("Choose an application type");
        type.setMaxWidth(Double.MAX_VALUE);
        type.setAccessibleText("NOC application type");

        TextArea reason = new TextArea();
        reason.setId("noc-reason");
        reason.setPromptText("Explain why you need this NOC (required)");
        reason.setWrapText(true);
        reason.setPrefRowCount(3);
        reason.setAccessibleText("Reason for the NOC application");
        RecipientPicker recipient = new RecipientPicker("noc", "Any available staff member",
                "Leave unassigned for the shared staff queue, or choose one teacher. Assigned applications are visible to that teacher and administrators.");

        Label feedback = Ui.text("");
        feedback.setId("noc-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);
        Button submit = Ui.button("Submit application", () -> {});
        submit.setOnAction(event -> {
            String selectedType = type.getValue(), enteredReason = reason.getText(), recipientId = recipient.recipientId();
            AppData.User actor = App.user();
            Ui.feedback(feedback,"Submitting…",true);
            Background.run(submit.getParent(), () -> App.data().submit(actor,selectedType,enteredReason,recipientId), request -> {
                type.getSelectionModel().clearSelection();
                reason.clear();
                recipient.select("");
                search.clear();
                statusFilter.setValue("All statuses");
                refresh();
                Ui.feedback(feedback, "Application submitted " + (request.recipientId() == null || request.recipientId().isBlank()
                    ? "to the shared staff queue." : "to " + App.data().contactName(request.recipientId()) + "."), true);
            }, exception -> Ui.feedback(feedback, exception.getMessage(), false));
        });
        submit.setId("noc-submit");
        VBox form = Ui.card(Ui.section("New application", "Select a type and enter a reason. One pending application per type is allowed."),
                Ui.text("Application type"), type, Ui.text("Reason"), reason, recipient, submit, feedback);
        form.setId("noc-form-panel");

        search.setId("noc-search");
        search.setPromptText("Search by type, request ID, or reason");
        statusFilter.setId("noc-status-filter");
        statusFilter.getItems().addAll("All statuses", "Pending", "Approved", "Rejected");
        statusFilter.setValue("All statuses");
        statusFilter.setMaxWidth(Double.MAX_VALUE);
        statusFilter.setAccessibleText("Filter applications by status");
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
        VBox history = new VBox(16, Ui.section("Application history", "Review submitted requests and staff decisions."),
                search, statusFilter, count, applications);
        history.setId("noc-history-panel");
        view = Ui.page("My NOC Applications", "NOC", Ui.columns(form, history));
    }

    private void refresh() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        String filter = statusFilter.getValue();
        List<AppData.NocRequest> matches = App.data().requestsFor(App.user().id()).stream()
                .filter(request -> matches(request, query))
                .filter(request -> filter == null || filter.equals("All statuses")
                        || request.status().name().equalsIgnoreCase(filter))
                .toList();
        count.setText(matches.size() + (matches.size() == 1 ? " application" : " applications"));
        applications.getChildren().clear();
        if (matches.isEmpty()) {
            applications.getChildren().add(Ui.muted("No applications match these filters."));
        } else {
            matches.forEach(request -> applications.getChildren().add(requestCard(request)));
        }
    }

    static boolean matches(AppData.NocRequest request, String query) {
        return (request.id() + " " + request.type() + " " + request.reason() + " " + request.status().name()
                + " " + App.data().contactName(request.recipientId()))
                .toLowerCase(Locale.ROOT).contains(query);
    }

    static VBox requestCard(AppData.NocRequest request) {
        VBox card = Ui.card(Ui.text(request.type()), Ui.status(request.status()),
                Ui.muted(request.id() + " • Submitted " + Ui.date(request.submittedAt())),
                Ui.muted(request.recipientId() == null || request.recipientId().isBlank() ? "Shared staff queue"
                    : "Assigned to " + App.data().contactName(request.recipientId())),
                Ui.text(request.reason()));
        if (request.decidedAt() != null) {
            card.getChildren().add(Ui.muted("Decided " + Ui.date(request.decidedAt())));
        }
        if (request.decisionNote() != null && !request.decisionNote().isBlank()) {
            card.getChildren().add(Ui.text("Teacher's note: " + request.decisionNote()));
        }
        if (request.recipientId() != null && !request.recipientId().isBlank())
            card.getChildren().add(Ui.messageContact("Message assigned staff", request.recipientId()));
        if (request.studentId().equals(App.user().id())) {
            card.getChildren().add(PrivateRemarks.forRequest(request));
            card.getChildren().add(SharingControls.forRequest(request));
        }
        return card;
    }

    public VBox getView() {
        return view;
    }
}
