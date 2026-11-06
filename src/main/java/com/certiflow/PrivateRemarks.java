package com.certiflow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Private feedback loads only when opened and is never part of a publication. */
final class PrivateRemarks {
    private PrivateRemarks() {}

    static TitledPane forRequest(AppData.NocRequest request) {
        return panel(AppData.RemarkSource.NOC, request.id());
    }

    static TitledPane forProject(AppData.Project project) {
        return panel(AppData.RemarkSource.PROJECT, project.id());
    }

    private static TitledPane panel(AppData.RemarkSource source, String id) {
        AppData.User actor = App.user();
        VBox history = new VBox(10);
        history.setId("remark-history-" + id);
        Label feedback = AccountView.feedback("remark-feedback-" + id);
        Label audience = Ui.muted("Visible only to the student, the staff member who wrote it, and administrators. Never included in public links or Community.");
        VBox content = new VBox(12, audience, history, feedback);
        content.setMinWidth(0);
        content.getStyleClass().add("private-remarks-content");

        TitledPane pane = new TitledPane("Private remarks", content);
        pane.setId("private-remarks-" + id);
        pane.setMinWidth(0);
        pane.setExpanded(false);
        pane.setAnimated(false);
        pane.getStyleClass().add("private-remarks");

        List<AppData.PrivateRemark> loaded = new ArrayList<>();
        boolean[] busy = {false};
        Button more = Ui.secondary("Load earlier remarks", () -> {});
        more.setId("remark-more-" + id);
        Runnable render = () -> {
            history.getChildren().clear();
            if (loaded.isEmpty()) {
                history.getChildren().add(Ui.muted("No private remarks yet. Feedback for this item will appear here."));
            }
            for (var remark : loaded) {
                VBox card = new VBox(6, Ui.text(remark.authorName()),
                    Ui.muted(Ui.date(remark.createdAt())), Ui.text(remark.body()));
                card.getStyleClass().add("remark-note");
                card.setMinWidth(0);
                history.getChildren().add(card);
            }
        };

        // Refresh replaces history only after success; a failed request leaves earlier rows visible.
        Consumer<Boolean> load = reset -> {
            if (busy[0]) return;
            busy[0] = true;
            int offset = reset ? 0 : loaded.size();
            Ui.feedback(feedback, "Loading private remarks…", true);
            Background.run(content, () -> App.data().privateRemarks(source, id, offset), rows -> {
                busy[0] = false;
                if (!sameAccount(actor)) return;
                if (reset) loaded.clear();
                for (var remark : rows) {
                    if (loaded.stream().noneMatch(r -> r.id().equals(remark.id()))) loaded.add(remark);
                }
                render.run();
                more.setVisible(rows.size() == 50);
                more.setManaged(rows.size() == 50);
                feedback.setVisible(false);
                feedback.setManaged(false);
            }, error -> {
                busy[0] = false;
                if (sameAccount(actor)) {
                    Ui.feedback(feedback, "Remarks could not be loaded. " + error.getMessage(), false);
                }
            });
        };
        Button refresh = Ui.secondary("Refresh remarks", () -> load.accept(true));
        refresh.setId("remark-refresh-" + id);
        more.setOnAction(event -> load.accept(false));
        more.setVisible(false);
        more.setManaged(false);
        content.getChildren().addAll(more, refresh);

        // Students get the same history panel without a composer. Server rules also enforce this.
        if (actor.role().isStaff()) {
            TextArea draft = new TextArea();
            draft.setId("remark-draft-" + id);
            draft.setPromptText("Write clear, helpful feedback for this student…");
            draft.setWrapText(true);
            draft.setPrefRowCount(3);
            draft.setMinWidth(0);
            draft.setAccessibleText("Private remark for this item");
            Label remaining = Ui.muted("0 / 2,000 characters");
            draft.textProperty().addListener((o, b, a) -> remaining.setText(a.length() + " / 2,000 characters"));
            Button send = Ui.button("Send private remark", () -> {});
            send.setId("remark-send-" + id);
            send.disableProperty().bind(draft.textProperty().isEmpty().or(draft.textProperty().length().greaterThan(2000)));
            send.setOnAction(event -> {
                String body = draft.getText();
                Background.run(content, () -> App.data().addPrivateRemark(source, id, body), saved -> {
                    if (!sameAccount(actor)) return;
                    // Clear only after the server confirms saving; failed sends retain the draft.
                    draft.clear();
                    loaded.add(0, saved);
                    render.run();
                    Ui.feedback(feedback, "Private remark sent to the student.", true);
                }, error -> {
                    if (sameAccount(actor)) Ui.feedback(feedback, error.getMessage(), false);
                });
            });
            content.getChildren().addAll(Ui.text("Add a remark"), draft, remaining, send);
        }

        // Collapsed entries do not make network requests for their private history.
        pane.expandedProperty().addListener((o, b, a) -> {
            if (a && sameAccount(actor)) load.accept(true);
        });
        return pane;
    }

    // An async result must not expose private text after sign-out or a role change.
    private static boolean sameAccount(AppData.User actor) {
        var current = App.data().currentUser();
        return current != null && current.id().equals(actor.id()) && current.role() == actor.role();
    }
}
