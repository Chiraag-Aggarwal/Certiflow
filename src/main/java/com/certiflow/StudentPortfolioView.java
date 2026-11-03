package com.certiflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Imports local supporting documents into the student's saved portfolio. */
public class StudentPortfolioView {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp", "txt");
    private final VBox view;
    private final TextField title = new TextField();
    private final TextField search = new TextField();
    private final VBox documents = new VBox(12);
    private final Label selectedFile = Ui.muted("No file selected");
    private final Label count = Ui.muted("");
    private final Label feedback = Ui.muted("");
    private Path selectedPath;

    public StudentPortfolioView() {
        title.setId("portfolio-title");
        title.setPromptText("Document title");
        title.setAccessibleText("Portfolio document title");
        Button choose = Ui.secondary("Choose document…", this::chooseFile);
        choose.setId("portfolio-choose");
        Button save = Ui.button("Add to portfolio", this::saveDocument);
        save.setId("portfolio-save");
        feedback.setId("portfolio-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);
        VBox form = Ui.card(Ui.section("Add a document",
                "PDF, image, or text files, up to 25 MB. A copy is saved with this app."),
                Ui.text("Title"), title, choose, selectedFile, save, feedback);
        form.setId("portfolio-form-panel");
        search.setId("portfolio-search");
        search.setPromptText("Search document title or filename");
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
        VBox saved = new VBox(16, Ui.section("Saved documents", "Your portfolio files are kept with this app."),
                search, count, documents);
        saved.setId("portfolio-list-panel");
        view = Ui.page("My Portfolios", "Portfolios", Ui.columns(form, saved));
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a portfolio document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents and images",
                "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.txt"));
        File chosen = chooser.showOpenDialog(App.window());
        if (chosen == null) {
            return;
        }
        if (!allowed(chosen.getName())) {
            Ui.feedback(feedback, "Choose a PDF, PNG, JPG, JPEG, GIF, WEBP, or TXT file.", false);
            return;
        }
        selectedPath = chosen.toPath();
        selectedFile.setText(chosen.getName());
        if (title.getText().isBlank()) {
            String name = chosen.getName();
            int dot = name.lastIndexOf('.');
            title.setText(dot > 0 ? name.substring(0, dot) : name);
        }
    }

    private void saveDocument() {
        if (selectedPath == null) {
            Ui.feedback(feedback, "Choose a document before adding it to your portfolio.", false);
            return;
        }
        if (!allowed(selectedPath.getFileName().toString())) {
            Ui.feedback(feedback, "This file type is not supported.", false);
            return;
        }
        try {
            AppData.PortfolioItem item = App.data().addPortfolio(App.user(), title.getText(), selectedPath);
            title.clear();
            selectedPath = null;
            selectedFile.setText("No file selected");
            search.clear();
            refresh();
            Ui.feedback(feedback, "Saved “" + item.title() + "” to your portfolio.", true);
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            Ui.feedback(feedback, exception.getMessage(), false);
        }
    }

    private void refresh() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        List<AppData.PortfolioItem> matches = App.data().portfoliosFor(App.user().id()).stream()
                .filter(item -> (item.title() + " " + item.fileName()).toLowerCase(Locale.ROOT).contains(query))
                .toList();
        documents.getChildren().clear();
        count.setText(matches.size() + (matches.size() == 1 ? " document" : " documents"));
        if (matches.isEmpty()) {
            documents.getChildren().add(Ui.muted(query.isEmpty()
                    ? "No documents yet. Add your first portfolio document above."
                    : "No documents match your search."));
        } else {
            for (AppData.PortfolioItem item : matches) {
                documents.getChildren().add(Ui.card(Ui.text(item.title()),
                        Ui.muted(item.fileName() + " • Added " + Ui.date(item.addedAt())),
                        Ui.secondary("Open document", () -> openDocument(item))));
            }
        }
    }

    private void openDocument(AppData.PortfolioItem item) {
        if (!allowed(item.fileName())) {
            Ui.error("This document type cannot be opened from Certiflow.");
            return;
        }
        Path path = App.data().portfolioPath(item);
        if (!allowed(path.getFileName().toString()) || !Files.isRegularFile(path)) {
            Ui.error("The saved document is missing or has an unsupported file type. Add it again from the original file.");
            return;
        }
        App.openFile(path);
    }

    private static boolean allowed(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && ALLOWED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public VBox getView() {
        return view;
    }
}
