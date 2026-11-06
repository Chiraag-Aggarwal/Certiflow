package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Read-only publications deliberately shared with signed-in Certiflow users. */
final class CommunityView {
    private final VBox view;
    private final GridPane items = Ui.grid(2, 320);
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final Label feedback = AccountView.feedback("community-feedback");
    private final Label resultCount = Ui.muted("");
    private List<PublicSharing.PublishedItem> publications = List.of();
    private boolean loading;

    CommunityView() {
        this(App.user().role() == AppData.Role.VIEWER);
    }

    CommunityView(boolean discover) {
        search.setId("community-search");
        search.setPromptText("Search public titles, summaries or display names");
        search.setMinWidth(0);
        filter.getItems().addAll("All publications", "Projects and achievements", "NOC applications");
        filter.setValue("All publications");
        filter.setMinWidth(0);
        filter.setMaxWidth(Double.MAX_VALUE);
        filter.setId("community-filter");
        Button refresh = Ui.secondary("Refresh publications", this::load);
        refresh.setId("community-refresh");
        items.setId("community-items");
        items.setMinWidth(0);

        VBox welcome = Ui.welcome(discover ? "Meet the work behind the potential" : "Explore shared work",
            discover
                ? "Discover student projects, achievements and shared NOC updates. Follow your curiosity and explore what they are building."
                : "Projects and application summaries that students have chosen to publish.");
        if (discover) {
            welcome.getChildren().add(Ui.actions(
                Ui.secondary("Complete your profile", App::showViewerProfile),
                Ui.secondary("Contact teaching staff", App::showMessages)));
        }

        resultCount.setId("community-result-count");
        resultCount.getStyleClass().add("collection-summary");
        VBox tools = Ui.card(PageComponents.section("discover", "Find your next discovery",
                "Explore projects, achievements and shared application updates."),
            Ui.grid(2, 250, search, filter), Ui.grid(2, 220, resultCount, refresh));
        tools.getStyleClass().add("collection-tools");
        Label publicationNote = Ui.muted("Work is student-provided. NOC status comes from the original application. Viewing a publication never grants approval or editing access.");
        publicationNote.getStyleClass().add("section-note");
        view = Ui.page(discover ? "Discover" : "Community", discover ? "Discover" : "Community",
            welcome, tools, feedback, items, publicationNote);

        search.textProperty().addListener((observable, before, after) -> render());
        filter.valueProperty().addListener((observable, before, after) -> render());
        // Wait until this page is the active root before starting its initial request.
        view.sceneProperty().addListener((observable, before, after) -> {
            if (after != null) Platform.runLater(() -> {
                if (view.getScene() != null && view.getScene().getRoot() == view) load();
            });
        });
        render();
    }

    private void load() {
        if (loading) return;
        loading = true;
        Ui.feedback(feedback, "Loading community publications…", true);
        Background.run(items, PublicSharing::community, rows -> {
            loading = false;
            publications = rows;
            render();
            Ui.feedback(feedback, rows.size() + " shared publication(s).", true);
        }, error -> {
            loading = false;
            Ui.feedback(feedback, error.getMessage(), false);
        });
    }

    /** Apply local filters to the already-public projection, never to private source records. */
    private void render() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        String category = filter.getValue();
        items.getChildren().clear();
        publications.stream()
            .filter(publication -> category == null || category.equals("All publications")
                || category.equals("NOC applications") == publication.sourceKind().equals("NOC"))
            .filter(publication -> (publication.title() + " " + publication.summary() + " "
                + publication.displayName() + " " + publication.category()).toLowerCase(Locale.ROOT).contains(query))
            .forEach(publication -> items.getChildren().add(publicationCard(publication)));

        int matches = items.getChildren().size();
        resultCount.setText(matches + " of " + publications.size() + " shared publication"
            + (publications.size() == 1 ? "" : "s"));
        if (items.getChildren().isEmpty()) {
            items.getChildren().add(WorkspaceCards.empty("Discoveries start with shared work",
                publications.isEmpty()
                    ? "This space is ready for student projects, achievements and NOC updates. Only work deliberately shared by its owner appears here."
                    : "No publications match yet. Try another search or publication type.",
                "discover"));
        }
    }

    private VBox publicationCard(PublicSharing.PublishedItem publication) {
        boolean noc = publication.sourceKind().equals("NOC");
        Label title = Ui.title(publication.title());
        title.getStyleClass().add("publication-title");
        VBox card = Ui.card(PageComponents.tag(publication.category(), noc ? "tag-warm" : ""), title,
            PageComponents.section(noc ? "noc" : "portfolios", publication.displayName(),
                Ui.date(publication.publishedAt())));
        card.getStyleClass().addAll("publication-card", noc ? "publication-noc" : "publication-project");

        if (noc) card.getChildren().add(PageComponents.tag("Current status · " + publication.status(), "tag-muted"));
        if (!publication.summary().isBlank()) card.getChildren().add(Ui.text(publication.summary()));
        if (!publication.link().isBlank()) {
            card.getChildren().add(Ui.secondary("Open published link", () -> App.openWebLink(publication.link())));
        }
        if (!publication.files().isEmpty()) {
            VBox files = new VBox(8, Ui.muted(publication.files().size() + " shared supporting file"
                + (publication.files().size() == 1 ? "" : "s")));
            files.setMinWidth(0);
            files.getStyleClass().add("publication-files");
            for (PublicSharing.FileItem file : publication.files()) {
                files.getChildren().add(Ui.secondary("Open " + file.fileName(), () ->
                    Background.run(view, () -> PublicSharing.download(file), App::openFile,
                        error -> Ui.error(error.getMessage()))));
            }
            card.getChildren().add(files);
        }
        if (List.of("LINK", "BOTH").contains(publication.audience())) {
            card.getChildren().add(Ui.secondary("Open public web page",
                () -> App.openWebLink(PublicSharing.publicUrl(publication))));
        }
        return card;
    }

    VBox getView() {
        return view;
    }
}
