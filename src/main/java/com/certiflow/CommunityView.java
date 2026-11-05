package com.certiflow;

import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Read-only publications deliberately shared with signed-in Certiflow users. */
final class CommunityView {
    private final VBox view;
    private final VBox items=new VBox(16);
    private final TextField search=new TextField();
    private final ComboBox<String> filter=new ComboBox<>();
    private final Label feedback=AccountView.feedback("community-feedback");
    private List<PublicSharing.PublishedItem> publications=List.of();
    private boolean loading;
    CommunityView(){ this(App.user().role()==AppData.Role.VIEWER); }
    CommunityView(boolean discover){
        search.setId("community-search");search.setPromptText("Search public titles, summaries or display names");search.setMinWidth(0);
        filter.getItems().addAll("All publications","Projects and achievements","NOC applications");filter.setValue("All publications");filter.setMinWidth(0);filter.setMaxWidth(Double.MAX_VALUE);filter.setId("community-filter");
        Button refresh=Ui.secondary("Refresh publications",()->load());refresh.setId("community-refresh");
        items.setId("community-items");items.setMinWidth(0);
        VBox welcome=Ui.welcome(discover?"Meet the work behind the potential":"Explore shared work", discover?
            "Discover student projects, achievements and shared NOC updates. Follow your curiosity and explore what they are building.":
            "Projects and application summaries that students have chosen to publish.");
        if(discover) welcome.getChildren().add(Ui.actions(Ui.secondary("Complete your profile",App::showViewerProfile),Ui.secondary("Contact teaching staff",App::showMessages)));
        view=Ui.page(discover?"Discover":"Community", discover?"Discover":"Community",welcome,
            Ui.muted("Publications are student-provided. A polished portfolio does not verify achievements. NOC status is read from the original application; viewing never grants approval or editing access."),
            Ui.grid(2,250,search,filter),refresh,feedback,items);
        search.textProperty().addListener((o,b,a)->render());filter.valueProperty().addListener((o,b,a)->render());
        view.sceneProperty().addListener((o,b,a)->{if(a!=null)javafx.application.Platform.runLater(()->{if(view.getScene()!=null&&view.getScene().getRoot()==view)load();});});
        render();
    }
    private void load(){if(loading)return;loading=true;Ui.feedback(feedback,"Loading community publications…",true);Background.run(items,PublicSharing::community,rows->{loading=false;publications=rows;render();Ui.feedback(feedback,rows.size()+" shared publication(s).",true);},error->{loading=false;Ui.feedback(feedback,error.getMessage(),false);});}
    private void render(){
        String query=search.getText().trim().toLowerCase(Locale.ROOT);String category=filter.getValue();items.getChildren().clear();
        publications.stream().filter(p->category==null||category.equals("All publications")||(category.equals("NOC applications")==p.sourceKind().equals("NOC")))
            .filter(p->(p.title()+" "+p.summary()+" "+p.displayName()+" "+p.category()).toLowerCase(Locale.ROOT).contains(query)).forEach(p->{
                VBox card=Ui.card(Ui.title(p.title()),Ui.muted("By "+p.displayName()+" · "+Ui.date(p.publishedAt())),Ui.muted(p.category()+(p.sourceKind().equals("NOC")?" · Status: "+p.status():" · Student-provided work")));
                if(!p.summary().isBlank())card.getChildren().add(Ui.text(p.summary()));
                if(!p.link().isBlank())card.getChildren().add(Ui.secondary("Open published link",()->App.openWebLink(p.link())));
                for(PublicSharing.FileItem file:p.files())card.getChildren().add(Ui.secondary("Open "+file.fileName(),()->Background.run(view,()->PublicSharing.download(file),App::openFile,error->Ui.error(error.getMessage()))));
                if(List.of("LINK","BOTH").contains(p.audience()))card.getChildren().add(Ui.secondary("Open public web page",()->App.openWebLink(PublicSharing.publicUrl(p))));
                items.getChildren().add(card);
            });
        if(items.getChildren().isEmpty())items.getChildren().add(Ui.card(Ui.title("No publications to show"),Ui.muted(publications.isEmpty()?"Student work will appear here when its owner shares it with the Certiflow community. Check back as the community grows.":"Try another search or publication type.")));
    }
    VBox getView(){return view;}
}
