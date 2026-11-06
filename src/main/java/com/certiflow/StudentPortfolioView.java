package com.certiflow;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Editable portfolio entries with supporting files and optional staff sharing. */
public class StudentPortfolioView {
    private final VBox view;
    private final TextField title = new TextField();
    private final ComboBox<String> kind = new ComboBox<>();
    private final TextArea description = new TextArea();
    private final TextField link = new TextField();
    private final TextField search = new TextField();
    private final VBox entries = new VBox(14);
    private final VBox pending = new VBox(8);
    private final Label editorTitle = Ui.title("Add a portfolio entry");
    private final Label count = Ui.muted("");
    private final Label feedback = Ui.muted("");
    private final Button save;
    private final Button cancel;
    private final RecipientPicker recipient;
    private final List<Path> selected = new ArrayList<>();
    private final Map<String, UploadResult> retryUploads = new HashMap<>();
    private AppData.Project editing;

    public StudentPortfolioView() {
        title.setId("portfolio-title"); title.setPromptText("Project, certificate, or achievement title");
        title.setAccessibleText("Portfolio entry title");
        kind.setId("portfolio-kind"); kind.getItems().addAll(AppData.PROJECT_KINDS);
        kind.setValue("Project"); kind.setMaxWidth(Double.MAX_VALUE); kind.setMinWidth(0);
        kind.setAccessibleText("Portfolio entry category");
        description.setId("portfolio-description"); description.setPromptText("What did you work on, learn, or achieve?");
        description.setWrapText(true); description.setPrefRowCount(4);
        description.setAccessibleText("Portfolio description");
        link.setId("portfolio-link"); link.setPromptText("https://… (optional project or certificate link)");
        link.setAccessibleText("Portfolio project link");
        recipient = new RecipientPicker("portfolio", "Keep private",
            "Private entries stay in your account. Choose staff to share this entry and its files with them; administrators can also review it.");
        Button choose = Ui.secondary("Choose multiple files…", () -> chooseFiles(selected));
        choose.setId("portfolio-choose");
        save = Ui.button("Add portfolio entry", this::saveEntry); save.setId("portfolio-save");
        cancel = Ui.secondary("Cancel editing", this::clearEditor); cancel.setId("portfolio-cancel");
        cancel.setVisible(false); cancel.setManaged(false);
        feedback.setId("portfolio-feedback"); feedback.setVisible(false); feedback.setManaged(false);
        VBox attachments = new VBox(12,
            PageComponents.section("export", "Supporting files", "PDF, images or text · up to 25 MB per file"), choose, pending);
        attachments.setMinWidth(0);
        attachments.getStyleClass().add("work-upload-area");
        VBox form = Ui.card(Ui.row(Icons.of("portfolios"), editorTitle),
            Ui.muted("Give your projects, certificates and achievements a place to grow. You choose what to share."),
            Ui.grid(2, 175, field("Title", title), field("Category", kind)),
            field("Tell the story (optional)", description), field("Project or certificate link (optional)", link),
            recipient, attachments, Ui.grid(2, 160, save, cancel), feedback);
        form.setId("portfolio-form-panel");
        form.getStyleClass().add("work-editor");
        search.setId("portfolio-search"); search.setPromptText("Search entries, descriptions, or filenames");
        search.setAccessibleText("Search your portfolio"); search.setMinWidth(0);
        search.textProperty().addListener((observable, before, after) -> refresh());
        refreshPending(); refresh();
        count.getStyleClass().add("work-result-count");
        VBox filters = Ui.card(search, count); filters.getStyleClass().add("work-filter");
        VBox saved = new VBox(16, PageComponents.section("portfolios", "Your collection", "A closer look at what you have built and learned."), filters, entries);
        saved.setMinWidth(0); saved.setId("portfolio-list-panel");
        view = Ui.page("My portfolios", "Portfolios", Ui.columns(form, saved));
    }

    private List<Path> pickFiles() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Choose supporting portfolio files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF, images and text",
            "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.txt"));
        List<File> files = chooser.showOpenMultipleDialog(App.window());
        return files == null ? List.of() : files.stream().map(File::toPath).map(Path::toAbsolutePath).distinct().toList();
    }

    private void chooseFiles(List<Path> destination) {
        for (Path path : pickFiles()) if (!destination.contains(path)) destination.add(path);
        refreshPending();
    }

    private void refreshPending() {
        pending.getChildren().clear();
        if (selected.isEmpty()) pending.getChildren().add(Ui.muted("No files selected. You can save an entry with just its details or link."));
        else for (Path path : List.copyOf(selected)) {
            Button remove = Ui.secondary("Remove from selection", () -> { selected.remove(path); refreshPending(); });
            VBox selection = new VBox(8, Ui.text(path.getFileName().toString()), remove);
            selection.setMinWidth(0); selection.getStyleClass().add("work-file-selection");
            pending.getChildren().add(selection);
        }
    }

    private record UploadResult(AppData.Project project, List<Path> failed, List<String> errors, int saved) {}

    private UploadResult upload(AppData.Project project, List<Path> files, AppData.User actor, Label progress) {
        List<Path> failed = new ArrayList<>(); List<String> errors = new ArrayList<>(); int saved = 0;
        for (int i = 0; i < files.size(); i++) {
            Path path = files.get(i); int number = i + 1;
            Platform.runLater(() -> Ui.feedback(progress, "Uploading file " + number + " of " + files.size() + ": " + path.getFileName(), true));
            try {
                String name = path.getFileName().toString(); int dot = name.lastIndexOf('.');
                String documentTitle = dot > 0 ? name.substring(0, dot) : name;
                App.data().addPortfolio(actor, documentTitle.substring(0, Math.min(150, documentTitle.length())), path, project.id());
                saved++;
            } catch (Exception error) { failed.add(path); errors.add(path.getFileName() + ": " + error.getMessage()); }
        }
        return new UploadResult(project, List.copyOf(failed), List.copyOf(errors), saved);
    }

    private void saveEntry() {
        String entryTitle = title.getText(), entryKind = kind.getValue(), entryDescription = description.getText(), entryLink = link.getText(), recipientId = recipient.recipientId();
        AppData.Project existing = editing; List<Path> files = List.copyOf(selected); AppData.User actor = App.user();
        Ui.feedback(feedback, "Saving your portfolio entry…", true);
        Background.run(view, () -> {
            AppData.Project project = existing == null
                ? App.data().createProject(actor, entryTitle, entryKind, entryDescription, entryLink, recipientId)
                : App.data().updateProject(existing, entryTitle, entryKind, entryDescription, entryLink, recipientId);
            return upload(project, files, actor, feedback);
        }, result -> {
            if (result.failed().isEmpty()) {
                clearEditor(); search.clear(); refresh();
                Ui.feedback(feedback, "Portfolio entry saved" + (result.saved() == 0 ? "." : " with " + result.saved() + " file(s)."), true);
            } else {
                editing = result.project(); setEditingState(true); selected.clear(); selected.addAll(result.failed()); refreshPending(); refresh();
                Ui.feedback(feedback, failureMessage(result), false);
            }
        }, error -> Ui.feedback(feedback, error.getMessage(), false));
    }

    private static String failureMessage(UploadResult result) {
        return "Entry saved. " + result.saved() + " file(s) uploaded; " + result.failed().size()
            + " failed. Only failed files remain for retry.\n" + String.join("\n", result.errors().stream().limit(3).toList());
    }

    private void clearEditor() {
        editing = null; title.clear(); kind.setValue("Project"); description.clear(); link.clear(); recipient.select("");
        selected.clear(); refreshPending(); setEditingState(false);
    }

    private void setEditingState(boolean isEditing) {
        editorTitle.setText(isEditing ? "Edit portfolio entry" : "Add a portfolio entry");
        save.setText(isEditing ? "Save changes" : "Add portfolio entry");
        cancel.setVisible(isEditing); cancel.setManaged(isEditing);
    }

    private void edit(AppData.Project project) {
        if (editorDirty() && !Ui.confirm("Discard the unsaved changes in the entry form and edit “" + project.title() + "”?")) return;
        editing = project; title.setText(project.title()); kind.setValue(project.kind()); description.setText(project.description());
        link.setText(project.link()); recipient.select(project.recipientId()); selected.clear(); refreshPending(); setEditingState(true);
        feedback.setVisible(false); feedback.setManaged(false);
        ScrollPane scroll = (ScrollPane) view.lookup("#page-scroll"); if (scroll != null) scroll.setVvalue(0);
        title.requestFocus();
    }

    private boolean editorDirty() {
        if (!selected.isEmpty()) return true;
        if (editing == null) return !title.getText().isBlank() || !description.getText().isBlank() || !link.getText().isBlank()
            || !"Project".equals(kind.getValue()) || !recipient.recipientId().isBlank();
        return !title.getText().equals(editing.title()) || !description.getText().equals(editing.description())
            || !link.getText().equals(editing.link()) || !java.util.Objects.equals(kind.getValue(), editing.kind())
            || !recipient.recipientId().equals(editing.recipientId() == null ? "" : editing.recipientId());
    }

    private void refresh() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        List<AppData.Project> projects = App.data().projectsFor(App.user().id());
        List<AppData.PortfolioItem> files = App.data().portfoliosFor(App.user().id());
        entries.getChildren().clear(); int matches = 0;
        for (AppData.Project project : projects) {
            List<AppData.PortfolioItem> attachments = files.stream().filter(f -> project.id().equals(f.projectId())).toList();
            String searchable = project.title() + " " + project.kind() + " " + project.description() + " "
                + String.join(" ", attachments.stream().map(f -> f.title() + " " + f.fileName()).toList());
            if (!searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            entries.getChildren().add(projectCard(project, attachments)); matches++;
        }
        List<AppData.PortfolioItem> ungrouped = files.stream().filter(f -> f.projectId() == null || f.projectId().isBlank())
            .filter(f -> (f.title() + " " + f.fileName()).toLowerCase(Locale.ROOT).contains(query)).toList();
        if (!ungrouped.isEmpty()) {
            VBox legacy = Ui.card(PageComponents.section("noc", "Individual documents", "Your earlier documents are still saved privately."));
            legacy.getStyleClass().add("work-record");
            ungrouped.forEach(f -> legacy.getChildren().add(fileCard(f)));
            entries.getChildren().add(legacy);
        }
        count.setText(projects.size() + " entries · " + files.size() + " supporting files");
        if (matches == 0 && ungrouped.isEmpty()) entries.getChildren().add(WorkspaceCards.empty(query.isEmpty() ? "Your next achievement belongs here" : "No matching entries",
            query.isEmpty() ? "Start with a project, a certificate, or something you are proud of. Add a description, a link, or supporting files whenever you are ready." : "Try another title, category, or filename.", "portfolios"));
    }

    private VBox projectCard(AppData.Project project, List<AppData.PortfolioItem> files) {
        VBox card = Ui.card(PageComponents.section("portfolios", project.title(), "Added " + Ui.date(project.createdAt())),
            PageComponents.tag(project.kind(), "work-category"),
            Ui.muted("Student-provided content · achievements are not independently verified"),
            Ui.muted(project.recipientId() == null || project.recipientId().isBlank() ? "Private · visible to you and administrators" : "Shared with " + App.data().contactName(project.recipientId()) + " and administrators"));
        card.setId("portfolio-entry-" + project.id());
        card.getStyleClass().addAll("work-record", "work-project");
        if (!project.description().isBlank()) card.getChildren().add(Ui.text(project.description()));
        if (!project.link().isBlank()) card.getChildren().add(Ui.secondary("Open project link", () -> App.openWebLink(project.link())));
        if (project.recipientId() != null && !project.recipientId().isBlank()) card.getChildren().add(Ui.messageContact("Message assigned staff", project.recipientId()));
        Button edit = Ui.secondary("Edit entry", () -> edit(project)); edit.setId("portfolio-edit-" + project.id());
        Button add = Ui.secondary("Add files…", () -> addFiles(project)); add.setId("portfolio-add-files-" + project.id());
        Button remove = Ui.secondary("Delete entry", () -> removeProject(project)); remove.setId("portfolio-delete-" + project.id()); remove.getStyleClass().add("danger-button");
        card.getChildren().add(Ui.grid(3, 140, edit, add, remove));
        card.getChildren().addAll(PrivateRemarks.forProject(project),SharingControls.forProject(project));
        UploadResult retry = retryUploads.get(project.id());
        if (retry != null) {
            Button retryButton = Ui.button("Retry failed files only", () -> uploadToProject(project, retry.failed()));
            retryButton.setId("portfolio-retry-" + project.id());
            Button dismiss = Ui.secondary("Dismiss failed selection", () -> { retryUploads.remove(project.id()); refresh(); });
            card.getChildren().add(Ui.card(Ui.text(failureMessage(retry)), retryButton, dismiss));
        }
        Label fileCount = Ui.muted(files.size() + " supporting file(s)");
        fileCount.getStyleClass().add("work-result-count");
        card.getChildren().add(fileCount);
        files.forEach(file -> card.getChildren().add(fileCard(file)));
        return card;
    }

    private void addFiles(AppData.Project project) {
        List<Path> paths = pickFiles(); if (paths.isEmpty()) return;
        UploadResult previous = retryUploads.get(project.id());
        if (previous != null) {
            List<Path> combined = new ArrayList<>(previous.failed());
            for (Path path : paths) if (!combined.contains(path)) combined.add(path);
            paths = List.copyOf(combined);
        }
        uploadToProject(project, paths);
    }

    private void uploadToProject(AppData.Project project, List<Path> paths) {
        AppData.User actor = App.user(); Ui.feedback(feedback, "Uploading supporting files…", true);
        Background.run(view, () -> upload(project, paths, actor, feedback), result -> {
            if (result.failed().isEmpty()) { retryUploads.remove(project.id()); Ui.feedback(feedback, result.saved() + " supporting file(s) added.", true); }
            else {
                retryUploads.put(project.id(), result); Ui.feedback(feedback, failureMessage(result), false);
            }
            refresh();
        }, error -> Ui.feedback(feedback, error.getMessage(), false));
    }

    private void removeProject(AppData.Project project) {
        if (!Ui.confirm("Delete “" + project.title() + "” and all its supporting files? This cannot be undone.")) return;
        Background.run(view, () -> { App.data().deleteProject(project); return true; }, ignored -> {
            if (editing != null && editing.id().equals(project.id())) clearEditor(); retryUploads.remove(project.id()); refresh();
            Ui.feedback(feedback, "Portfolio entry deleted.", true);
        }, error -> {
            if (App.data().projects().stream().noneMatch(p -> p.id().equals(project.id()))) {
                if (editing != null && editing.id().equals(project.id())) clearEditor(); retryUploads.remove(project.id());
            }
            refresh(); Ui.feedback(feedback, error.getMessage(), false);
        });
    }

    private VBox fileCard(AppData.PortfolioItem item) {
        TextField rename = new TextField(item.title()); rename.setId("portfolio-file-title-" + item.id()); rename.setAccessibleText("Document title");
        Label status = Ui.muted(""); status.setVisible(false); status.setManaged(false);
        VBox editor = new VBox(8); editor.setVisible(false); editor.setManaged(false);
        Button saveName = Ui.button("Save title", () -> {
            String value = rename.getText();
            Background.run(view, () -> App.data().updatePortfolio(item, value), updated -> refresh(), error -> Ui.feedback(status, error.getMessage(), false));
        });
        editor.getChildren().addAll(rename, saveName, status);
        Button open = Ui.secondary("Open", () -> openDocument(item)); open.setId("portfolio-open-" + item.id());
        Button renameButton = Ui.secondary("Rename", () -> { editor.setVisible(!editor.isVisible()); editor.setManaged(editor.isVisible()); });
        renameButton.setId("portfolio-rename-" + item.id());
        Button remove = Ui.secondary("Remove", () -> {
            if (!Ui.confirm("Permanently remove “" + item.title() + "” from your portfolio?")) return;
            Background.run(view, () -> { App.data().removePortfolio(item); return true; }, ignored -> {
                refresh(); Ui.feedback(feedback, "Document removed.", true);
            }, error -> { refresh(); Ui.feedback(feedback, error.getMessage(), false); });
        });
        remove.setId("portfolio-remove-" + item.id()); remove.getStyleClass().add("danger-button");
        VBox card = Ui.card(PageComponents.section("noc", item.title(), item.fileName() + " · " + Ui.date(item.addedAt())),
            Ui.grid(3, 90, open, renameButton, remove), editor);
        card.getStyleClass().add("work-file-card");
        return card;
    }

    private static VBox field(String title, Control control) {
        Label label = Ui.text(title); label.setLabelFor(control); label.getStyleClass().add("work-field-label");
        control.setMinWidth(0);
        VBox field = new VBox(8, label, control); field.setMinWidth(0); return field;
    }

    private void openDocument(AppData.PortfolioItem item) {
        Background.run(view, () -> App.data().downloadPortfolio(item), App::openFile, error -> Ui.error(error.getMessage()));
    }

    public VBox getView() { return view; }
}
