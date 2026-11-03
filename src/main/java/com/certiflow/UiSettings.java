package com.certiflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Parent;

/** Local appearance preferences, separate from academic records. */
final class UiSettings {
    enum Palette {
        FOREST("Forest"), PLUM("Plum"), TERRACOTTA("Terracotta"), TEAL("Teal");
        final String label;
        Palette(String label) { this.label = label; }
        String key() { return name().toLowerCase(Locale.ROOT); }
    }
    static final BooleanProperty dark = new SimpleBooleanProperty(false);
    static final ObjectProperty<Palette> palette = new SimpleObjectProperty<>(Palette.FOREST);
    private static Path file;
    private UiSettings() {}

    static void load(Path directory) {
        file = directory.resolve("appearance.properties");
        Properties values = new Properties();
        if (Files.isRegularFile(file)) {
            try (var input = Files.newInputStream(file)) { values.load(input); }
            catch (IOException | IllegalArgumentException ignored) { /* Retain safe defaults. */ }
        }
        dark.set(Boolean.parseBoolean(values.getProperty("dark", "false")));
        try { palette.set(Palette.valueOf(values.getProperty("palette", "FOREST").toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ex) { palette.set(Palette.FOREST); }
    }

    static void save() {
        if (file == null) return;
        Path temporary = file.resolveSibling("appearance.properties.tmp");
        Properties values = new Properties();
        values.setProperty("dark", Boolean.toString(dark.get()));
        values.setProperty("palette", palette.get().name());
        try {
            try (var output = Files.newOutputStream(temporary)) { values.store(output, "Certiflow appearance"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Ui.error("Your appearance changed for this session, but could not be saved: " + ex.getMessage());
        }
    }

    static void decorate(Parent root) {
        ChangeListener<Boolean> darkListener = (observable, before, after) ->
            root.pseudoClassStateChanged(PseudoClass.getPseudoClass("dark"), after);
        ChangeListener<Palette> paletteListener = (observable, before, after) -> applyPalette(root, after);
        root.getProperties().put("appearance-listener", darkListener);
        root.getProperties().put("palette-listener", paletteListener);
        dark.addListener(new WeakChangeListener<>(darkListener));
        palette.addListener(new WeakChangeListener<>(paletteListener));
        root.pseudoClassStateChanged(PseudoClass.getPseudoClass("dark"), dark.get());
        applyPalette(root, palette.get());
    }

    private static void applyPalette(Parent root, Palette selected) {
        for (Palette value : Palette.values())
            root.pseudoClassStateChanged(PseudoClass.getPseudoClass(value.key()), value == selected);
    }
    static boolean animate() { return App.window() != null && App.window().isShowing(); }
}
