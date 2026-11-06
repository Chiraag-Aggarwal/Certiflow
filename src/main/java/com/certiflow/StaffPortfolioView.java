package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Staff can view only the portfolio entries returned by their database access rules. */
public final class StaffPortfolioView {
    private final VBox view;
    private final TextField search = new TextField();
    private final GridPane entries = Ui.grid(2, 360);
    private final Label count = Ui.muted("");

    public StaffPortfolioView() {
        search.setId("shared-portfolio-search");
        search.setPromptText("Search student, title, category, or description");
        search.setAccessibleText("Search shared portfolio entries");
        search.setMinWidth(0);
        search.textProperty().addListener((o, before, after) -> refresh());
        count.getStyleClass().add("work-result-count");
        VBox filters = Ui.card(PageComponents.section("portfolios", "Student work", App.user().role() == AppData.Role.ADMIN
                ? "Review portfolio entries and supporting files, then leave useful private feedback."
                : "Explore the entries students have shared directly with you."), search, count);
        filters.getStyleClass().add("work-filter");
        view = Ui.page("Shared portfolios", "Portfolios",
            filters, entries);
        refresh();
    }

    private void refresh() {
        entries.getChildren().clear();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        for (AppData.Project project : App.data().projects()) {
            String owner = App.data().contactName(project.studentId());
            if (!(owner + " " + project.title() + " " + project.kind() + " " + project.description()).toLowerCase(Locale.ROOT).contains(query)) continue;
            List<AppData.PortfolioItem> files = App.data().portfolios().stream().filter(f -> project.id().equals(f.projectId())).toList();
            VBox card = Ui.card(PageComponents.section("portfolios", project.title(), "By " + owner),
                PageComponents.tag(project.kind(), "work-category"),
                Ui.muted("Added " + Ui.date(project.createdAt())),
                Ui.muted("Student-provided content · profile identity verification does not verify these achievements"),
                Ui.muted(project.recipientId() == null || project.recipientId().isBlank() ? "Unassigned · administrator access"
                    : "Shared with " + App.data().contactName(project.recipientId())));
            card.setId("shared-portfolio-" + project.id());
            card.getStyleClass().addAll("work-record", "work-project");
            if (!project.description().isBlank()) card.getChildren().add(Ui.text(project.description()));
            if (!project.link().isBlank()) card.getChildren().add(Ui.secondary("Open project link", () -> App.openWebLink(project.link())));
            card.getChildren().add(Ui.messageContact("Message student", project.studentId()));
            card.getChildren().add(PrivateRemarks.forProject(project));
            card.getChildren().add(Ui.muted(files.size() + " supporting file(s)"));
            for (AppData.PortfolioItem file : files) {
                VBox attachment = Ui.card(PageComponents.section("noc", file.title(), file.fileName()),
                    Ui.secondary("Open document", () -> Background.run(view, () -> App.data().downloadPortfolio(file), App::openFile, error -> Ui.error(error.getMessage()))));
                attachment.getStyleClass().add("work-file-card");
                card.getChildren().add(attachment);
            }
            entries.getChildren().add(card);
        }
        int visible = entries.getChildren().size();
        count.setText(visible + (visible == 1 ? " entry" : " entries") + " available for review");
        if (visible == 0) entries.getChildren().add(WorkspaceCards.empty("No shared entries to show",
            query.isBlank() ? "Entries shared with you will appear here. Use Refresh to retrieve new submissions."
                : "Try a different student name, title, or category.", "portfolios"));
    }

    public VBox getView() { return view; }
}
