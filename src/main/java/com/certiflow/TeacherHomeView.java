package com.certiflow;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The overview uses the same saved requests as the review and student screens. */
public class TeacherHomeView {
    private final VBox view;

    public TeacherHomeView() {
        List<AppData.NocRequest> requests = App.data().requests();
        long pending = requests.stream().filter(r -> r.status() == AppData.Status.PENDING).count();
        long approved = requests.stream().filter(r -> r.status() == AppData.Status.APPROVED).count();
        long rejected = requests.stream().filter(r -> r.status() == AppData.Status.REJECTED).count();
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        long recent = requests.stream().filter(r -> !r.submittedAt().isBefore(weekStart)).count();

        GridPane summary = Ui.grid(4, 120,
                Ui.statistic("Pending", Long.toString(pending), "Awaiting a decision", "stat-gold"),
                Ui.statistic("Approved", Long.toString(approved), "Requests accepted", "stat-green"),
                Ui.statistic("Rejected", Long.toString(rejected), "Requests declined", "stat-red"),
                Ui.statistic("Last 7 days", Long.toString(recent), "New submissions", "stat-neutral"));
        summary.setId("teacher-statistics");

        GridPane actions = Ui.actions(
                Ui.button("Review requests", App::showTeacherDashboard),
                Ui.secondary("Student list", App::showTeacherStudentList),
                Ui.secondary("Export report", App::showTeacherReportExport));

        VBox activity = new VBox(12);
        requests.stream().sorted(Comparator.comparing(TeacherHomeView::activityDate).reversed())
                .limit(5).forEach(request -> activity.getChildren().add(Ui.card(
                        Ui.text(request.studentName() + " · " + request.type()),
                        Ui.muted(request.id() + " · " + Ui.date(activityDate(request))),
                        Ui.status(request.status()))));
        if (activity.getChildren().isEmpty()) {
            activity.getChildren().add(Ui.muted("No applications yet. Submitted NOC requests will appear here."));
        }

        String heading = App.user().role() == AppData.Role.ADMIN ? "Admin overview" : "Teacher home";
        VBox recentActivity = new VBox(16, Ui.section("Recent activity", "The latest applications and review decisions."),
                activity, Ui.secondary("View decision history", App::showTeacherHistory));
        VBox quickActions = Ui.card(Ui.section("Quick actions", "Keep student applications moving."), actions,
                Ui.muted("Decisions appear in student history as soon as they are saved."));
        view = Ui.page(heading, "Home",
                Ui.welcome("Welcome, " + App.user().name(), "Review student applications and track their decisions."),
                summary, Ui.columns(recentActivity, quickActions));
    }

    private static LocalDateTime activityDate(AppData.NocRequest request) {
        return request.decidedAt() == null ? request.submittedAt() : request.decidedAt();
    }

    public VBox getView() { return view; }
}
