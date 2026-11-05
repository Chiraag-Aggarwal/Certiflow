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
                Ui.secondary("Shared portfolios", App::showSharedPortfolios),
                Ui.secondary("Messages", App::showMessages),
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
        long directlyAssigned = requests.stream().filter(request -> App.user().id().equals(request.recipientId()))
                .filter(request -> request.status() == AppData.Status.PENDING).count();
        VBox inbox = Ui.card(Ui.section("Your review workspace", "A clear view of what needs attention."),
                Ui.text(directlyAssigned + " pending request" + (directlyAssigned == 1 ? "" : "s") + " assigned directly to you"),
                Ui.text(App.data().projects().size() + " visible portfolio entr" + (App.data().projects().size() == 1 ? "y" : "ies")),
                Ui.muted(App.user().role() == AppData.Role.ADMIN
                    ? "Administrators can oversee all submissions. Personal chats remain visible only to their participants."
                    : "Your requests include the shared staff queue and submissions assigned to you. Portfolio entries appear when a student shares them with you."),
                Ui.actions(Ui.secondary("Open portfolios", App::showSharedPortfolios), Ui.secondary("Open messages", App::showMessages)));
        VBox guidance = Ui.card(Ui.section("Before you decide", "Make every review clear and useful."),
                Ui.text("1. Check the student's identity and supporting details."),
                Ui.text("2. Use Messages if you need clarification."),
                Ui.text("3. Add a clear note when you record a decision."),
                Ui.muted("A filled profile or polished portfolio does not verify a student's claims. Identity checks are separate from self-reported achievements."),
                Ui.secondary("Update my profile", App::showTeacherProfile));
        if (App.user().role() == AppData.Role.ADMIN) {
            guidance.getChildren().addAll(Ui.section("Account administration", "Approve staff access and check identity details against institutional records."),
                    Ui.secondary("Manage account access", App::showAccountManagement));
        }
        view = Ui.page(heading, "Home",
                Ui.welcome("Welcome, " + App.user().name(), "Review student applications and track their decisions."),
                summary, Ui.columns(recentActivity, quickActions), Ui.columns(inbox, guidance));
    }

    private static LocalDateTime activityDate(AppData.NocRequest request) {
        return request.decidedAt() == null ? request.submittedAt() : request.decidedAt();
    }

    public VBox getView() { return view; }
}
