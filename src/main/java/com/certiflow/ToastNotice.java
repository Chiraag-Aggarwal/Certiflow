package com.certiflow;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Success feedback never blocks the next action. */
final class ToastNotice extends HBox {
    private final Label message = Ui.text("");
    private final PauseTransition timeout = new PauseTransition(Duration.seconds(6));
    ToastNotice() {
        super(12);
        setId("success-notice");
        getStyleClass().add("toast-notice");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(0);
        setMaxWidth(520);
        setMaxHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(message, Priority.ALWAYS);
        getChildren().addAll(message, Ui.iconButton("close", "Dismiss notification", this::dismiss));
        StackPane.setAlignment(this, Pos.BOTTOM_CENTER);
        StackPane.setMargin(this, new Insets(16));
        setVisible(false);
        setManaged(false);
        timeout.setOnFinished(event -> dismiss());
        setOnMouseEntered(event -> timeout.pause());
        setOnMouseExited(event -> timeout.play());
        sceneProperty().addListener((observable, before, after) -> { if (after == null) timeout.stop(); });
    }
    void show(String text) {
        message.setText(text);
        message.setAccessibleText(text);
        setVisible(true);
        setManaged(true);
        toFront();
        timeout.playFromStart();
    }
    private void dismiss() {
        timeout.stop();
        setVisible(false);
        setManaged(false);
    }
}
