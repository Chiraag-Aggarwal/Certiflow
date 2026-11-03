package com.certiflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

/** CSV export without UI dependencies. A failed write does not truncate an existing report. */
public final class ReportExporter {
    private ReportExporter() {}

    public static List<AppData.NocRequest> requestsForWeek(List<AppData.NocRequest> requests,
                                                         LocalDate dayInWeek) {
        LocalDate first = dayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate afterLast = first.plusWeeks(1);
        return requests.stream().filter(request ->
                within(request.submittedAt().toLocalDate(), first, afterLast)
                || (request.decidedAt() != null
                    && within(request.decidedAt().toLocalDate(), first, afterLast))).toList();
    }

    private static boolean within(LocalDate value, LocalDate first, LocalDate afterLast) {
        return !value.isBefore(first) && value.isBefore(afterLast);
    }

    public static void writeCsv(Path target, List<AppData.NocRequest> requests) throws IOException {
        Objects.requireNonNull(target, "Choose a report file.");
        Objects.requireNonNull(requests, "Requests are required.");
        // Build the contents first, so invalid inputs cannot damage an existing export.
        StringBuilder csv = new StringBuilder("\uFEFFid,studentId,studentName,type,reason,status,submittedAt,decidedAt,decisionNote\r\n");
        for (AppData.NocRequest request : requests) {
            Objects.requireNonNull(request, "A report cannot contain a missing request.");
            String[] fields = { request.id(), request.studentId(), request.studentName(), request.type(),
                    request.reason(), request.status().name(), request.submittedAt().toString(),
                    request.decidedAt() == null ? "" : request.decidedAt().toString(), request.decisionNote() };
            for (int index = 0; index < fields.length; index++) {
                if (index > 0) csv.append(',');
                csv.append(csvCell(fields[index]));
            }
            csv.append("\r\n");
        }

        Path destination = target.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        // The file chooser chooses an existing directory; never silently create a misspelled destination.
        Path temporary = Files.createTempFile(parent, ".certiflow-report-", ".tmp");
        try {
            Files.writeString(temporary, csv, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String csvCell(String value) {
        Objects.requireNonNull(value, "Report fields must not be null.");
        int firstContent = 0;
        while (firstContent < value.length()
                && (Character.isWhitespace(value.charAt(firstContent))
                    || Character.isSpaceChar(value.charAt(firstContent)))) firstContent++;
        boolean formula = firstContent < value.length()
                && "=+-@".indexOf(value.charAt(firstContent)) >= 0;
        boolean leadingControl = !value.isEmpty()
                && (value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n');
        // Quoting alone does not stop a spreadsheet from executing a formula.
        if (formula || leadingControl) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
