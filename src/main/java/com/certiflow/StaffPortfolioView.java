package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Staff can view only the portfolio entries returned by their database access rules. */
public final class StaffPortfolioView {
    private final VBox view;
    private final TextField search = new TextField();
    private final VBox entries = new VBox(14);

    public StaffPortfolioView() {
        search.setId("shared-portfolio-search");
        search.setPromptText("Search student, title, category, or description");
        search.setAccessibleText("Search shared portfolio entries");
        search.textProperty().addListener((o, before, after) -> refresh());
        view = Ui.page("Shared portfolios", "Portfolios",
            Ui.section("Student work", App.user().role() == AppData.Role.ADMIN
                ? "Review student portfolio entries and their supporting files. Students manage their own content."
                : "Students can share an entry directly with you. Their other entries remain private."),
            search, entries);
        refresh();
    }

    private void refresh() {
        entries.getChildren().clear();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        for (AppData.Project project : App.data().projects()) {
            String owner = App.data().contactName(project.studentId());
            if (!(owner + " " + project.title() + " " + project.kind() + " " + project.description()).toLowerCase(Locale.ROOT).contains(query)) continue;
            List<AppData.PortfolioItem> files = App.data().portfolios().stream().filter(f -> project.id().equals(f.projectId())).toList();
            VBox card = Ui.card(Ui.title(project.title()), Ui.text(owner),
                Ui.muted(project.kind() + " · Added " + Ui.date(project.createdAt())),
                Ui.muted("Student-provided content · profile identity verification does not verify these achievements"),
                Ui.muted(project.recipientId() == null || project.recipientId().isBlank() ? "Unassigned · administrator access"
                    : "Shared with " + App.data().contactName(project.recipientId())));
            card.setId("shared-portfolio-" + project.id());
            if (!project.description().isBlank()) card.getChildren().add(Ui.text(project.description()));
            if (!project.link().isBlank()) card.getChildren().add(Ui.secondary("Open project link", () -> App.openWebLink(project.link())));
            card.getChildren().add(Ui.messageContact("Message student", project.studentId()));
            card.getChildren().add(PrivateRemarks.forProject(project));
            card.getChildren().add(Ui.muted(files.size() + " supporting file(s)"));
            for (AppData.PortfolioItem file : files) card.getChildren().add(Ui.card(Ui.text(file.title()), Ui.muted(file.fileName()),
                Ui.secondary("Open document", () -> Background.run(view, () -> App.data().downloadPortfolio(file), App::openFile, error -> Ui.error(error.getMessage())))));
            entries.getChildren().add(card);
        }
        if (entries.getChildren().isEmpty()) entries.getChildren().add(Ui.card(Ui.title("No shared entries to show"),
            Ui.muted(query.isBlank() ? "Entries shared with you will appear here. Use Refresh to retrieve new submissions."
                : "Try a different student name, title, or category.")));
    }

    public VBox getView() { return view; }
}
