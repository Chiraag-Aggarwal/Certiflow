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
    private final VBox overview = new VBox();

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
        VBox form = Ui.card(PageComponents.section("noc", "New application", "A clear request helps your reviewer make a decision."),
                field("01 · Choose your application", type), field("02 · Explain your request", reason),
                recipient, Ui.muted("You can have one pending application of each type. Follow its progress beside this form."), submit, feedback);
        form.setId("noc-form-panel");
        form.getStyleClass().add("work-editor");

        search.setId("noc-search");
        search.setPromptText("Search by type, request ID, or reason");
        search.setAccessibleText("Search your NOC applications");
        search.setMinWidth(0);
        statusFilter.setId("noc-status-filter");
        statusFilter.getItems().addAll("All statuses", "Pending", "Approved", "Rejected");
        statusFilter.setValue("All statuses");
        statusFilter.setMaxWidth(Double.MAX_VALUE);
        statusFilter.setAccessibleText("Filter applications by status");
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
        count.getStyleClass().add("work-result-count");
        VBox filters = Ui.card(Ui.grid(2, 175, search, statusFilter), count);
        filters.getStyleClass().add("work-filter");
        VBox history = new VBox(16, PageComponents.section("history", "Your application journey", "Track decisions, read feedback and stay up to date."),
                overview, filters, applications);
        history.setMinWidth(0);
        history.setId("noc-history-panel");
        view = Ui.page("My NOC Applications", "NOC", Ui.columns(form, history));
    }

    private void refresh() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        String filter = statusFilter.getValue();
        List<AppData.NocRequest> requests = App.data().requestsFor(App.user().id());
        List<AppData.NocRequest> matches = requests.stream()
                .filter(request -> matches(request, query))
                .filter(request -> filter == null || filter.equals("All statuses")
                        || request.status().name().equalsIgnoreCase(filter))
                .toList();
        count.setText(matches.size() + (matches.size() == 1 ? " application" : " applications"));
        // Summaries describe the complete collection; the count above follows the current filters.
        overview.getChildren().setAll(Ui.grid(3, 90,
            summary(requests, AppData.Status.PENDING, "Pending", "work-pending"),
            summary(requests, AppData.Status.APPROVED, "Approved", "work-approved"),
            summary(requests, AppData.Status.REJECTED, "Rejected", "work-rejected")));
        applications.getChildren().clear();
        if (matches.isEmpty()) {
            applications.getChildren().add(WorkspaceCards.empty(requests.isEmpty() ? "Your next step starts here" : "No matching applications",
                requests.isEmpty() ? "Submit a request using the form. Your application, decision and private feedback will stay together here."
                    : "Try another search or choose a different status.", "noc"));
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
        VBox card = Ui.card(PageComponents.section("noc", request.type(), "Submitted " + Ui.date(request.submittedAt())),
                Ui.status(request.status()),
                Ui.muted(request.recipientId() == null || request.recipientId().isBlank() ? "Shared staff queue"
                    : "Assigned to " + App.data().contactName(request.recipientId())),
                Ui.text(request.reason()));
        card.getStyleClass().addAll("work-record", "work-record-" + request.status().name().toLowerCase(Locale.ROOT));
        Label reference = Ui.muted("Request ID · " + request.id());
        reference.getStyleClass().add("work-reference");
        card.getChildren().add(reference);
        if (request.decidedAt() != null) {
            card.getChildren().add(Ui.muted("Decided " + Ui.date(request.decidedAt())));
        }
        if (request.decisionNote() != null && !request.decisionNote().isBlank()) {
            VBox note = new VBox(6, Ui.text("Decision note"), Ui.muted(request.decisionNote()));
            note.getStyleClass().add("work-note");
            card.getChildren().add(note);
        }
        if (request.recipientId() != null && !request.recipientId().isBlank())
            card.getChildren().add(Ui.messageContact("Message assigned staff", request.recipientId()));
        if (request.studentId().equals(App.user().id())) {
            card.getChildren().add(PrivateRemarks.forRequest(request));
            card.getChildren().add(SharingControls.forRequest(request));
        }
        return card;
    }

    private static VBox field(String title, javafx.scene.control.Control control) {
        Label caption = Ui.text(title);
        caption.setLabelFor(control);
        caption.getStyleClass().add("work-field-label");
        control.setMinWidth(0);
        VBox field = new VBox(8, caption, control);
        field.setMinWidth(0);
        return field;
    }

    private static VBox summary(List<AppData.NocRequest> requests, AppData.Status status, String title, String colour) {
        long number = requests.stream().filter(request -> request.status() == status).count();
        Label value = Ui.title(Long.toString(number));
        VBox summary = new VBox(5, value, Ui.muted(title));
        summary.setMinWidth(0);
        summary.getStyleClass().addAll("work-summary", colour);
        return summary;
    }

    public VBox getView() {
        return view;
    }
}
