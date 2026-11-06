package com.certiflow;

import java.util.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

/** The student reviews the exact public fields before opting in. */
final class SharingControls {
    private SharingControls() {}
    static Node forProject(AppData.Project project) { return Ui.secondary("Sharing & public preview",()->open("PROJECT",project.id(),project.title(),project.kind(),"",App.data().portfoliosFor(project.studentId()).stream().filter(f->project.id().equals(f.projectId())).toList())); }
    static Node forRequest(AppData.NocRequest request) { return Ui.secondary("Sharing & public preview",()->open("NOC",request.id(),request.type(),request.type(),request.status().name(),List.of())); }
    private record Audience(String key,String label){@Override public String toString(){return label;}}
    private static final List<Audience> AUDIENCES=List.of(new Audience("PRIVATE","Private · no publication"),new Audience("COMMUNITY","Certiflow community only"),new Audience("LINK","Anyone with a public web link"),new Audience("BOTH","Community and public web link"));

    private static void open(String kind,String id,String initialTitle,String category,String nocStatus,List<AppData.PortfolioItem> attachments) {
        Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Share your work");if(App.window()!=null)dialog.initOwner(App.window());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.setResizable(true);
        TextField author=new TextField(App.user().name()),title=new TextField(initialTitle),link=new TextField();
        author.setId("share-author");title.setId("share-title");link.setId("share-link");link.setPromptText("https://… (optional)");
        TextArea summary=new TextArea();summary.setId("share-summary");summary.setWrapText(true);summary.setPrefRowCount(3);summary.setPromptText("Write only what you want viewers to see. Private details are not copied here.");
        ComboBox<Audience> audience=new ComboBox<>();audience.getItems().addAll(AUDIENCES);audience.setValue(AUDIENCES.get(0));audience.setMaxWidth(Double.MAX_VALUE);audience.setId("share-audience");
        Map<String,CheckBox> selected=new LinkedHashMap<>();VBox fileChoices=new VBox(7);
        // Attachments start unchecked: publishing an entry must not silently expose its files.
        for(AppData.PortfolioItem file:attachments){CheckBox choice=new CheckBox(file.fileName());choice.setWrapText(true);choice.setMinWidth(0);choice.setMaxWidth(Double.MAX_VALUE);selected.put(file.id(),choice);fileChoices.getChildren().add(choice);}
        Label status=AccountView.feedback("share-feedback");VBox preview=Ui.card();preview.setId("share-preview");
        PublicSharing.PublishedItem[] saved={null};
        Button publish=Ui.button("Save sharing choice",()->{});publish.setId("share-save");
        Button copy=Ui.secondary("Copy public link",()->{if(saved[0]!=null){ClipboardContent clip=new ClipboardContent();clip.putString(PublicSharing.publicUrl(saved[0]));Clipboard.getSystemClipboard().setContent(clip);Ui.feedback(status,"Public link copied.",true);}});copy.setId("share-copy");
        Button open=Ui.secondary("Open public page",()->{if(saved[0]!=null)App.openWebLink(PublicSharing.publicUrl(saved[0]));});open.setId("share-open");
        VBox content=new VBox(12,Ui.title("Choose who can see this item"),Ui.muted("Your original item stays protected. Only the preview below and selected files are published. Anyone who can view a file can save a copy."),
            Ui.text("Audience"),audience,Ui.text("Public display name"),author,Ui.text("Public title"),title,Ui.text("Public summary · up to 2,000 characters"),summary,Ui.text("Public web link (optional)"),link);
        content.setMinWidth(0);content.setMaxWidth(Double.MAX_VALUE);
        if(kind.equals("NOC"))content.getChildren().add(Ui.muted("Your application reason, staff notes, email, institution ID and assigned teacher are excluded. Only the application type and its current server-recorded status are added."));
        if(!attachments.isEmpty())content.getChildren().addAll(Ui.text("Include these files · choose up to 30"),fileChoices);
        else content.getChildren().add(Ui.muted("No supporting files selected. Files are never published automatically."));
        content.getChildren().addAll(Ui.title("What viewers will see"),preview,status,publish,Ui.grid(2,160,copy,open));
        Runnable updatePreview=()->{
            Audience choice=audience.getValue();boolean privateItem=choice==null||choice.key().equals("PRIVATE");
            String currentStatus=saved[0]!=null&&!saved[0].status().isBlank()?saved[0].status():nocStatus;
            preview.getChildren().setAll(Ui.title(title.getText().isBlank()?"Untitled publication":title.getText()),Ui.muted("By "+author.getText()),Ui.muted(category+(kind.equals("NOC")?" · "+currentStatus:" · Student-provided work")),Ui.text(summary.getText().isBlank()?"No public summary":summary.getText()));
            if(!link.getText().isBlank())preview.getChildren().add(Ui.text(link.getText()));
            selected.forEach((fileId,box)->{if(box.isSelected())preview.getChildren().add(Ui.muted("Included file: "+box.getText()));});
            preview.getChildren().add(Ui.muted(privateItem?"This preview will not be published.":"Audience: "+choice.label()));
            // Link actions reflect the saved publication, not an unsaved audience selection.
            boolean publicLink=saved[0]!=null&&List.of("LINK","BOTH").contains(saved[0].audience());copy.setDisable(!publicLink);open.setDisable(!publicLink);
            publish.setText(privateItem?"Keep private / stop sharing":"Publish this preview");
        };
        for(TextInputControl input:List.of(author,title,link,summary)){input.setMinWidth(0);input.textProperty().addListener((o,b,a)->updatePreview.run());}
        audience.valueProperty().addListener((o,b,a)->updatePreview.run());selected.values().forEach(box->box.selectedProperty().addListener((o,b,a)->updatePreview.run()));
        publish.setOnAction(event->{
            // Snapshot controls on the JavaFX thread before the background network operation.
            Audience target=audience.getValue();boolean privateItem=target==null||target.key().equals("PRIVATE");
            String publicName=author.getText(),publicTitle=title.getText(),publicSummary=summary.getText(),publicLink=link.getText();
            List<String> files=selected.entrySet().stream().filter(e->e.getValue().isSelected()).map(Map.Entry::getKey).toList();
            String audienceKey=privateItem?"PRIVATE":target.key();
            Background.run(content,()->{if(privateItem){PublicSharing.unpublish(kind,id);return null;}return PublicSharing.publish(kind,id,publicTitle,publicSummary,publicName,publicLink,audienceKey,files);},result->{saved[0]=result;updatePreview.run();Ui.feedback(status,result==null?"Publication removed. Previous public links no longer work. Copies already downloaded cannot be recalled.":"Published exactly the preview and selected files. Your private source details remain protected.",true);},error->Ui.feedback(status,error.getMessage(),false));
        });
        ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setMinWidth(0);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(Math.min(650,Math.max(270,App.window().getWidth()-80)));scroll.setPrefViewportHeight(Math.min(650,Math.max(300,App.window().getHeight()-140)));
        dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().getStylesheets().add(App.class.getResource("/style.css").toExternalForm());UiSettings.decorate(dialog.getDialogPane());dialog.getDialogPane().getStyleClass().add("app-page");
        updatePreview.run();dialog.show();
        // If the saved choice cannot be loaded, block writes instead of overwriting it blindly.
        Background.run(content,()->PublicSharing.getOwn(kind,id),item->{saved[0]=item;if(item!=null){author.setText(item.displayName());title.setText(item.title());summary.setText(item.summary());link.setText(item.link());audience.setValue(AUDIENCES.stream().filter(a->a.key().equals(item.audience())).findFirst().orElse(AUDIENCES.get(0)));Set<String> existing=new HashSet<>(item.files().stream().map(PublicSharing.FileItem::id).toList());selected.forEach((fileId,box)->box.setSelected(existing.contains(fileId)));}updatePreview.run();},error->{publish.setDisable(true);Ui.feedback(status,"Could not load the current sharing choice. Close and reopen this panel before changing it. "+error.getMessage(),false);});
    }
}
