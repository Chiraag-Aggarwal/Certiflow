package com.certiflow;

import static com.certiflow.AppData.Role.*;
import static com.certiflow.AppData.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppDataTest {
    @TempDir Path temporary;
    private AppData data;
    private AppData.User student;
    private AppData.User teacher;
    private AppData.User admin;

    @BeforeEach
    void setUp() throws IOException {
        data = new AppData(temporary.resolve("data"));
        student = data.authenticate(STUDENT, "student", "student123").orElseThrow();
        teacher = data.authenticate(TEACHER, "teacher", "teacher123").orElseThrow();
        admin = data.authenticate(ADMIN, "admin", "admin123").orElseThrow();
    }

    @Test
    void loginRequiresTheCorrectAccountPasswordAndRole() {
        assertEquals(student, data.authenticate(STUDENT, " STUDENT ", "student123").orElseThrow());
        assertTrue(data.authenticate(STUDENT, "student", "wrong").isEmpty());
        assertTrue(data.authenticate(STUDENT, "student", "student123 ").isEmpty());
        assertTrue(data.authenticate(ADMIN, "student", "student123").isEmpty());
        assertTrue(data.authenticate(TEACHER, "admin", "admin123").isEmpty());
        assertTrue(data.authenticate(STUDENT, "mark", "student123").isEmpty());
        assertTrue(data.authenticate(null, "student", "student123").isEmpty());
        assertTrue(data.authenticate(STUDENT, null, null).isEmpty());
    }

    @Test
    void validatedRequestIsVisibleOnlyForItsStudentAndSurvivesRestart() throws IOException {
        String reason = "Certificate for internship, research & development\nSecond line: café / 学生";
        AppData.NocRequest request = data.submit(student, " Internship ", "  " + reason + "  ");
        assertEquals("Internship", request.type());
        assertEquals(reason, request.reason());
        assertEquals(PENDING, request.status());
        assertNull(request.decidedAt());
        assertTrue(data.requestsFor(student.id()).contains(request));
        assertFalse(data.requestsFor("STU002").contains(request));
        assertEquals(request, data.requests().get(0));
        AppData reloaded = new AppData(data.dataDirectory());
        assertEquals(data.requests(), reloaded.requests());
        assertEquals(4, reloaded.requests().size(), "Restart must not reseed sample requests");
    }

    @Test
    void emptyDuplicateOrUnauthorizedRequestsDoNotChangeData() {
        List<AppData.NocRequest> before = data.requests();
        assertThrows(IllegalArgumentException.class, () -> data.submit(student, " ", "reason"));
        assertThrows(IllegalArgumentException.class, () -> data.submit(student, null, "reason"));
        assertThrows(IllegalArgumentException.class, () -> data.submit(student, "Unknown request type", "reason"));
        assertThrows(IllegalArgumentException.class, () -> data.submit(student, "Internship", " "));
        assertThrows(IllegalArgumentException.class, () -> data.submit(student, "Internship", "x".repeat(2001)));
        assertThrows(IllegalArgumentException.class, () -> data.submit(student, " library clearance ", "Duplicate"));
        assertThrows(IllegalArgumentException.class, () -> data.submit(teacher, "Internship", "reason"));
        AppData.User forged = new AppData.User("STU001", "student", "Someone else", STUDENT, "", "");
        assertThrows(IllegalArgumentException.class, () -> data.submit(forged, "Internship", "reason"));
        assertEquals(before, data.requests());
    }

    @Test
    void approvalUpdatesTheSameRequestAndAllowsLaterResubmission() throws IOException {
        AppData.NocRequest original = data.requestsFor(student.id()).get(0);
        data.decide(teacher, original.id(), APPROVED, "All books returned");
        AppData.NocRequest approved = data.requestsFor(student.id()).get(0);
        assertEquals(original.id(), approved.id());
        assertEquals(original.submittedAt(), approved.submittedAt());
        assertEquals(APPROVED, approved.status());
        assertNotNull(approved.decidedAt());
        assertEquals("All books returned", approved.decisionNote());
        assertThrows(IllegalArgumentException.class, () -> data.decide(admin, original.id(), REJECTED, "Changed my mind"));
        AppData.NocRequest later = data.submit(student, original.type(), "A later request for another event");
        assertNotEquals(original.id(), later.id());
        assertEquals(2, data.requestsFor(student.id()).size());
        assertEquals(data.requests(), new AppData(data.dataDirectory()).requests());
    }

    @Test
    void rejectionRequiresReasonAndAnAuthorizedActor() throws IOException {
        String requestId = data.requestsFor(student.id()).get(0).id();
        assertThrows(IllegalArgumentException.class, () -> data.decide(student, requestId, APPROVED, ""));
        assertThrows(IllegalArgumentException.class, () -> data.decide(teacher, requestId, PENDING, ""));
        assertThrows(IllegalArgumentException.class, () -> data.decide(teacher, requestId, REJECTED, " "));
        assertThrows(IllegalArgumentException.class, () -> data.decide(admin, "missing", APPROVED, ""));
        data.decide(admin, requestId, REJECTED, "  Return the outstanding library book.  ");
        AppData.NocRequest rejected = new AppData(data.dataDirectory()).requestsFor(student.id()).get(0);
        assertEquals(REJECTED, rejected.status());
        assertEquals("Return the outstanding library book.", rejected.decisionNote());
    }

    @Test
    void failedSaveDoesNotCommitSubmitOrDecisionInMemory() throws IOException {
        List<AppData.NocRequest> before = data.requests();
        Path state = data.dataDirectory().resolve("state.xml");
        Path backup = temporary.resolve("saved-state.xml");
        Files.move(state, backup);
        Files.createDirectory(state);
        Files.writeString(state.resolve("blocker.txt"), "Force an atomic replacement failure");
        assertThrows(IOException.class, () -> data.submit(student, "Internship", "Research internship"));
        assertThrows(IOException.class, () -> data.decide(teacher, before.get(0).id(), APPROVED, ""));
        assertEquals(before, data.requests());
        Files.delete(state.resolve("blocker.txt"));
        Files.delete(state);
        Files.move(backup, state);
        assertEquals(before, new AppData(data.dataDirectory()).requests());
    }

    @Test
    void corruptDataIsReportedWithoutResettingOrOverwritingTheFile() throws IOException {
        Path state = data.dataDirectory().resolve("state.xml");
        String broken = "<not-valid-xml> my saved work";
        Files.writeString(state, broken);
        IOException error = assertThrows(IOException.class, () -> new AppData(data.dataDirectory()));
        assertTrue(error.getMessage().contains("has not been reset"));
        assertEquals(broken, Files.readString(state));
    }

    @Test
    void incompleteDuplicateOrInconsistentRecordsAreReportedWithoutOverwriting() throws IOException {
        Path state = data.dataDirectory().resolve("state.xml");
        byte[] original = Files.readAllBytes(state);
        List<Consumer<Properties>> corruptions = List.of(
            properties -> properties.remove("request.0.reason"),
            properties -> properties.setProperty("request.1.id", properties.getProperty("request.0.id")),
            properties -> properties.setProperty("request.0.status", "APPROVED"),
            properties -> properties.setProperty("request.0.studentName", "Someone else"),
            properties -> properties.setProperty("requests.count", "-1"),
            properties -> properties.remove("version")
        );
        for (Consumer<Properties> corruption : corruptions) {
            Properties properties = new Properties();
            properties.loadFromXML(new ByteArrayInputStream(original));
            corruption.accept(properties);
            try (var output = Files.newOutputStream(state)) { properties.storeToXML(output, "invalid test state", "UTF-8"); }
            byte[] broken = Files.readAllBytes(state);
            assertThrows(IOException.class, () -> new AppData(data.dataDirectory()));
            assertArrayEquals(broken, Files.readAllBytes(state));
        }
    }

    @Test
    void missingStateWithExistingDataIsNotTreatedAsFirstLaunch() throws IOException {
        Path existing = temporary.resolve("existing-data");
        Files.createDirectories(existing.resolve("documents"));
        Files.writeString(existing.resolve("documents").resolve("my-certificate.txt"), "certificate");
        assertThrows(IOException.class, () -> new AppData(existing));
        assertFalse(Files.exists(existing.resolve("state.xml")));
    }

    @Test
    void portfolioImportsAnIndependentCopyAndRetainsItAcrossRestart() throws IOException {
        Path source = temporary.resolve("Summer certificate.txt");
        Files.writeString(source, "Certificate of internship\nSarah Jenkins");
        AppData.PortfolioItem item = data.addPortfolio(student, " Summer internship ", source);
        assertEquals("Summer internship", item.title());
        assertEquals("Summer certificate.txt", item.fileName());
        assertTrue(data.portfolioPath(item).startsWith(data.dataDirectory().resolve("documents")));
        assertEquals(Files.readString(source), Files.readString(data.portfolioPath(item)));
        Files.delete(source);
        AppData reloaded = new AppData(data.dataDirectory());
        assertEquals(List.of(item), reloaded.portfoliosFor(student.id()));
        assertTrue(reloaded.portfoliosFor("STU002").isEmpty());
        assertTrue(Files.exists(reloaded.portfolioPath(item)));
    }

    @Test
    void portfolioRejectsMissingEmptyOversizedAndUnauthorizedFiles() throws IOException {
        Path empty = temporary.resolve("empty.txt");
        Files.createFile(empty);
        Path large = temporary.resolve("large.pdf");
        try (RandomAccessFile file = new RandomAccessFile(large.toFile(), "rw")) {
            file.setLength(25L * 1024 * 1024 + 1);
        }
        assertThrows(IllegalArgumentException.class, () -> data.addPortfolio(student, "Document", empty));
        assertThrows(IllegalArgumentException.class, () -> data.addPortfolio(student, "Document", large));
        assertThrows(IllegalArgumentException.class, () -> data.addPortfolio(student, "Document", temporary));
        assertThrows(IllegalArgumentException.class, () -> data.addPortfolio(teacher, "Document", large));
        assertThrows(IllegalArgumentException.class, () -> data.addPortfolio(student, "Document", temporary.resolve("missing.txt")));
        assertTrue(data.portfoliosFor(student.id()).isEmpty());
    }

    @Test
    void failedPortfolioSaveCleansUpTheCopiedFile() throws IOException {
        Path source = temporary.resolve("certificate.txt");
        Files.writeString(source, "certificate");
        Path state = data.dataDirectory().resolve("state.xml");
        Files.move(state, temporary.resolve("saved-state.xml"));
        Files.createDirectory(state);
        Files.writeString(state.resolve("blocker.txt"), "block replacement");
        assertThrows(IOException.class, () -> data.addPortfolio(student, "Certificate", source));
        assertTrue(data.portfoliosFor(student.id()).isEmpty());
        try (var documents = Files.list(data.dataDirectory().resolve("documents"))) {
            assertEquals(0, documents.count());
        }
    }

    @Test
    void collectionsCannotBypassPersistenceAndStoredNamesCannotEscapeDataFolder() {
        assertThrows(UnsupportedOperationException.class, () -> data.requests().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.users().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.requestsFor(student.id()).clear());
        AppData.PortfolioItem traversal = new AppData.PortfolioItem("bad", student.id(), "bad", "bad", "../../outside.txt", java.time.LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> data.portfolioPath(traversal));
    }
}
