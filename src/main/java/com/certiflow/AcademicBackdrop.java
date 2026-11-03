package com.certiflow;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** A quiet, scalable notebook template with margin lines and academic geometry. */
final class AcademicBackdrop extends Region {
    private final Canvas canvas = new Canvas();
    private final ChangeListener<UiSettings.Palette> paletteListener = (o, before, after) -> draw();
    private final ChangeListener<Boolean> darkListener = (o, before, after) -> draw();

    AcademicBackdrop() {
        setId("academic-background");
        setMinSize(0, 0);
        setMouseTransparent(true);
        setFocusTraversable(false);
        getChildren().add(canvas);
        UiSettings.palette.addListener(new WeakChangeListener<>(paletteListener));
        UiSettings.dark.addListener(new WeakChangeListener<>(darkListener));
    }

    @Override protected void layoutChildren() {
        if (canvas.getWidth() != getWidth() || canvas.getHeight() != getHeight()) {
            canvas.setWidth(getWidth());
            canvas.setHeight(getHeight());
            draw();
        }
    }

    private void draw() {
        double width = canvas.getWidth(), height = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        Color accent = Color.web(switch (UiSettings.palette.get()) {
            case FOREST -> "#47765d";
            case PLUM -> "#8b5792";
            case TERRACOTTA -> "#b86544";
            case TEAL -> "#268280";
        });
        boolean dark = UiSettings.dark.get();
        g.setFill(accent);
        g.setGlobalAlpha(dark ? 0.085 : 0.07);
        g.fillOval(width - 330, -180, 520, 430);
        g.fillOval(-180, height - 230, 400, 420);
        g.setGlobalAlpha(dark ? 0.12 : 0.085);
        g.setStroke(dark ? Color.web("#c8c9bd") : accent);
        g.setLineWidth(0.7);
        for (double y = 28; y < height; y += 32) g.strokeLine(0, y, width, y);
        g.setGlobalAlpha(dark ? 0.16 : 0.10);
        double margin = width < 600 ? 9 : 28;
        g.strokeLine(margin, 0, margin, height);
        g.setFill(accent);
        for (double y = 12; y < height; y += 64)
            for (double x = margin + 30; x < width; x += 64) g.fillOval(x, y, 1.7, 1.7);
        // One outlined notebook in the margin; never placed over an input.
        if (width > 900) {
            g.setGlobalAlpha(dark ? 0.16 : 0.11);
            g.setLineWidth(2);
            g.save();
            g.translate(width - 95, height - 110);
            g.rotate(13);
            g.strokeRoundRect(-28, -36, 56, 72, 8, 8);
            g.strokeLine(-16, -35, -16, 35);
            g.strokeLine(-7, -14, 16, -14);
            g.strokeLine(-7, -3, 12, -3);
            g.restore();
        }
        g.setGlobalAlpha(1);
    }
}
