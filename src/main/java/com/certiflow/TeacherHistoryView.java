package com.certiflow;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Search and decision status filtering both operate on saved decisions. */
public class TeacherHistoryView {
    private final VBox view;
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final Label count = Ui.muted("");
    private final GridPane decisions = Ui.grid(2, 350);
    private final GridPane totals = Ui.grid(3, 150);

    public TeacherHistoryView() {
        search.setPromptText("Search student name, student ID, request ID or type");
        search.setId("history-search");
        search.setMinWidth(0);
        search.setAccessibleText("Search saved decisions");
        decisions.setId("history-card-grid");
        filter.getItems().addAll("All decisions", "Approved", "Rejected");
        filter.setValue("All decisions");
        filter.setMaxWidth(Double.MAX_VALUE);
        filter.setId("history-filter");
        search.textProperty().addListener((observable, before, after) -> refresh());
        filter.valueProperty().addListener((observable, before, after) -> refresh());
        VBox filters = Ui.card(PageComponents.section("history", "Decision archive", "A clear record of completed reviews, most recent first."), totals,
                Ui.grid(2, 260, new VBox(8, Ui.muted("Search decisions"), search),
                        new VBox(8, Ui.muted("Decision status"), filter)), count);
        filters.getStyleClass().add("review-filter");
        view = Ui.page("NOC history", "History", filters, decisions);
        refresh();
    }

    private void refresh() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        List<AppData.NocRequest> history = App.data().requests().stream()
                .filter(r -> r.status() != AppData.Status.PENDING).toList();
        long approved = history.stream().filter(r -> r.status() == AppData.Status.APPROVED).count();
        totals.getChildren().setAll(PageComponents.tag(history.size() + " decisions", "review-total"),
                PageComponents.tag(approved + " approved", "review-good"),
                PageComponents.tag((history.size() - approved) + " rejected", "review-bad"));
        List<AppData.NocRequest> visible = history.stream()
                .filter(r -> "All decisions".equals(filter.getValue())
                        || r.status().name().equalsIgnoreCase(filter.getValue()))
                .filter(r -> (r.studentName() + " " + r.studentId() + " " + r.id() + " "
                        + r.type() + " " + r.decisionNote()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(AppData.NocRequest::decidedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
        count.setText("Showing " + visible.size() + " of " + history.size() + " decisions");
        decisions.getChildren().clear();
        for (AppData.NocRequest request : visible) {
            VBox dates = new VBox(6, Ui.muted("Submitted · " + Ui.date(request.submittedAt())),
                    Ui.muted("Decision · " + Ui.date(request.decidedAt())));
            dates.setMinWidth(0);
            dates.getStyleClass().add("review-timeline");
            VBox card = Ui.card(Ui.status(request.status()), PageComponents.section("noc", request.type(), request.studentName()),
                    Ui.muted("Student reference · " + request.studentId()), Ui.muted("Application reference · " + request.id()), dates,
                    Ui.text("Application: " + request.reason()));
            card.getStyleClass().addAll("review-record", request.status() == AppData.Status.APPROVED ? "review-approved" : "review-rejected");
            if (!request.decisionNote().isBlank()) {
                VBox note = Ui.card(PageComponents.tag("DECISION NOTE", "review-caption"), Ui.text(request.decisionNote()));
                note.getStyleClass().add("review-detail");
                card.getChildren().add(note);
            }
            card.getChildren().add(PrivateRemarks.forRequest(request));
            decisions.getChildren().add(card);
        }
        if (visible.isEmpty()) decisions.getChildren().add(WorkspaceCards.empty(
                history.isEmpty() ? "Your review history starts here" : "No matching decisions",
                history.isEmpty() ? "Approved and rejected applications will appear here after a review."
                : "Try another search or choose All decisions.", "history"));
    }

    public VBox getView() { return view; }
}
