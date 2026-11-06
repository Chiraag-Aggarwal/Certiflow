package com.certiflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Export the real saved request data rather than displaying a pretend download. */
public class TeacherReportExportView {
    private final VBox view;
    private final ComboBox<String> range = new ComboBox<>();
    private final Label period = Ui.muted("");
    private final Label summary = Ui.text("");
    private final Label result = Ui.muted("");
    private final GridPane totals = Ui.grid(3, 100);
    private final VBox sample = new VBox(10);
    private final Button export;

    public TeacherReportExportView() {
        range.getItems().addAll("This week", "All records");
        range.setValue("This week");
        range.setId("report-range");
        range.setMaxWidth(Double.MAX_VALUE);
        export = Ui.button("Save CSV report", this::exportReport);
        export.setId("report-save");
        range.valueProperty().addListener((observable, before, after) -> refreshPreview());
        VBox settings = Ui.card(PageComponents.section("export", "Build your report",
                "Choose a period, review the records, then save a spreadsheet-ready CSV."),
                PageComponents.tag("01 · CHOOSE A PERIOD", "review-caption"),
                Ui.text("Report period"), range,
                Ui.muted("Includes requests submitted or decided in the selected period, with their current status and decision notes."),
                PageComponents.tag("WHAT'S INCLUDED", "review-caption"),
                Ui.text("Student and application references\nApplication type, reason and status\nSubmission and decision dates\nDecision notes"),
                Ui.muted("Only records available to your account are exported. Private remarks and conversations are not included."));
        settings.setId("report-settings-panel");
        settings.getStyleClass().add("report-settings");
        VBox preview = Ui.card(PageComponents.section("history", "Ready to export", "Review your selection before choosing where to save it."),
                PageComponents.tag("02 · REVIEW & SAVE", "review-caption"), period, summary, totals, sample, export, result);
        preview.setId("report-preview-panel");
        preview.getStyleClass().add("report-preview");
        summary.getStyleClass().add("report-summary");
        sample.setMinWidth(0);
        view = Ui.page("Export report", "Export",
                Ui.columns(settings, preview));
        refreshPreview();
    }

    private List<AppData.NocRequest> selectedRequests() {
        List<AppData.NocRequest> requests = App.data().requests();
        return "All records".equals(range.getValue()) ? requests
                : ReportExporter.requestsForWeek(requests, LocalDate.now());
    }

    private void refreshPreview() {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DateTimeFormatter format = DateTimeFormatter.ofPattern("d MMM uuuu");
        period.setText("All records".equals(range.getValue()) ? "All saved applications"
                : start.format(format) + " – " + start.plusDays(6).format(format) + " (Monday–Sunday)");
        List<AppData.NocRequest> requests = selectedRequests();
        long pending = requests.stream().filter(r -> r.status() == AppData.Status.PENDING).count();
        long approved = requests.stream().filter(r -> r.status() == AppData.Status.APPROVED).count();
        long rejected = requests.stream().filter(r -> r.status() == AppData.Status.REJECTED).count();
        summary.setText(requests.size() + " requests · " + pending + " pending · " + approved
                + " approved · " + rejected + " rejected");
        totals.getChildren().setAll(PageComponents.tag(pending + " pending", "review-warn"),
                PageComponents.tag(approved + " approved", "review-good"),
                PageComponents.tag(rejected + " rejected", "review-bad"));
        sample.getChildren().clear();
        // This is a preview of the selected server records, never fabricated example rows.
        if (requests.isEmpty()) {
            sample.getChildren().add(WorkspaceCards.empty("No records in this period",
                    "Choose All records to check a wider period, or save a file with column headings only.", "export"));
        } else {
            sample.getChildren().add(Ui.muted("Preview · first " + Math.min(3, requests.size()) + " selected records"));
            requests.stream().limit(3).forEach(request -> {
                VBox row = Ui.card(Ui.text(request.studentName()), Ui.muted(request.type()), Ui.status(request.status()));
                row.getStyleClass().add("report-preview-row");
                sample.getChildren().add(row);
            });
        }
        result.setText(requests.isEmpty() ? "This period has no requests. The CSV will contain column headings only." : "");
    }

    private void exportReport() {
        if (App.user() == null || !App.user().role().isStaff()) {
            Ui.error("Only a teacher or administrator can export request reports.");
            return;
        }
        export.setDisable(true);
        try {
            Path reports = App.data().dataDirectory().resolveSibling("reports");
            Files.createDirectories(reports);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Certiflow CSV report");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV report (*.csv)", "*.csv"));
            chooser.setInitialDirectory(reports.toFile());
            chooser.setInitialFileName("certiflow-report-" + LocalDate.now() + ".csv");
            File chosen = chooser.showSaveDialog(view.getScene().getWindow());
            if (chosen == null) return;
            List<AppData.NocRequest> requests = selectedRequests();
            ReportExporter.writeCsv(chosen.toPath(), requests);
            result.setText("Saved " + requests.size() + " requests to:\n" + chosen.getAbsolutePath());
            Ui.toast("CSV report saved. " + requests.size() + " requests exported.");
        } catch (IOException | RuntimeException ex) {
            Ui.error("The report could not be saved. " + ex.getMessage());
        } finally {
            export.setDisable(false);
        }
    }

    public VBox getView() { return view; }
}
