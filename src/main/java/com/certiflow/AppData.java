package com.certiflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Local demo data shared by every screen. This is not a network authentication service. */
public final class AppData {
    public enum Role { STUDENT, TEACHER, ADMIN }
    public enum Status { PENDING, APPROVED, REJECTED }
    public static final List<String> REQUEST_TYPES = List.of(
        "Library Clearance", "Laboratory Access", "Sports Equipment", "Internship", "Other");

    public record User(String id, String username, String name, Role role, String department, String email) {}
    public record NocRequest(String id, String studentId, String studentName, String type, String reason,
                             Status status, LocalDateTime submittedAt, LocalDateTime decidedAt,
                             String decisionNote) {}
    public record PortfolioItem(String id, String studentId, String title, String fileName,
                                String storedName, LocalDateTime addedAt) {}

    private static final long MAX_DOCUMENT_BYTES = 25L * 1024 * 1024;
    private static final List<User> USERS = List.of(
        new User("STU001", "student", "Sarah Jenkins", Role.STUDENT, "Computer Science", "sarah@example.test"),
        new User("STU002", "mark", "Mark Thompson", Role.STUDENT, "Engineering", "mark@example.test"),
        new User("STU003", "david", "David Miller", Role.STUDENT, "Sports Science", "david@example.test"),
        new User("TCH001", "teacher", "Professor Sarah", Role.TEACHER, "Computer Science", "teacher@example.test"),
        new User("ADM001", "admin", "Local Administrator", Role.ADMIN, "Administration", "admin@example.test")
    );

    private final Path directory;
    private final Path stateFile;
    private List<NocRequest> requests = List.of();
    private List<PortfolioItem> portfolios = List.of();

    public AppData(Path dataDir) throws IOException {
        directory = Objects.requireNonNull(dataDir, "Data directory is required").toAbsolutePath().normalize();
        stateFile = directory.resolve("state.xml");
        Files.createDirectories(directory);
        if (Files.exists(stateFile)) {
            load();
        } else {
            // A missing state file in an occupied data directory must not silently reset user data.
            try (var contents = Files.list(directory)) {
                if (contents.findAny().isPresent()) {
                    throw new IOException("The data folder contains files but state.xml is missing. Restore your data backup before continuing: " + directory);
                }
            }
            LocalDateTime now = LocalDateTime.now();
            List<NocRequest> initial = List.of(
                new NocRequest("NOC-8821", "STU001", "Sarah Jenkins", "Library Clearance",
                    "Please confirm that all library books have been returned.", Status.PENDING, now.minusDays(2), null, ""),
                new NocRequest("NOC-8845", "STU002", "Mark Thompson", "Laboratory Access",
                    "Requesting access to the laboratory for the final-year project.", Status.PENDING, now.minusDays(1), null, ""),
                new NocRequest("NOC-8849", "STU003", "David Miller", "Sports Equipment",
                    "Please confirm return of the department sports equipment.", Status.PENDING, now.minusHours(2), null, "")
            );
            persist(initial, List.of());
            requests = List.copyOf(initial);
        }
    }

    public Optional<User> authenticate(Role role, String username, String password) {
        if (role == null || username == null || password == null) return Optional.empty();
        String login = username.trim().toLowerCase(Locale.ROOT);
        String expected = switch (login) {
            case "student" -> "student123";
            case "teacher" -> "teacher123";
            case "admin" -> "admin123";
            default -> null;
        };
        if (expected == null || !expected.equals(password)) return Optional.empty();
        return USERS.stream().filter(user -> user.role() == role && user.username().equals(login)).findFirst();
    }

    public List<User> users() { return USERS; }

    public synchronized List<NocRequest> requests() {
        return requests.stream().sorted(Comparator.comparing(NocRequest::submittedAt).reversed()
            .thenComparing(NocRequest::id)).toList();
    }

    public synchronized List<NocRequest> requestsFor(String studentId) {
        return requests().stream().filter(request -> request.studentId().equals(studentId)).toList();
    }

    public synchronized NocRequest submit(User student, String type, String reason) throws IOException {
        requireStudent(student);
        String enteredType = required(type, "Request type", 100);
        String cleanType = REQUEST_TYPES.stream().filter(value -> value.equalsIgnoreCase(enteredType)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Choose a request type from the list."));
        String cleanReason = required(reason, "Reason", 2000);
        boolean duplicate = requests.stream().anyMatch(request -> request.studentId().equals(student.id())
            && request.status() == Status.PENDING && request.type().equalsIgnoreCase(cleanType));
        if (duplicate) throw new IllegalArgumentException("You already have a pending request of this type.");
        NocRequest request = new NocRequest(newId("NOC"), student.id(), student.name(), cleanType, cleanReason,
            Status.PENDING, LocalDateTime.now(), null, "");
        List<NocRequest> updated = new ArrayList<>(requests);
        updated.add(request);
        persist(updated, portfolios);
        requests = List.copyOf(updated);
        return request;
    }

    public synchronized void decide(User actor, String requestId, Status decision, String note) throws IOException {
        requireKnownUser(actor);
        if (actor.role() != Role.TEACHER && actor.role() != Role.ADMIN) {
            throw new IllegalArgumentException("Only a teacher or administrator can decide requests.");
        }
        if (decision != Status.APPROVED && decision != Status.REJECTED) {
            throw new IllegalArgumentException("Choose Approve or Reject.");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (decision == Status.REJECTED) cleanNote = required(cleanNote, "Rejection reason", 2000);
        if (cleanNote.length() > 2000) throw new IllegalArgumentException("Decision note must be 2,000 characters or fewer.");
        NocRequest existing = requests.stream().filter(request -> request.id().equals(requestId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("This request could not be found."));
        if (existing.status() != Status.PENDING) throw new IllegalArgumentException("This request has already been decided.");
        NocRequest decided = new NocRequest(existing.id(), existing.studentId(), existing.studentName(),
            existing.type(), existing.reason(), decision, existing.submittedAt(), LocalDateTime.now(), cleanNote);
        List<NocRequest> updated = new ArrayList<>(requests);
        updated.set(updated.indexOf(existing), decided);
        persist(updated, portfolios);
        requests = List.copyOf(updated);
    }

    public synchronized List<PortfolioItem> portfoliosFor(String studentId) {
        return portfolios.stream().filter(item -> item.studentId().equals(studentId))
            .sorted(Comparator.comparing(PortfolioItem::addedAt).reversed().thenComparing(PortfolioItem::id)).toList();
    }

    public synchronized PortfolioItem addPortfolio(User student, String title, Path source) throws IOException {
        requireStudent(student);
        String cleanTitle = required(title, "Document title", 150);
        if (source == null || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Choose a regular document file.");
        }
        long size = Files.size(source);
        if (size > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("Choose a file smaller than 25 MB.");
        if (size == 0) throw new IllegalArgumentException("The selected file is empty.");
        String originalName = source.getFileName().toString();
        String itemId = newId("DOC");
        // Keep the original extension so a copied document remains usable in its usual application.
        String extension = originalName.lastIndexOf('.') > 0 ? originalName.substring(originalName.lastIndexOf('.')) : "";
        if (!extension.matches("\\.[A-Za-z0-9]{1,12}")) extension = "";
        String storedName = itemId + extension;
        PortfolioItem item = new PortfolioItem(itemId, student.id(), cleanTitle, originalName, storedName, LocalDateTime.now());
        Path documents = directory.resolve("documents");
        Files.createDirectories(documents);
        Path destination = portfolioPath(item);
        try {
            Files.copy(source, destination);
            // Recheck after the copy in case the source grew during import.
            if (Files.size(destination) > MAX_DOCUMENT_BYTES) throw new IOException("The selected file grew beyond the 25 MB limit during import.");
            List<PortfolioItem> updated = new ArrayList<>(portfolios);
            updated.add(item);
            persist(requests, updated);
            portfolios = List.copyOf(updated);
            return item;
        } catch (IOException error) {
            try { Files.deleteIfExists(destination); } catch (IOException cleanupError) { error.addSuppressed(cleanupError); }
            throw error;
        }
    }

    public Path portfolioPath(PortfolioItem item) {
        Objects.requireNonNull(item, "Document is required");
        String name = item.storedName();
        if (name == null || !name.matches("DOC-[A-F0-9]{16}(\\.[A-Za-z0-9]{1,12})?")) {
            throw new IllegalArgumentException("Invalid stored document name.");
        }
        return directory.resolve("documents").resolve(name);
    }

    public Path dataDirectory() { return directory; }

    private static void requireKnownUser(User user) {
        if (user == null || !USERS.contains(user)) throw new IllegalArgumentException("Please sign in again.");
    }

    private static void requireStudent(User user) {
        requireKnownUser(user);
        if (user.role() != Role.STUDENT) throw new IllegalArgumentException("Only a student can submit documents or requests.");
    }

    private static String required(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        return cleaned;
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private void persist(List<NocRequest> nextRequests, List<PortfolioItem> nextPortfolios) throws IOException {
        Properties state = new Properties();
        state.setProperty("version", "1");
        state.setProperty("requests.count", Integer.toString(nextRequests.size()));
        for (int i = 0; i < nextRequests.size(); i++) {
            NocRequest request = nextRequests.get(i);
            String prefix = "request." + i + ".";
            state.setProperty(prefix + "id", request.id());
            state.setProperty(prefix + "studentId", request.studentId());
            state.setProperty(prefix + "studentName", request.studentName());
            state.setProperty(prefix + "type", request.type());
            state.setProperty(prefix + "reason", request.reason());
            state.setProperty(prefix + "status", request.status().name());
            state.setProperty(prefix + "submittedAt", request.submittedAt().toString());
            state.setProperty(prefix + "decidedAt", request.decidedAt() == null ? "" : request.decidedAt().toString());
            state.setProperty(prefix + "decisionNote", request.decisionNote());
        }
        state.setProperty("portfolios.count", Integer.toString(nextPortfolios.size()));
        for (int i = 0; i < nextPortfolios.size(); i++) {
            PortfolioItem item = nextPortfolios.get(i);
            String prefix = "portfolio." + i + ".";
            state.setProperty(prefix + "id", item.id());
            state.setProperty(prefix + "studentId", item.studentId());
            state.setProperty(prefix + "title", item.title());
            state.setProperty(prefix + "fileName", item.fileName());
            state.setProperty(prefix + "storedName", item.storedName());
            state.setProperty(prefix + "addedAt", item.addedAt().toString());
        }
        Path temporary = Files.createTempFile(directory, "state-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                state.storeToXML(out, "Certiflow local demo data", "UTF-8");
            }
            Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void load() throws IOException {
        try {
            if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS)) throw new IOException("state.xml is not a regular file.");
            Properties state = new Properties();
            try (InputStream input = Files.newInputStream(stateFile)) { state.loadFromXML(input); }
            if (!"1".equals(property(state, "version"))) throw new IllegalArgumentException("Unsupported data version");
            List<NocRequest> loadedRequests = new ArrayList<>();
            Set<String> requestIds = new HashSet<>();
            Set<String> pendingTypes = new HashSet<>();
            for (int i = 0, count = count(state, "requests.count"); i < count; i++) {
                String prefix = "request." + i + ".";
                String id = required(property(state, prefix + "id"), "Request ID", 100);
                String studentId = property(state, prefix + "studentId");
                User student = storedStudent(studentId);
                String name = property(state, prefix + "studentName");
                if (!student.name().equals(name)) throw new IllegalArgumentException("Invalid student identity");
                String type = required(property(state, prefix + "type"), "Request type", 100);
                if (!REQUEST_TYPES.contains(type)) throw new IllegalArgumentException("Unknown request type");
                String reason = required(property(state, prefix + "reason"), "Reason", 2000);
                Status status = Status.valueOf(property(state, prefix + "status"));
                LocalDateTime submitted = LocalDateTime.parse(property(state, prefix + "submittedAt"));
                String decidedValue = property(state, prefix + "decidedAt");
                LocalDateTime decided = decidedValue.isEmpty() ? null : LocalDateTime.parse(decidedValue);
                String note = property(state, prefix + "decisionNote");
                if (note.length() > 2000 || (status == Status.PENDING && (decided != null || !note.isEmpty()))
                    || (status != Status.PENDING && decided == null) || (status == Status.REJECTED && note.isBlank())) {
                    throw new IllegalArgumentException("Inconsistent request decision");
                }
                if (!requestIds.add(id)) throw new IllegalArgumentException("Duplicate request ID");
                if (status == Status.PENDING && !pendingTypes.add(studentId + "\n" + type.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate pending request");
                }
                loadedRequests.add(new NocRequest(id, studentId, name, type, reason, status, submitted, decided, note));
            }
            List<PortfolioItem> loadedPortfolios = new ArrayList<>();
            Set<String> documentIds = new HashSet<>();
            Set<String> storedNames = new HashSet<>();
            for (int i = 0, count = count(state, "portfolios.count"); i < count; i++) {
                String prefix = "portfolio." + i + ".";
                String id = property(state, prefix + "id");
                String studentId = property(state, prefix + "studentId");
                storedStudent(studentId);
                String title = required(property(state, prefix + "title"), "Document title", 150);
                String fileName = required(property(state, prefix + "fileName"), "File name", 255);
                String storedName = property(state, prefix + "storedName");
                PortfolioItem item = new PortfolioItem(id, studentId, title, fileName, storedName,
                    LocalDateTime.parse(property(state, prefix + "addedAt")));
                portfolioPath(item); // Validate persisted relative names before ever resolving them.
                if (!id.matches("DOC-[A-F0-9]{16}") || !storedName.startsWith(id)
                    || !documentIds.add(id) || !storedNames.add(storedName)) {
                    throw new IllegalArgumentException("Invalid document identity");
                }
                loadedPortfolios.add(item);
            }
            requests = List.copyOf(loadedRequests);
            portfolios = List.copyOf(loadedPortfolios);
        } catch (IOException | RuntimeException error) {
            throw new IOException("Could not read saved Certiflow data. Your data has not been reset. Check or restore " + stateFile, error);
        }
    }

    private static User storedStudent(String id) {
        return USERS.stream().filter(user -> user.id().equals(id) && user.role() == Role.STUDENT)
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown student"));
    }

    private static String property(Properties state, String key) {
        String value = state.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing data field: " + key);
        return value;
    }

    private static int count(Properties state, String key) {
        int value = Integer.parseInt(property(state, key));
        if (value < 0 || value > 100_000) throw new IllegalArgumentException("Invalid record count");
        return value;
    }
}
