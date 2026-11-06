package com.certiflow;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/** Device-local chat preferences. Message contents and drafts are never written here. */
record ChatSettings(boolean enterToSend, boolean compact, boolean largeText, boolean showPreviews, boolean autoScroll) {
    static ChatSettings defaults() { return new ChatSettings(false, false, false, true, true); }

    static ChatSettings load(Path directory, String accountId) {
        Path file = file(directory, accountId);
        if (!Files.isRegularFile(file)) return defaults();
        Properties values = new Properties();
        try (var input = Files.newInputStream(file)) { values.load(input); }
        catch (IOException | IllegalArgumentException ex) { return defaults(); }
        return new ChatSettings(value(values, "enterToSend", false), value(values, "compact", false),
            value(values, "largeText", false), value(values, "showPreviews", true), value(values, "autoScroll", true));
    }

    void save(Path directory, String accountId) throws IOException {
        Path target = file(directory, accountId), temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Properties values = new Properties();
        values.setProperty("enterToSend", Boolean.toString(enterToSend));
        values.setProperty("compact", Boolean.toString(compact));
        values.setProperty("largeText", Boolean.toString(largeText));
        values.setProperty("showPreviews", Boolean.toString(showPreviews));
        values.setProperty("autoScroll", Boolean.toString(autoScroll));
        Files.createDirectories(target.getParent());
        try {
            try (var output = Files.newOutputStream(temporary)) { values.store(output, "Certiflow chat preferences"); }
            // Replace a complete file so interrupted writes do not destroy the last preferences.
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static boolean value(Properties values, String key, boolean fallback) {
        return Boolean.parseBoolean(values.getProperty(key, Boolean.toString(fallback)));
    }
    private static Path file(Path directory, String accountId) {
        // Separate each account's settings and reject path characters through UUID parsing.
        return directory.toAbsolutePath().normalize().resolve("chat-" + UUID.fromString(accountId) + ".properties");
    }
}
