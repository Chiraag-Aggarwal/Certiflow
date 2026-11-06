package com.certiflow;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Student overview backed by the same saved requests the teacher reviews. */
public class StudentDashboardView {
    private final VBox view;

    public StudentDashboardView() {
        List<AppData.NocRequest> requests = App.data().requestsFor(App.user().id());
        int documentCount = App.data().portfoliosFor(App.user().id()).size();
        var projects = App.data().projectsFor(App.user().id());
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
                recentList.getChildren().add(WorkspaceCards.empty(query.isEmpty()?"Your next step starts here":"No matching applications", query.isEmpty()
                        ? "Planning an internship, scholarship or event? Open applications to send your first NOC request."
                        : "Try another type or request ID.","noc"));
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

        int completion = TeacherProfileView.completion(App.user());
        ProgressBar progress = new ProgressBar(completion / 100.0);
        progress.getStyleClass().add("profile-completion-meter");
        progress.setMinHeight(10); progress.setPrefHeight(10); progress.setMaxHeight(10);
        progress.setMaxWidth(Double.MAX_VALUE); progress.setMinWidth(0); progress.setId("student-profile-progress");
        VBox nextSteps = Ui.card(Ui.section("Your next steps", "Small updates help teachers understand your work."),
                Ui.text("Profile details · " + completion + "% complete"), progress,
                Ui.muted(completion == 100 ? "Your introduction, programme and interests are filled in. Keep them current."
                        : "Add your programme, introduction and interests to complete your profile."),
                Ui.secondary("Edit my profile", App::showStudentProfile),
                Ui.text(projects.isEmpty() ? "Start your portfolio" : projects.size() + " portfolio entr" + (projects.size() == 1 ? "y" : "ies")),
                Ui.muted(projects.isEmpty() ? "Create a project, certificate, achievement or experience entry, then attach supporting files."
                        : "Add evidence to your entries and choose a teacher when you want to share your work."),
                Ui.secondary("Build my portfolio", App::showStudentPortfolio));
        VBox portfolioHighlights = Ui.card(Ui.section("Portfolio highlights", "Your latest saved work."));
        if (projects.isEmpty()) {
            portfolioHighlights.getChildren().add(Ui.muted("Your saved entries will appear here. Use a title, a short description and evidence to tell the story of your work."));
        } else projects.stream().limit(3).forEach(project -> portfolioHighlights.getChildren().add(
                Ui.section(project.title(), project.kind() + (project.recipientId() == null || project.recipientId().isBlank()
                    ? " · Private entry" : " · Shared with " + App.data().contactName(project.recipientId())))));
        portfolioHighlights.getChildren().addAll(Ui.secondary("Open portfolio", App::showStudentPortfolio),
                Ui.muted("Choose a registered teacher on a portfolio entry or NOC application to send it directly to them. Use Messages for questions and follow-ups."));
        view = Ui.page("Student Home", "Home",
                Ui.welcome("Welcome, " + App.user().name(), "Your applications and documents, in one place."),
                overview, WorkspaceCards.shortcuts(), Ui.columns(recent, nextSteps), portfolioHighlights);
    }

    private VBox statistic(List<AppData.NocRequest> requests, AppData.Status status, String title, String style) {
        long count = requests.stream().filter(request -> request.status() == status).count();
        return Ui.statistic(title, Long.toString(count), "NOC applications", style);
    }

    public VBox getView() {
        return view;
    }
}
