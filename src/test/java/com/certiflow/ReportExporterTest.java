package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportExporterTest {
    @TempDir Path directory;

    @Test void csvPreservesUnicodeCommasQuotesAndMultilineText() throws Exception {
        AppData.NocRequest request = request("NOC-1", "Jos\u00e9, \"JJ\"", "First line\nSecond, \"quoted\" line",
                AppData.Status.PENDING, LocalDateTime.of(2026, 11, 3, 12, 30), null, "");
        Path target = directory.resolve("report.csv");
        ReportExporter.writeCsv(target, List.of(request));
        String csv = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFid,studentId,studentName,type,reason,status,submittedAt,decidedAt,decisionNote\r\n"));
        assertTrue(csv.contains("\"Jos\u00e9, \"\"JJ\"\"\""));
        assertTrue(csv.contains("\"First line\nSecond, \"\"quoted\"\" line\""));
        assertTrue(csv.endsWith("\"PENDING\",\"2026-11-03T12:30\",\"\",\"\"\r\n"));
    }

    @Test void neutralizesFormulaPrefixesIncludingWhitespaceAndTabs() throws Exception {
        for (String input : List.of("=1+1", "+SUM(A1:A2)", "-2+3", "@SUM(1)", "  =1+1", "\tvalue", "\rvalue", "\n=1")) {
            assertTrue(ReportExporter.csvCell(input).startsWith("\"'"), "Unsafe cell: " + input);
        }
        Path target = directory.resolve("safe.csv");
        AppData.NocRequest request = new AppData.NocRequest("=ID", "+STUDENT", "@NAME", "-TYPE", "\tREASON",
                AppData.Status.APPROVED, LocalDateTime.of(2026, 11, 3, 10, 0),
                LocalDateTime.of(2026, 11, 3, 11, 0), "=NOTE");
        ReportExporter.writeCsv(target, List.of(request));
        String csv = Files.readString(target);
        for (String field : List.of("=ID", "+STUDENT", "@NAME", "-TYPE", "\tREASON", "=NOTE")) {
            assertTrue(csv.contains("\"'" + field + "\""));
        }
        assertEquals("\"ordinary value\"", ReportExporter.csvCell("ordinary value"));
    }

    @Test void emptyExportHasHeadingsAndReplacesExistingReportWithoutTrailingOldRows() throws Exception {
        Path target = directory.resolve("empty.csv");
        Files.writeString(target, "old report data that should not remain");
        ReportExporter.writeCsv(target, List.of());
        assertEquals("\uFEFFid,studentId,studentName,type,reason,status,submittedAt,decidedAt,decisionNote\r\n",
                Files.readString(target));
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }

    @Test void invalidInputDoesNotTruncateExistingReport() throws Exception {
        Path target = directory.resolve("existing.csv");
        Files.writeString(target, "keep this report");
        List<AppData.NocRequest> invalid = new ArrayList<>();
        invalid.add(null);
        assertThrows(NullPointerException.class, () -> ReportExporter.writeCsv(target, invalid));
        assertEquals("keep this report", Files.readString(target));
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }

    @Test void failedReplacementLeavesExistingDirectoryAndNoTemporaryFile() throws Exception {
        Path destination = Files.createDirectory(directory.resolve("occupied"));
        Files.writeString(destination.resolve("keep.txt"), "keep");
        assertThrows(java.io.IOException.class, () -> ReportExporter.writeCsv(destination, List.of()));
        assertEquals("keep", Files.readString(destination.resolve("keep.txt")));
        try (var entries = Files.list(directory)) { assertEquals(1, entries.count()); }
    }

    @Test void weeklyFilterIncludesSubmissionOrDecisionWithinCalendarWeek() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        AppData.NocRequest submitted = request("one", "A", "reason", AppData.Status.PENDING,
                monday.atStartOfDay(), null, "");
        AppData.NocRequest decided = request("two", "B", "reason", AppData.Status.APPROVED,
                monday.minusDays(20).atStartOfDay(), monday.plusDays(6).atTime(23, 59), "Done");
        AppData.NocRequest previous = request("three", "C", "reason", AppData.Status.APPROVED,
                monday.minusDays(2).atStartOfDay(), monday.minusDays(1).atTime(23, 59), "Done");
        AppData.NocRequest next = request("four", "D", "reason", AppData.Status.PENDING,
                monday.plusWeeks(1).atStartOfDay(), null, "");
        assertEquals(List.of(submitted, decided), ReportExporter.requestsForWeek(
                List.of(submitted, decided, previous, next), monday.plusDays(3)));
    }

    private static AppData.NocRequest request(String id, String name, String reason, AppData.Status status,
            LocalDateTime submitted, LocalDateTime decided, String note) {
        return new AppData.NocRequest(id, "STU001", name, "Library Clearance", reason, status, submitted, decided, note);
    }
}
