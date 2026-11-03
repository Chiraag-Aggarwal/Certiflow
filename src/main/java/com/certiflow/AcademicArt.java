package com.certiflow;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Decorative artwork remains small, non-interactive, and separate from form controls. */
final class AcademicArt {
    private AcademicArt() {}
    private static final Image BOOKS = new Image(AcademicArt.class.getResource("/art/study-books.png").toExternalForm(), 480, 0, true, true);
    private static final Image SPARKLE = new Image(AcademicArt.class.getResource("/art/study-sparkle.gif").toExternalForm());

    static ImageView books(double width) {
        ImageView image = new ImageView(BOOKS);
        image.setFitWidth(width);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        image.setMouseTransparent(true);
        image.getStyleClass().add("study-illustration");
        return image;
    }

    static ImageView sparkle() {
        ImageView image = new ImageView(SPARKLE);
        image.setId("study-sparkle");
        image.setFitWidth(36);
        image.setFitHeight(36);
        image.setMouseTransparent(true);
        return image;
    }

    static Node sticker() {
        SVGPath paper = new SVGPath();
        paper.setContent("M12 4 L47 4 Q52 4 52 9 L52 54 Q52 59 47 59 L12 59 Q7 59 7 54 L7 9 Q7 4 12 4 Z");
        paper.getStyleClass().add("sticker-paper");
        SVGPath lines = new SVGPath();
        lines.setContent("M18 16 H41 M18 24 H35 M18 32 H29 M30 46 L35 51 L45 39");
        lines.getStyleClass().add("sticker-ink");
        javafx.scene.Group graphic = new javafx.scene.Group(paper, lines);
        graphic.setRotate(-7);
        StackPane sticker = new StackPane(graphic);
        sticker.getStyleClass().add("academic-sticker");
        sticker.setMinSize(64, 68);
        sticker.setPrefSize(64, 68);
        sticker.setMaxSize(64, 68);
        sticker.setMouseTransparent(true);
        return sticker;
    }
}
