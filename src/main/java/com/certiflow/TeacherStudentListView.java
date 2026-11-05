package com.certiflow;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
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
        students.setId("student-card-grid");
        search.textProperty().addListener((observable, before, after) -> refresh());
        view = Ui.page("Student roster", "Students",
                search, count, students);
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
            students.getChildren().add(Ui.card(Ui.title(student.name()),
                    Ui.muted(student.displayId() + " · " + student.department()),
                    Ui.muted(student.identityVerified() ? "Identity checked by an administrator" : "Identity details are self-reported"),
                    Ui.text(requests.size() + " applications · " + pending + " pending · "
                            + approved + " approved · " + rejected + " rejected")));
        }
        if (visible.isEmpty()) students.getChildren().add(Ui.card(Ui.muted("No students match your search.")));
    }

    public VBox getView() { return view; }
}
