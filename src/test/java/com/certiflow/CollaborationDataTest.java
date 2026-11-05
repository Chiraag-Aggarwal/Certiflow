package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** HTTP contract checks; database authorization is checked by verify_collaboration.sql. */
class CollaborationDataTest {
    @TempDir Path directory;
    private TestBackend backend;
    private AppData data;

    @BeforeEach void setup() throws Exception { backend = new TestBackend(); data = new AppData(directory, backend.client()); }
    @AfterEach void cleanup() { backend.close(); }
    private AppData.User login() throws Exception {
        return data.authenticate(backend.role, "account0@example.invalid", "long-password-test").orElseThrow();
    }

    @Test void expandedApplicationTypesPersistRealStaffRecipientWithoutClientIdentityFields() throws Exception {
        var user = login();
        assertTrue(AppData.REQUEST_TYPES.containsAll(List.of("Scholarship", "Hostel Clearance", "Transcript", "Exam Permission", "Research Project")));
        var request = data.submit(user, "Scholarship", "  Scholarship clearance documents are ready.  ", TestBackend.TEACHER_ID);
        assertEquals(TestBackend.TEACHER_ID, request.recipientId());
        assertEquals("Scholarship clearance documents are ready.", request.reason());
        assertEquals(Set.of("type", "reason", "recipient_id"), backend.lastBody.keySet());
        assertEquals(user.id(), request.studentId());
        assertEquals(AppData.Status.PENDING, request.status());
    }

    @Test void unknownApplicationAndPortfolioRecipientsAreRejectedBeforeSubmission() throws Exception {
        var user = login();
        backend.calls.clear();
        String unknown = "00000000-0000-4000-8000-000000000777";
        assertThrows(IllegalArgumentException.class, () -> data.submit(user, "Scholarship", "Request reason", unknown));
        assertThrows(IllegalArgumentException.class, () -> data.createProject(user, "Project", "Project", "", "", unknown));
        assertTrue(backend.calls.isEmpty());
    }

    @Test void portfolioEntryCanGroupMultipleFilesAndEditDescriptionsAndAttachmentNames() throws Exception {
        var user = login();
        var project = data.createProject(user, "  Science project  ", "Project", "Research and findings", "https://example.invalid/project", TestBackend.TEACHER_ID);
        assertEquals(Set.of("title", "kind", "description", "link", "recipient_id"), backend.lastBody.keySet());
        Path report = directory.resolve("report.txt"), notes = directory.resolve("notes.txt");
        Files.writeString(report, "Research report"); Files.writeString(notes, "Research notes");
        var first = data.addPortfolio(user, "Report", report, project.id());
        var second = data.addPortfolio(user, "Notes", notes, project.id());
        assertEquals(project.id(), first.projectId()); assertEquals(project.id(), second.projectId());
        assertNotEquals(first.storedName(), second.storedName());
        assertEquals(2, data.portfoliosFor(user.id()).size());

        var edited = data.updateProject(project, "Final research", "Achievement", "Updated findings", "https://example.invalid/final", TestBackend.TEACHER_ID);
        assertEquals(project.id(), edited.id()); assertEquals("Achievement", edited.kind());
        assertEquals("Updated findings", edited.description());
        var renamed = data.updatePortfolio(first, "Final report");
        assertEquals(Set.of("title"), backend.lastBody.keySet());
        assertEquals("Final report", renamed.title()); assertEquals(first.storedName(), renamed.storedName());

        data.refresh();
        assertEquals(1, data.projectsFor(user.id()).size());
        assertEquals("Final research", data.projects().get(0).title());
        assertEquals(2, data.portfolios().size());
        assertTrue(data.portfolios().stream().allMatch(item -> item.projectId().equals(project.id())));
    }

    @Test void clearingPortfolioRecipientSendsExplicitNullAndMakesEntryUnassigned() throws Exception {
        var user = login();
        var project = data.createProject(user, "Shared work", "Project", "", "", TestBackend.TEACHER_ID);
        var updated = data.updateProject(project, project.title(), project.kind(), "", "", "");
        assertTrue(backend.lastBody.has("recipient_id"), "PATCH must include recipient_id to revoke the previous assignment");
        assertTrue(backend.lastBody.get("recipient_id").isJsonNull());
        assertEquals("", updated.recipientId());
        data.refresh(); assertEquals("", data.projects().get(0).recipientId());
    }

    @Test void removingFilesAndEntriesDeletesMetadataBeforeCleaningUpTheirStorageObjects() throws Exception {
        var user = login();
        var project = data.createProject(user, "Documents", "Document", "", "", "");
        Path file = directory.resolve("document.txt"); Files.writeString(file, "Document content");
        var first = data.addPortfolio(user, "First", file, project.id());
        var second = data.addPortfolio(user, "Second", file, project.id());
        backend.calls.clear();
        data.removePortfolio(first);
        assertEquals(1, data.portfolios().size());
        assertTrue(backend.calls.get(0).startsWith("DELETE /rest/v1/portfolios?id=eq." + first.id()));
        assertEquals("DELETE /storage/v1/object/portfolios", backend.calls.get(1));
        assertEquals(first.storedName(), backend.lastBody.getAsJsonArray("prefixes").get(0).getAsString());

        backend.calls.clear();
        data.deleteProject(project);
        assertTrue(data.projects().isEmpty()); assertTrue(data.portfolios().isEmpty());
        assertTrue(backend.projects.isEmpty()); assertTrue(backend.portfolios.isEmpty());
        assertTrue(backend.calls.get(0).startsWith("DELETE /rest/v1/portfolio_projects?id=eq." + project.id()));
        assertEquals("DELETE /storage/v1/object/portfolios", backend.calls.get(1));
        assertEquals(second.storedName(), backend.lastBody.getAsJsonArray("prefixes").get(0).getAsString());
    }

    @Test void invalidLinksAndOversizedProfileOrProjectFieldsNeverReachServer() throws Exception {
        var user = login(); backend.calls.clear();
        for (String link : List.of("javascript:alert(1)", "file:///private.txt", "https://user:secret@example.invalid", "not a link")) {
            assertThrows(IllegalArgumentException.class, () -> data.createProject(user, "Work", "Project", "", link, ""));
            assertThrows(IllegalArgumentException.class, () -> data.updateProfile("Name", "Science", "", "", "", "", link));
        }
        assertThrows(IllegalArgumentException.class, () -> data.createProject(user, "Work", "Project", "x".repeat(4001), "", ""));
        assertThrows(IllegalArgumentException.class, () -> data.updateProfile("Name", "Science", "", "x".repeat(1001), "", "", ""));
        assertTrue(backend.calls.isEmpty());
    }

    @Test void expandedProfileEditsKeepEmailRoleAndVerificationServerControlled() throws Exception {
        login();
        var result = data.updateProfile("Updated Name", "Science", "ROLL-17", "Research interests", "Writing, Java", "Computer Science", "https://example.invalid/");
        assertEquals(Set.of("full_name", "department", "student_number", "bio", "skills", "programme", "website"), backend.lastBody.keySet());
        assertEquals("ROLL-17", result.studentNumber()); assertEquals("Research interests", result.bio());
        assertEquals("Writing, Java", result.skills()); assertEquals("Computer Science", result.programme());
        assertEquals("https://example.invalid/", result.website());
        assertEquals("account0@example.invalid", result.email()); assertEquals(AppData.Role.STUDENT, result.role());
        assertFalse(result.identityVerified()); assertEquals(result, data.currentUser());
    }

    @Test void onlyAdministratorCanRequestRoleOrIdentityChangesAndCannotCreateAnotherAdmin() throws Exception {
        login(); backend.calls.clear();
        assertThrows(IllegalArgumentException.class, () -> data.setAccountRole(TestBackend.TEACHER_ID, AppData.Role.TEACHER));
        assertThrows(IllegalArgumentException.class, () -> data.verifyIdentity(TestBackend.TEACHER_ID));
        assertTrue(backend.calls.isEmpty());

        data.logout(); backend.role = AppData.Role.ADMIN; login(); backend.calls.clear();
        assertThrows(IllegalArgumentException.class, () -> data.setAccountRole(TestBackend.TEACHER_ID, AppData.Role.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> data.setAccountRole(TestBackend.ID, AppData.Role.STUDENT));
        assertTrue(backend.calls.isEmpty());
        var teacher = data.setAccountRole(TestBackend.TEACHER_ID, AppData.Role.TEACHER);
        assertEquals(AppData.Role.TEACHER, teacher.role()); assertEquals(TestBackend.TEACHER_ID, teacher.id());
        assertEquals(Set.of("target_id", "new_role"), backend.lastBody.keySet());
        var verified = data.verifyIdentity(TestBackend.TEACHER_ID);
        assertTrue(verified.identityVerified()); assertEquals(Set.of("target_id"), backend.lastBody.keySet());
    }

    @Test void messagesUseServerSenderAndSupportConversationOrderUnreadCountAndReadReceipts() throws Exception {
        login();
        JsonObject incoming = new JsonObject();
        incoming.addProperty("id", "00000000-0000-4000-8000-000000000555");
        incoming.addProperty("sender_id", TestBackend.TEACHER_ID); incoming.addProperty("recipient_id", TestBackend.ID);
        incoming.addProperty("body", "Please attach the report."); incoming.addProperty("sent_at", "2026-11-05T06:29:00Z");
        incoming.add("read_at", JsonNull.INSTANCE); backend.messages.add(incoming);
        var sent = data.sendMessage(TestBackend.TEACHER_ID, "  I have attached it.  ");
        assertEquals(Set.of("recipient_id", "body"), backend.lastBody.keySet());
        assertEquals(TestBackend.ID, sent.senderId()); assertEquals("I have attached it.", sent.body());
        assertEquals(1, data.unreadMessageCount());
        var conversation = data.conversation(TestBackend.TEACHER_ID);
        assertEquals(List.of("Please attach the report.", "I have attached it."), conversation.stream().map(AppData.ChatMessage::body).toList());
        assertTrue(backend.calls.stream().anyMatch(call -> call.contains("/messages?select=*&or=(and(sender_id.eq." + TestBackend.ID)));
        data.markMessagesRead(TestBackend.TEACHER_ID);
        assertEquals(Set.of("other_id"), backend.lastBody.keySet()); assertEquals(0, data.unreadMessageCount());
        assertNotNull(data.conversation(TestBackend.TEACHER_ID).get(0).readAt());
    }

    @Test void invalidMessageTargetsAndBodiesAreRejectedAndLogoutClearsNewCaches() throws Exception {
        var user = login();
        data.createProject(user, "My work", "Project", "", "", "");
        assertFalse(data.projects().isEmpty()); assertFalse(data.contacts().isEmpty()); backend.calls.clear();
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage(TestBackend.ID, "Self message"));
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage("00000000-0000-4000-8000-000000000777", "Unknown recipient"));
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage(TestBackend.TEACHER_ID, " \t\n "));
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage(TestBackend.TEACHER_ID, "x".repeat(4001)));
        assertTrue(backend.calls.isEmpty());
        data.logout(); assertTrue(data.projects().isEmpty()); assertTrue(data.contacts().isEmpty());
        assertThrows(IllegalStateException.class, () -> data.conversation(TestBackend.TEACHER_ID));
    }
}
