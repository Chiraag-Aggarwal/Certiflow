package com.certiflow;

import javafx.scene.control.Label;
import javafx.scene.layout.*;

/** Useful destinations and honest empty states, without sample activity. */
final class WorkspaceCards {
    private WorkspaceCards() {}
    static GridPane shortcuts() {
        if(App.user().role()==AppData.Role.STUDENT) return Ui.grid(3,250,
            action("portfolios","Make your work stand out","Projects, achievements and evidence, all in your portfolio.","Build my portfolio",App::showStudentPortfolio,"lilac"),
            action("noc","Keep your plans moving","Submit an application and follow each decision.","Open applications",App::showStudentNoc,"peach"),
            action("messages","A little guidance goes far","Ask a teacher a question or follow up on feedback.","Open messages",App::showMessages,"mint"));
        long pending=App.data().requests().stream().filter(r->r.status()==AppData.Status.PENDING).count();
        return Ui.grid(3,250,
            action("requests","Ready for your review",pending+" pending application"+(pending==1?"":"s")+" in your accessible queue.","Review applications",App::showTeacherDashboard,"peach"),
            action("portfolios","Support the next big idea",App.data().projects().size()+" portfolio entries available for your feedback.","Review portfolios",App::showSharedPortfolios,"lilac"),
            action("messages","Keep the conversation going","Clarify a detail or help a student take their next step.","Open messages",App::showMessages,"mint"));
    }
    private static VBox action(String icon,String title,String detail,String label,Runnable route,String colour) {
        StackPane badge=new StackPane(Icons.of(icon));badge.getStyleClass().add("workspace-icon");
        badge.setMinSize(42,42);badge.setPrefSize(42,42);badge.setMaxSize(42,42);
        VBox tile=Ui.card(badge,Ui.title(title),Ui.muted(detail),Ui.secondary(label+"  →",route));
        tile.getStyleClass().addAll("workspace-tile","tile-"+colour);return tile;
    }
    static VBox empty(String heading,String detail,String icon) {
        StackPane badge=new StackPane(Icons.of(icon));badge.getStyleClass().add("empty-state-icon");
        badge.setMinSize(50,50);badge.setPrefSize(50,50);badge.setMaxSize(50,50);
        Label title=Ui.title(heading);
        VBox state=Ui.card(badge,title,Ui.muted(detail));state.getStyleClass().add("workspace-empty");return state;
    }
}
