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

    public TeacherHistoryView() {
        search.setPromptText("Search student name, student ID, request ID or type");
        search.setId("history-search");
        decisions.setId("history-card-grid");
        filter.getItems().addAll("All decisions", "Approved", "Rejected");
        filter.setValue("All decisions");
        filter.setMaxWidth(Double.MAX_VALUE);
        filter.setId("history-filter");
        search.textProperty().addListener((observable, before, after) -> refresh());
        filter.valueProperty().addListener((observable, before, after) -> refresh());
        view = Ui.page("NOC history", "History",
                Ui.muted("Saved decisions, most recent first."),
                Ui.grid(2, 260, new VBox(8, Ui.muted("Search decisions"), search),
                        new VBox(8, Ui.muted("Decision status"), filter)), count, decisions);
        refresh();
    }

    private void refresh() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        List<AppData.NocRequest> history = App.data().requests().stream()
                .filter(r -> r.status() != AppData.Status.PENDING).toList();
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
            VBox card = Ui.card(Ui.title(request.type()),
                    Ui.text(request.studentName() + " · " + request.studentId()),
                    Ui.muted(request.id()), Ui.status(request.status()),
                    Ui.muted("Submitted " + Ui.date(request.submittedAt())),
                    Ui.muted("Decided " + Ui.date(request.decidedAt())),
                    Ui.text("Application: " + request.reason()));
            if (!request.decisionNote().isBlank()) card.getChildren().add(Ui.text("Decision note: " + request.decisionNote()));
            decisions.getChildren().add(card);
        }
        if (visible.isEmpty()) decisions.getChildren().add(Ui.card(Ui.muted(history.isEmpty()
                ? "No decisions yet. Approved and rejected requests will appear here."
                : "No decisions match these filters.")));
    }

    public VBox getView() { return view; }
}
