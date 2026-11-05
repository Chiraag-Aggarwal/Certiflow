package com.certiflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Selects a real registered staff account; free text never becomes a recipient. */
final class RecipientPicker extends VBox {
    private record Choice(String id, String label) {
        @Override public String toString() { return label; }
    }
    private final TextField search = new TextField();
    private final ComboBox<Choice> choices = new ComboBox<>();
    private final List<Choice> staff;
    private final Choice none;

    RecipientPicker(String prefix, String emptyLabel, String explanation) {
        super(8);
        setMinWidth(0);
        none = new Choice("", emptyLabel);
        staff = App.data().contacts().stream().filter(c -> c.role().isStaff())
            .map(c -> new Choice(c.id(), c.name() + " · " + Ui.roleName(c.role())
                + (c.department().isBlank() ? "" : " · " + c.department())))
            .toList();
        search.setId(prefix + "-recipient-search");
        search.setPromptText("Find a teacher by name or department");
        search.setAccessibleText("Search registered teachers and administrators");
        search.setMinWidth(0);
        choices.setId(prefix + "-recipient");
        choices.setAccessibleText("Optional recipient");
        choices.setEditable(false);
        choices.setMinWidth(0);
        choices.setMaxWidth(Double.MAX_VALUE);
        choices.setCellFactory(list -> cell());
        choices.setButtonCell(cell());
        choices.getItems().add(none);
        choices.getItems().addAll(staff);
        choices.setValue(none);
        search.textProperty().addListener((o, before, after) -> filter());
        getChildren().addAll(Ui.text("Send to a teacher (optional)"), search, choices, Ui.muted(explanation));
        if (staff.isEmpty()) getChildren().add(Ui.muted("No registered staff are available yet. Refresh after a teacher joins."));
    }

    private static ListCell<Choice> cell() {
        return new ListCell<>() {
            @Override protected void updateItem(Choice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    private void filter() {
        Choice selected = choices.getValue();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        List<Choice> filtered = new ArrayList<>();
        filtered.add(none);
        staff.stream().filter(c -> c.label().toLowerCase(Locale.ROOT).contains(query)).forEach(filtered::add);
        // Searching must not silently change a previously chosen recipient.
        if (selected != null && !filtered.contains(selected)) filtered.add(selected);
        choices.getItems().setAll(filtered);
        choices.setValue(selected == null ? none : selected);
    }

    String recipientId() { return choices.getValue() == null ? "" : choices.getValue().id(); }

    void select(String id) {
        search.clear();
        if (id == null || id.isBlank()) { choices.setValue(none); return; }
        Choice selected = staff.stream().filter(c -> c.id().equals(id)).findFirst()
            .orElse(new Choice(id, App.data().contactName(id) + " · current recipient"));
        if (!choices.getItems().contains(selected)) choices.getItems().add(selected);
        choices.setValue(selected);
    }
}
