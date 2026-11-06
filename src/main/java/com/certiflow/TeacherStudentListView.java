package com.certiflow;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The roster and its request counts use the same identities as applications. */
public class TeacherStudentListView {
    private final VBox view;
    private final TextField search = new TextField();
    private final Label count = Ui.muted("");
    private final GridPane students = Ui.grid(2, 350);

    public TeacherStudentListView() {
        search.setPromptText("Search students by name, ID or department");
        search.setId("student-roster-search");
        search.setMinWidth(0);
        search.setAccessibleText("Search the student roster");
        students.setId("student-card-grid");
        search.textProperty().addListener((observable, before, after) -> refresh());
        VBox filters = Ui.card(PageComponents.section("students", "Find a student", "Browse registered students and the application records available to you."),
                search, count);
        filters.getStyleClass().add("review-filter");
        view = Ui.page("Student roster", "Students", filters, students);
        refresh();
    }

    private void refresh() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        List<AppData.User> roster = App.data().users().stream()
                .filter(user -> user.role() == AppData.Role.STUDENT).toList();
        List<AppData.User> visible = roster.stream()
                .filter(user -> (user.name() + " " + user.displayId() + " " + user.department() + " " + user.email())
                        .toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(AppData.User::name, String.CASE_INSENSITIVE_ORDER)).toList();
        count.setText("Showing " + visible.size() + " of " + roster.size() + " students");
        students.getChildren().clear();
        for (AppData.User student : visible) {
            List<AppData.NocRequest> requests = App.data().requestsFor(student.id());
            long pending = requests.stream().filter(r -> r.status() == AppData.Status.PENDING).count();
            long approved = requests.stream().filter(r -> r.status() == AppData.Status.APPROVED).count();
            long rejected = requests.stream().filter(r -> r.status() == AppData.Status.REJECTED).count();
            Label avatar = new Label(initials(student.name()));
            avatar.getStyleClass().add("roster-avatar");
            avatar.setMinSize(48, 48); avatar.setPrefSize(48, 48); avatar.setMaxSize(48, 48);
            VBox name = Ui.section(student.name(), student.department().isBlank() ? "Department not provided" : student.department());
            name.setMinWidth(0); HBox.setHgrow(name, Priority.ALWAYS);
            VBox card = Ui.card(Ui.row(avatar, name),
                    Ui.muted("Student ID · " + student.displayId()),
                    PageComponents.tag(student.identityVerified() ? "Identity checked" : "Self-reported identity",
                            student.identityVerified() ? "review-good" : "review-route"),
                    Ui.text(requests.size() + " accessible applications"),
                    Ui.grid(3, 90, PageComponents.tag(pending + " pending", "review-warn"),
                            PageComponents.tag(approved + " approved", "review-good"),
                            PageComponents.tag(rejected + " rejected", "review-bad")),
                    Ui.messageContact("Message student", student.id()));
            card.getStyleClass().add("roster-card");
            students.getChildren().add(card);
        }
        if (visible.isEmpty()) students.getChildren().add(WorkspaceCards.empty(
                roster.isEmpty() ? "Students will appear here" : "No matching students",
                roster.isEmpty() ? "The roster fills as students create and verify their accounts." : "Try another name, ID or department.", "students"));
    }

    private static String initials(String name) {
        String[] words = name.strip().split("\\s+");
        if (words.length == 0 || words[0].isBlank()) return "S";
        return (words[0].substring(0, 1) + (words.length > 1 ? words[words.length - 1].substring(0, 1) : "")).toUpperCase(Locale.ROOT);
    }

    public VBox getView() { return view; }
}
