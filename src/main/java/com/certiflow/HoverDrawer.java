package com.certiflow;

import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Interpolator;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Overlay navigation with delayed hover dismissal and a keyboard/touch alternative. */
final class HoverDrawer extends StackPane {
    private final VBox panel;
    private final Button menu;
    private final Region edge = new Region();
    private final Region shade = new Region();
    private final PauseTransition leaveDelay = new PauseTransition(Duration.millis(300));
    private final TranslateTransition slide = new TranslateTransition(Duration.millis(190));
    private boolean desktop;
    private boolean open;
    private boolean edgeInside;
    private boolean panelInside;
    private final ChangeListener<Node> focusListener;

    HoverDrawer(Node content, VBox panel, Button menu) {
        this.panel = panel;
        this.menu = menu;
        setMinSize(0, 0);
        panel.setMinWidth(260);
        panel.setPrefWidth(260);
        panel.setMaxWidth(260);
        StackPane.setAlignment(panel, Pos.CENTER_LEFT);
        panel.setVisible(false);
        panel.setMouseTransparent(true);
        panel.setTranslateX(-280);
        shade.getStyleClass().add("drawer-shade");
        shade.setVisible(false);
        shade.setMouseTransparent(true);
        edge.setId("sidebar-edge");
        edge.getStyleClass().add("sidebar-edge");
        edge.setMinWidth(9);
        edge.setPrefWidth(9);
        edge.setMaxWidth(9);
        StackPane.setAlignment(edge, Pos.CENTER_LEFT);
        StackPane.setMargin(edge, new Insets(84, 0, 24, 0));
        Tooltip.install(edge, new Tooltip("Reveal navigation • Alt+M"));
        getChildren().addAll(content, shade, edge, panel);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        slide.setNode(panel);
        slide.setInterpolator(Interpolator.EASE_BOTH);
        slide.setOnFinished(event -> { if (!open) panel.setVisible(false); });
        leaveDelay.setOnFinished(event -> {
            if (!edgeInside && !panelInside && !hasDrawerFocus()) close(false);
        });
        edge.setOnMouseEntered(event -> { edgeInside = true; reveal(false); });
        edge.setOnMouseExited(event -> { edgeInside = false; scheduleClose(); });
        panel.setOnMouseEntered(event -> { panelInside = true; leaveDelay.stop(); });
        panel.setOnMouseExited(event -> { panelInside = false; scheduleClose(); });
        menu.setOnAction(event -> { if (open) close(true); else reveal(true); });
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (open && event.getTarget() instanceof Node target && !within(target, panel) && !within(target, menu))
                close(false);
        });
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.M && event.isAltDown()) {
                if (open) close(true); else reveal(true);
                event.consume();
            } else if (open && event.getCode() == KeyCode.ESCAPE) {
                close(true);
                event.consume();
            }
        });
        focusListener = (observable, before, after) -> {
            if (within(after, panel)) leaveDelay.stop(); else scheduleClose();
        };
        sceneProperty().addListener((observable, before, after) -> {
            if (before != null) before.focusOwnerProperty().removeListener(focusListener);
            if (after != null) after.focusOwnerProperty().addListener(focusListener);
            else { leaveDelay.stop(); slide.stop(); }
        });
    }

    void setDesktop(boolean value) {
        if (desktop != value) {
            desktop = value;
            close(false);
            edgeInside = false;
            panelInside = false;
        }
        edge.setVisible(value);
        edge.setManaged(value);
        panel.setManaged(true);
        menu.setVisible(true);
        menu.setManaged(true);
    }

    void reveal(boolean keyboard) {
        leaveDelay.stop();
        open = true;
        panel.setVisible(true);
        panel.setMouseTransparent(false);
        shade.setVisible(true);
        menu.setAccessibleText("Close navigation");
        moveTo(0);
        if (keyboard) {
            Node first = panel.lookup(".app-nav-button");
            if (first != null) first.requestFocus();
        }
    }

    void close(boolean returnFocus) {
        leaveDelay.stop();
        open = false;
        panel.setMouseTransparent(true);
        shade.setVisible(false);
        menu.setAccessibleText("Open navigation");
        moveTo(-280);
        if (returnFocus) menu.requestFocus();
    }

    boolean isOpen() { return open; }

    private void moveTo(double destination) {
        slide.stop();
        if (UiSettings.animate()) {
            slide.setToX(destination);
            slide.playFromStart();
        } else {
            panel.setTranslateX(destination);
            panel.setVisible(open);
        }
    }

    private void scheduleClose() { if (open) leaveDelay.playFromStart(); }
    private boolean hasDrawerFocus() { return getScene() != null && within(getScene().getFocusOwner(), panel); }
    private static boolean within(Node target, Node parent) {
        for (Node node = target; node != null; node = node.getParent()) if (node == parent) return true;
        return false;
    }
}
