package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Student overview backed by the same saved requests the teacher reviews. */
public class StudentDashboardView {
    private final VBox view;

    public StudentDashboardView() {
        List<AppData.NocRequest> requests = App.data().requestsFor(App.user().id());
        int documentCount = App.data().portfoliosFor(App.user().id()).size();
        GridPane overview = Ui.grid(4, 120,
                statistic(requests, AppData.Status.APPROVED, "Approved", "stat-green"),
                statistic(requests, AppData.Status.PENDING, "Pending", "stat-gold"),
                statistic(requests, AppData.Status.REJECTED, "Rejected", "stat-red"),
                Ui.statistic("Documents", Integer.toString(documentCount), "In your portfolio", "stat-neutral"));
        overview.setId("student-statistics");

        TextField search = new TextField();
        search.setId("student-search");
        search.setPromptText("Search your NOC applications by type or ID");
        VBox recentList = new VBox(12);
        Runnable refresh = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            List<AppData.NocRequest> matches = requests.stream()
                    .filter(request -> StudentNocView.matches(request, query))
                    .toList();
            recentList.getChildren().clear();
            if (matches.isEmpty()) {
                recentList.getChildren().add(Ui.muted(query.isEmpty()
                        ? "You have no applications yet. Create one using New NOC application."
                        : "No applications match your search."));
            } else {
                matches.stream().limit(3)
                        .forEach(request -> recentList.getChildren().add(StudentNocView.requestCard(request)));
                if (matches.size() > 3) {
                    recentList.getChildren().add(Ui.muted("Showing the 3 most recent matches. Open All applications to see more."));
                }
            }
        };
        search.textProperty().addListener((observable, oldText, newText) -> refresh.run());
        refresh.run();

        VBox recent = new VBox(16, Ui.section("Recent NOC applications", "Follow the progress of your latest requests."),
                search, recentList, Ui.secondary("All applications", App::showStudentNoc));
        VBox actions = Ui.card(Ui.section("Quick actions", "Start a request or keep your documents together."),
                Ui.actions(Ui.button("New NOC application", App::showStudentNoc),
                        Ui.secondary("My portfolios", App::showStudentPortfolio)),
                Ui.muted(documentCount + (documentCount == 1 ? " saved portfolio document" : " saved portfolio documents")));
        view = Ui.page("Student Home", "Home",
                Ui.welcome("Welcome, " + App.user().name(), "Your applications and documents, in one place."),
                overview, Ui.columns(recent, actions));
    }

    private VBox statistic(List<AppData.NocRequest> requests, AppData.Status status, String title, String style) {
        long count = requests.stream().filter(request -> request.status() == status).count();
        return Ui.statistic(title, Long.toString(count), "NOC applications", style);
    }

    public VBox getView() {
        return view;
    }
}
