package com.certiflow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;

/** Participant-only chat with a persistent inbox, history and editable sent messages. */
public final class MessagesView {
    private static final int PAGE_SIZE = 50;
    private final VBox view;
    private final AppData data;
    private final String ownerId;
    private final ComboBox<AppData.Contact> recipient = new ComboBox<>();
    private final TextField search = new TextField();
    private final VBox inbox = new VBox(6), messages = new VBox(12);
    private final ScrollPane transcript = new ScrollPane(messages);
    private final TextArea composer = new TextArea();
    private final Label feedback = AccountView.feedback("messages-feedback");
    private final Label heading = Ui.title("Choose a conversation");
    private final Label subtitle = Ui.muted("Your private conversations stay here.");
    private final Label unread = Ui.muted("Loading conversations…");
    private final Label count = Ui.muted("0 / 4,000 characters");
    private final Label editNotice = Ui.text("Editing your message");
    private final Label historyStatus = Ui.muted("");
    private final Button refresh = Ui.secondary("Refresh", () -> load(true));
    private final Button send = Ui.button("Send message", this::send);
    private final Button older = Ui.secondary("Load older messages", this::loadOlder);
    private final Button cancelEdit = Ui.secondary("Cancel edit", this::cancelEdit);
    private final Button backToInbox = Ui.secondary("‹ Conversations", this::showInbox);
    private final Button openSettings = Ui.secondary("Settings", () -> showSettings(true));
    // Drafts belong to this open page only; private text is never persisted in preferences.
    private final Map<String, String> drafts = new HashMap<>();
    private final Map<String, EditDraft> editDrafts = new HashMap<>();
    private final List<AppData.Contact> contacts;
    private final GridPane workspace = new GridPane();
    private final StackPane settingsOverlay = new StackPane();
    private TitledPane settingsPanel;
    private final VBox people, conversation;
    private final Timeline poll;
    private List<AppData.Conversation> conversations = List.of();
    private List<AppData.ChatMessage> displayed = List.of();
    private Set<String> remotePageIds = Set.of();
    private ChatSettings settings;
    private AppData.ChatMessage editing;
    private String selectedId, selectedName = "";
    // Reject late responses after switching contacts or changing a message locally.
    private long selectionVersion, mutationVersion;
    private boolean loading, loadingOlder, sending, filtering, moreOlder, historyLoaded, narrow, showMobileInbox = true;
    private boolean selectedCanSend;

    public MessagesView() { this(null); }

    public MessagesView(String contactId) {
        data = App.data(); ownerId = App.user().id();
        contacts = List.copyOf(data.contacts());
        settings = ChatSettings.load(data.dataDirectory(), ownerId);
        search.setId("messages-search"); search.setPromptText("Search conversations or people"); search.setMinWidth(0);
        search.setAccessibleText("Search conversations and people");
        recipient.setId("messages-recipient"); recipient.setPromptText("Start a new chat"); recipient.setMinWidth(0);
        recipient.setMaxWidth(Double.MAX_VALUE); recipient.setEditable(false);
        recipient.setConverter(new StringConverter<>() {
            @Override public String toString(AppData.Contact contact) {
                return contact == null ? "" : contact.name() + " · " + Ui.roleName(contact.role());
            }
            @Override public AppData.Contact fromString(String text) { return null; }
        });
        recipient.getItems().setAll(contacts);
        recipient.valueProperty().addListener((observable, before, after) -> {
            if (!filtering && !sending) select(after == null ? null : after.id(),
                after == null ? "" : after.name(), after != null);
        });
        search.textProperty().addListener((observable, before, after) -> filterPeople());
        inbox.setId("messages-inbox"); inbox.setMinWidth(0);
        ScrollPane inboxScroll = new ScrollPane(inbox);
        inboxScroll.setId("messages-inbox-scroll"); inboxScroll.setFitToWidth(true);
        inboxScroll.setMinSize(0, 80); inboxScroll.setPrefHeight(240); inboxScroll.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(inboxScroll, Priority.ALWAYS);
        inboxScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        inboxScroll.getStyleClass().add("dashboard-scroll-pane");
        people = Ui.card(Ui.section("Conversations", "Pick up where you left off."), search, unread,
            inboxScroll, Ui.text("New conversation"), recipient,
            Ui.muted("Only you and the other participant can read your messages."));
        people.getStyleClass().add("chat-people");
        people.setMinHeight(0); people.setMaxHeight(Double.MAX_VALUE); people.setSpacing(10);
        if (contacts.isEmpty()) people.getChildren().add(Ui.muted("No new contacts are available. Existing conversations remain below."));
        messages.setId("messages-list"); messages.setMinWidth(0); messages.getStyleClass().add("chat-transcript");
        transcript.setId("messages-transcript"); transcript.setFitToWidth(true);
        transcript.setMinSize(0, 90); transcript.setPrefHeight(240); transcript.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(transcript, Priority.ALWAYS);
        transcript.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); transcript.getStyleClass().add("dashboard-scroll-pane");
        composer.setId("message-body"); composer.setPromptText("Write a message…");
        composer.setAccessibleText("Message text"); composer.setWrapText(true); composer.setMinWidth(0); composer.setPrefRowCount(2);
        composer.setMinHeight(65); composer.setPrefHeight(74); composer.setMaxHeight(90);
        composer.textProperty().addListener((observable, before, after) -> {
            count.setText(after.length() + " / 4,000 characters"); updateControls();
        });
        composer.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()
                && (settings.enterToSend() || event.isControlDown() || event.isMetaDown())) {
                event.consume();
                if (!send.isDisabled()) send();
            }
            if (event.getCode() == KeyCode.ESCAPE && editing != null && !sending) { event.consume(); cancelEdit(); }
        });
        refresh.setId("messages-refresh"); send.setId("message-send");
        older.setId("messages-load-older"); cancelEdit.setId("message-edit-cancel"); backToInbox.setId("messages-back-to-inbox");
        openSettings.setId("chat-settings-open"); openSettings.setAccessibleText("Chat settings");
        openSettings.setText("⋯"); openSettings.setTooltip(new Tooltip("Chat settings"));
        refresh.setText("↻"); refresh.setAccessibleText("Refresh conversation"); refresh.setTooltip(new Tooltip("Refresh conversation"));
        for (Button button : List.of(refresh, openSettings)) { button.setMinWidth(44); button.setPrefWidth(44); button.setMaxWidth(44); }
        backToInbox.setText("‹ Chats"); backToInbox.setPrefWidth(82); backToInbox.setMaxWidth(82);
        older.setText("Older"); older.setTooltip(new Tooltip("Load older messages")); older.setPrefWidth(72); older.setMaxWidth(72);
        historyStatus.setId("messages-history-status");
        historyStatus.setWrapText(false); historyStatus.getStyleClass().add("chat-history-status");
        editNotice.setId("message-edit-notice"); editNotice.getStyleClass().add("chat-edit-notice");
        VBox title = new VBox(4, heading, subtitle); title.setMinWidth(0);
        cancelEdit.setText("Cancel"); cancelEdit.setMinHeight(32); cancelEdit.setPrefHeight(32); cancelEdit.setMaxWidth(74);
        HBox editBar = new HBox(8, editNotice, cancelEdit); editBar.setAlignment(Pos.CENTER_LEFT); editBar.setMinWidth(0);
        HBox.setHgrow(editNotice, Priority.ALWAYS);
        editBar.setId("message-edit-bar"); editBar.visibleProperty().bind(editNotice.visibleProperty());
        editBar.managedProperty().bind(editNotice.managedProperty());
        Region toolbarSpace = new Region(); HBox.setHgrow(toolbarSpace, Priority.ALWAYS);
        HBox toolbar = new HBox(6, backToInbox, older, toolbarSpace, refresh, openSettings);
        toolbar.setMinWidth(0); toolbar.setAlignment(Pos.CENTER_LEFT);
        Region footerSpace = new Region(); HBox.setHgrow(footerSpace, Priority.ALWAYS);
        send.setPrefWidth(122); send.setMaxWidth(122);
        HBox footer = new HBox(8, count, footerSpace, send); footer.setAlignment(Pos.CENTER_LEFT); footer.setMinWidth(0);
        conversation = Ui.card(toolbar, title, historyStatus, transcript, editBar, composer, feedback, footer);
        conversation.getStyleClass().add("chat-conversation");
        conversation.setMinHeight(0); conversation.setMaxHeight(Double.MAX_VALUE); conversation.setSpacing(8);
        workspace.setId("chat-workspace"); workspace.getStyleClass().add("chat-workspace");
        workspace.setMinSize(0, 0); workspace.setPrefHeight(0); workspace.setMaxHeight(Double.MAX_VALUE); workspace.setHgap(18); workspace.setVgap(16);
        RowConstraints row = new RowConstraints(); row.setPercentHeight(100); row.setMinHeight(0); row.setVgrow(Priority.ALWAYS);
        workspace.getRowConstraints().add(row);
        workspace.add(people, 0, 0); workspace.add(conversation, 1, 0);
        GridPane.setHgrow(conversation, Priority.ALWAYS);
        GridPane.setValignment(people, javafx.geometry.VPos.TOP);
        GridPane.setValignment(conversation, javafx.geometry.VPos.TOP);
        GridPane.setFillHeight(people, true); GridPane.setFillHeight(conversation, true);
        settingsPanel = settingsPane();
        Button closeSettings = Ui.secondary("Close settings", () -> showSettings(false)); closeSettings.setId("chat-settings-close");
        ScrollPane settingsScroll = new ScrollPane(settingsPanel); settingsScroll.setFitToWidth(true); settingsScroll.setMinSize(0, 0);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); settingsScroll.getStyleClass().add("dashboard-scroll-pane");
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);
        VBox settingsCard = Ui.card(closeSettings, settingsScroll); settingsCard.setMinSize(0, 0); settingsCard.setMaxSize(480, 480);
        settingsOverlay.getChildren().add(settingsCard); settingsOverlay.getStyleClass().add("chat-settings-overlay");
        settingsOverlay.setVisible(false); settingsOverlay.setManaged(false); settingsOverlay.setMinSize(0, 0);
        settingsPanel.expandedProperty().addListener((observable, before, after) -> {
            settingsOverlay.setVisible(after); settingsOverlay.setManaged(after);
        });
        StackPane chatArea = new StackPane(workspace, settingsOverlay); chatArea.setMinSize(0, 0); chatArea.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        view = Ui.page("Messages", "Messages", chatArea); view.getStyleClass().add("chat-page");
        view.getStylesheets().add(MessagesView.class.getResource("/chat.css").toExternalForm());
        fitChatPage(chatArea);
        workspace.widthProperty().addListener((observable, before, after) -> adapt());
        view.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && settingsOverlay.isVisible()) { showSettings(false); event.consume(); }
        });
        poll = new Timeline(new KeyFrame(Duration.seconds(8), event -> pollTick()));
        poll.setCycleCount(Timeline.INDEFINITE);
        view.sceneProperty().addListener((observable, before, after) -> {
            if (after == null) { poll.stop(); rememberDraft(); }
            else Platform.runLater(() -> { if (active()) { poll.play(); load(false); } });
        });
        applySettings(); updateControls(); renderInbox(); renderMessages(false, false, null); adapt();
        if (contactId != null) {
            AppData.Contact contact = contacts.stream().filter(c -> c.id().equals(contactId)).findFirst().orElse(null);
            select(contactId, contact == null ? "Conversation" : contact.name(), contact != null);
            selectCombo(contactId);
        }
    }

    /** Chat uses the viewport as a workspace instead of adding a second page scroll. */
    private void fitChatPage(StackPane chatArea) {
        ScrollPane pageScroll = (ScrollPane) view.lookup("#page-scroll");
        // ScrollPane content is not reachable through CSS lookup until its skin exists.
        VBox body = (VBox) chatArea.getParent();
        VBox wrapper = (VBox) pageScroll.getContent();
        VBox headingBox = (VBox) body.getChildren().get(0);
        HBox pageActions = (HBox) headingBox.getChildren().get(0);
        Label pageTitle = (Label) headingBox.getChildren().get(1);
        headingBox.getChildren().remove(pageTitle);
        pageActions.getChildren().remove(1);
        pageActions.getChildren().add(1, pageTitle); HBox.setHgrow(pageTitle, Priority.ALWAYS);
        pageActions.setSpacing(8); pageTitle.setMinWidth(0);
        Button pageRefresh = (Button) pageActions.getChildren().get(2);
        pageRefresh.setOnAction(event -> load(true));
        pageScroll.setFitToHeight(true); pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        wrapper.setMinHeight(0); body.setMinHeight(0); body.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(body, Priority.ALWAYS); VBox.setVgrow(chatArea, Priority.ALWAYS);
        Runnable compactPage = () -> {
            body.setSpacing(12);
            body.setPadding(new javafx.geometry.Insets(12, view.getWidth() < 600 ? 12 : 26, 14, view.getWidth() < 600 ? 12 : 26));
            pageTitle.setVisible(view.getWidth() >= 360); pageTitle.setManaged(pageTitle.isVisible());
        };
        view.widthProperty().addListener((observable, before, after) -> compactPage.run());
        compactPage.run();
    }

    private void showSettings(boolean open) {
        if (settingsPanel == null) return;
        settingsPanel.setExpanded(open);
        if (open) settingsOverlay.toFront();
    }

    private TitledPane settingsPane() {
        CheckBox enter = preference("Enter sends a message; Shift+Enter starts a new line", "chat-enter-to-send", settings.enterToSend());
        CheckBox compact = preference("Compact message spacing", "chat-compact", settings.compact());
        CheckBox large = preference("Larger message text", "chat-large-text", settings.largeText());
        CheckBox previews = preference("Show message previews in the conversation list", "chat-show-previews", settings.showPreviews());
        CheckBox scroll = preference("Follow new messages when I am already at the bottom", "chat-auto-scroll", settings.autoScroll());
        Runnable changed = () -> {
            settings = new ChatSettings(enter.isSelected(), compact.isSelected(), large.isSelected(), previews.isSelected(), scroll.isSelected());
            applySettings(); renderInbox(); renderMessages(false, true, null);
            ChatSettings saved = settings;
            Background.execute(() -> {
                try { saved.save(data.dataDirectory(), ownerId); }
                catch (Exception error) { Platform.runLater(() -> { if (active()) Ui.feedback(feedback, "Settings changed for now, but could not be saved: " + error.getMessage(), false); }); }
            });
        };
        for (CheckBox option : List.of(enter, compact, large, previews, scroll)) option.setOnAction(event -> changed.run());
        VBox options = new VBox(12, enter, compact, large, previews, scroll,
            Ui.muted("Saved for this account on this device. Ctrl+Enter always sends. Drafts stay in memory only."));
        options.setMinWidth(0);
        TitledPane pane = new TitledPane("Chat settings", options); pane.setId("chat-settings");
        pane.setExpanded(false); pane.setAnimated(false); pane.setMinWidth(0); pane.getStyleClass().add("chat-settings");
        return pane;
    }

    private static CheckBox preference(String text, String id, boolean value) {
        CheckBox box = new CheckBox(text); box.setId(id); box.setSelected(value);
        box.setMinWidth(0); box.setMaxWidth(Double.MAX_VALUE); box.setWrapText(true); return box;
    }

    private void applySettings() {
        workspace.getStyleClass().removeAll("chat-compact", "chat-large-text");
        if (settings.compact()) workspace.getStyleClass().add("chat-compact");
        if (settings.largeText()) workspace.getStyleClass().add("chat-large-text");
        messages.setSpacing(settings.compact() ? 7 : 12);
    }

    private void pollTick() { if (active()) load(false); else poll.stop(); }

    private void adapt() {
        narrow = workspace.getWidth() < 740;
        workspace.getColumnConstraints().clear();
        ColumnConstraints left = new ColumnConstraints(); left.setMinWidth(0);
        if (narrow) {
            left.setPercentWidth(100); workspace.getColumnConstraints().add(left);
            GridPane.setColumnIndex(conversation, 0);
            people.setVisible(showMobileInbox || selectedId == null); people.setManaged(people.isVisible());
            conversation.setVisible(!people.isVisible()); conversation.setManaged(conversation.isVisible());
        } else {
            left.setMinWidth(245); left.setPrefWidth(290); left.setMaxWidth(310);
            ColumnConstraints right = new ColumnConstraints(); right.setMinWidth(0); right.setHgrow(Priority.ALWAYS);
            workspace.getColumnConstraints().addAll(left, right);
            GridPane.setColumnIndex(conversation, 1);
            people.setVisible(true); people.setManaged(true); conversation.setVisible(true); conversation.setManaged(true);
        }
        backToInbox.setVisible(narrow); backToInbox.setManaged(narrow);
    }

    private void showInbox() {
        if (sending) return;
        rememberDraft(); showMobileInbox = true; adapt();
    }

    private void filterPeople() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        AppData.Contact chosen = recipient.getValue();
        List<AppData.Contact> matches = new ArrayList<>(contacts.stream().filter(c ->
            (c.name() + " " + c.department() + " " + c.role()).toLowerCase(Locale.ROOT).contains(query)).toList());
        if (chosen != null && !matches.contains(chosen)) matches.add(chosen);
        filtering = true;
        try { recipient.getItems().setAll(matches); recipient.setValue(chosen); }
        finally { filtering = false; }
        renderInbox();
    }

    private void renderInbox() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        inbox.getChildren().clear();
        List<AppData.Conversation> matches = conversations.stream().filter(c ->
            (c.name() + " " + c.department() + (settings.showPreviews() ? " " + c.lastMessage() : "")).toLowerCase(Locale.ROOT).contains(query)).toList();
        for (AppData.Conversation item : matches) {
            Label avatar = Ui.text(initials(item.name())); avatar.getStyleClass().add("chat-avatar");
            avatar.setMinSize(34, 34); avatar.setPrefSize(34, 34); avatar.setMaxSize(34, 34);
            Label name = Ui.text(item.name()); name.setStyle("-fx-font-weight: bold;");
            Label preview = Ui.muted(settings.showPreviews() ? preview(item.lastMessage()) : "Open conversation");
            preview.getStyleClass().add("chat-preview");
            VBox copy = new VBox(3, name, preview, Ui.muted(shortDate(item.lastSentAt())));
            copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
            HBox line = new HBox(10, avatar, copy); line.setAlignment(Pos.CENTER_LEFT); line.setMinWidth(0);
            if (item.unreadCount() > 0) {
                Label badge = new Label(item.unreadCount() > 99 ? "99+" : Integer.toString(item.unreadCount()));
                badge.getStyleClass().add("chat-unread"); badge.setMinWidth(Region.USE_PREF_SIZE); line.getChildren().add(badge);
            }
            Button open = new Button(); open.setId("conversation-" + item.contactId()); open.setGraphic(line);
            open.setContentDisplay(ContentDisplay.GRAPHIC_ONLY); open.setMinWidth(0); open.setMaxWidth(Double.MAX_VALUE);
            open.setAccessibleText(item.name() + ", " + item.unreadCount() + " unread messages");
            open.getStyleClass().add("chat-inbox-item");
            if (Objects.equals(selectedId, item.contactId())) open.getStyleClass().add("chat-selected");
            open.setOnAction(event -> { if (!sending) { select(item.contactId(), item.name(), item.canSend()); selectCombo(item.contactId()); } });
            open.setDisable(sending); inbox.getChildren().add(open);
        }
        if (matches.isEmpty()) inbox.getChildren().add(Ui.muted(conversations.isEmpty()
            ? "No conversations yet. Choose a person below to start one." : "No conversations match your search."));
        int total = conversations.stream().mapToInt(AppData.Conversation::unreadCount).sum();
        unread.setText(total == 0 ? conversations.size() + " conversation" + (conversations.size() == 1 ? "" : "s")
            : total + " unread message" + (total == 1 ? "" : "s"));
    }

    private static String initials(String name) {
        String[] pieces = name.strip().split("\\s+");
        if (pieces.length == 0 || pieces[0].isEmpty()) return "•";
        return (pieces[0].substring(0, 1) + (pieces.length > 1 ? pieces[pieces.length - 1].substring(0, 1) : "")).toUpperCase(Locale.ROOT);
    }
    private static String preview(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return value.length() > 68 ? value.substring(0, 65) + "…" : value;
    }
    private static String shortDate(java.time.LocalDateTime date) {
        if (date == null) return "";
        return date.toLocalDate().equals(LocalDate.now()) ? date.format(DateTimeFormatter.ofPattern("HH:mm"))
            : date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH));
    }

    private void selectCombo(String id) {
        filtering = true;
        try { recipient.setValue(contacts.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null)); }
        finally { filtering = false; }
    }

    private void rememberDraft() {
        if (selectedId == null) return;
        if (editing == null) drafts.put(selectedId, composer.getText());
        else editDrafts.put(selectedId, new EditDraft(editing, composer.getText()));
    }

    private void select(String id, String name, boolean canSend) {
        if (sending) return;
        if (Objects.equals(id, selectedId) && id != null) { showMobileInbox = false; adapt(); load(false); return; }
        rememberDraft();
        selectedId = id; selectedName = name; selectedCanSend = canSend; selectionVersion++;
        displayed = List.of(); remotePageIds = Set.of(); moreOlder = false; historyLoaded = false; editing = null;
        EditDraft pending = id == null ? null : editDrafts.get(id);
        if (pending != null) editing = pending.original();
        composer.setText(id == null ? "" : pending == null ? drafts.getOrDefault(id, "") : pending.body());
        heading.setText(id == null ? "Choose a conversation" : name);
        subtitle.setText(id == null ? "Your private conversations stay here." : canSend ? "Private conversation · messages update automatically" : "Past conversation · new messages are unavailable for these account roles");
        showMobileInbox = id == null; adapt(); updateControls(); renderInbox();
        messages.getChildren().setAll(Ui.muted(id == null ? "Choose a conversation or start a new one." : "Loading your messages…"));
        feedback.setVisible(false); feedback.setManaged(false);
        load(false);
    }

    private boolean active() {
        return view != null && view.getScene() != null && view.getScene().getRoot() == view
            && App.window() != null && App.window().getScene() == view.getScene()
            && data.currentUser() != null && ownerId.equals(data.currentUser().id());
    }

    /** State callbacks must finish even when the same view temporarily changes scenes. */
    private <T> void run(javafx.scene.Node busy, java.util.concurrent.Callable<T> work,
            java.util.function.Consumer<T> success, java.util.function.Consumer<Exception> failure) {
        busy.setDisable(true);
        Background.execute(() -> {
            T result = null; Exception problem = null;
            try {
                if (data.currentUser() == null || !ownerId.equals(data.currentUser().id()))
                    throw new IllegalStateException("Please sign in again.");
                result = work.call();
            } catch (Exception error) { problem = error; }
            T value = result; Exception error = problem;
            Platform.runLater(() -> {
                busy.setDisable(false);
                if (error == null) success.accept(value); else failure.accept(error);
            });
        });
    }

    private record Loaded(List<AppData.Conversation> inbox, List<AppData.ChatMessage> rows, boolean more) {}
    private record EditDraft(AppData.ChatMessage original, String body) {}
    private record Anchor(double height, double offset) {}

    // Poll only the newest page; a manual refresh also revisits already loaded history.
    private void load(boolean manual) {
        if (!active() || loading || loadingOlder || sending) return;
        String target = selectedId; long version = selectionVersion, mutation = mutationVersion;
        int pageCount = manual ? Math.max(1, (displayed.size() + PAGE_SIZE - 1) / PAGE_SIZE) : 1;
        loading = true;
        if (manual) Ui.feedback(feedback, "Refreshing conversations…", true);
        run(refresh, () -> {
            List<AppData.Conversation> nextInbox = data.conversations();
            List<AppData.ChatMessage> rows = new ArrayList<>(); boolean hasMore = false;
            String beforeId = "";
            if (target != null) for (int page = 0; page < pageCount; page++) {
                List<AppData.ChatMessage> part = data.conversationPage(target, beforeId, PAGE_SIZE);
                rows.addAll(part); hasMore = part.size() == PAGE_SIZE;
                if (!hasMore) break;
                beforeId = part.get(0).id();
            }
            return new Loaded(nextInbox, rows, hasMore);
        }, result -> {
            loading = false;
            if (!active()) return;
            conversations = List.copyOf(result.inbox()); renderInbox();
            if (version != selectionVersion || mutation != mutationVersion || sending) { load(false); return; }
            if (target != null) {
                conversations.stream().filter(c -> c.contactId().equals(target)).findFirst().ifPresent(c -> {
                    selectedName = c.name(); selectedCanSend = c.canSend(); heading.setText(c.name());
                    subtitle.setText(c.canSend() ? "Private conversation · messages update automatically" : "Past conversation · new messages are unavailable for these account roles");
                });
                boolean first = !historyLoaded;
                // A non-overlapping newest page means more than one page arrived between polls.
                // Reset the window rather than presenting a transcript with an invisible gap.
                boolean gap = !remotePageIds.isEmpty() && !result.rows().isEmpty()
                    && result.rows().stream().noneMatch(m -> remotePageIds.contains(m.id()));
                List<AppData.ChatMessage> previous = displayed;
                if (gap) displayed = List.of();
                if (first || manual || gap) moreOlder = result.more();
                remotePageIds = result.rows().stream().map(AppData.ChatMessage::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
                merge(result.rows()); historyLoaded = true;
                if (first || gap || !displayed.equals(previous)) renderMessages(first || gap, false, null);
                updateControls();
                if (gap) Ui.feedback(feedback, "Showing the latest messages. Use Load older to continue through the earlier history.", true);
                if (!narrow || !showMobileInbox) markRead(target, version);
            }
            if (manual) Ui.feedback(feedback, "Conversation refreshed.", true);
        }, error -> {
            loading = false;
            if (!active()) return;
            if (version != selectionVersion) { load(false); return; }
            Ui.feedback(feedback, error.getMessage(), false);
            if (target != null && displayed.isEmpty()) messages.getChildren().setAll(Ui.muted("Could not load messages. Use Refresh to try again."));
            updateControls();
        });
    }

    private void markRead(String target, long version) {
        boolean unreadRows = displayed.stream().anyMatch(m -> m.recipientId().equals(ownerId) && m.readAt() == null);
        if (!unreadRows) return;
        Background.execute(() -> {
            try {
                if (data.currentUser() == null || !ownerId.equals(data.currentUser().id())) return;
                data.markMessagesRead(target);
                Platform.runLater(() -> {
                    if (!active() || version != selectionVersion) return;
                    conversations = conversations.stream().map(c -> c.contactId().equals(target)
                        ? new AppData.Conversation(c.contactId(), c.name(), c.role(), c.department(), c.lastMessage(), c.lastSentAt(), 0, c.canSend()) : c).toList();
                    renderInbox();
                });
            } catch (Exception ignored) { /* The next visible refresh retries receipts. */ }
        });
    }

    private void loadOlder() {
        if (!active() || selectedId == null || displayed.isEmpty() || loading || loadingOlder || sending || !moreOlder) return;
        String target = selectedId, before = displayed.get(0).id(); long version = selectionVersion;
        Anchor anchor = anchor(); loadingOlder = true; updateControls();
        run(older, () -> data.conversationPage(target, before, PAGE_SIZE), rows -> {
            loadingOlder = false;
            if (!active() || version != selectionVersion) { load(false); return; }
            moreOlder = rows.size() == PAGE_SIZE; merge(rows); renderMessages(false, false, anchor); updateControls();
            if (rows.isEmpty()) Ui.feedback(feedback, "You have reached the beginning of this conversation.", true);
        }, error -> {
            loadingOlder = false;
            if (active() && version == selectionVersion) Ui.feedback(feedback, error.getMessage(), false);
            updateControls();
        });
    }

    private void merge(List<AppData.ChatMessage> rows) {
        Map<String, AppData.ChatMessage> merged = new LinkedHashMap<>();
        displayed.forEach(m -> merged.put(m.id(), m)); rows.forEach(m -> merged.put(m.id(), m));
        displayed = merged.values().stream().sorted(Comparator.comparing(AppData.ChatMessage::sentAt)
            .thenComparing(AppData.ChatMessage::id)).toList();
    }

    private Anchor anchor() {
        double height = messages.getHeight(), range = Math.max(0, height - transcript.getViewportBounds().getHeight());
        return new Anchor(height, transcript.getVvalue() * range);
    }

    private void renderMessages(boolean forceBottom, boolean keepPosition, Anchor olderAnchor) {
        long version = selectionVersion;
        Anchor position = olderAnchor == null ? anchor() : olderAnchor;
        boolean follow = forceBottom || (!keepPosition && olderAnchor == null && settings.autoScroll() && transcript.getVvalue() >= 0.92);
        messages.getChildren().clear();
        if (displayed.isEmpty()) messages.getChildren().add(Ui.muted(selectedId == null
            ? "Choose a conversation or start a new one." : "This is the start of your conversation. Say hello below."));
        LocalDate previousDay = null;
        for (AppData.ChatMessage message : displayed) {
            LocalDate day = message.sentAt().toLocalDate();
            if (!day.equals(previousDay)) {
                String title = day.equals(LocalDate.now()) ? "Today" : day.equals(LocalDate.now().minusDays(1)) ? "Yesterday"
                    : day.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH));
                Label date = Ui.text(title); date.getStyleClass().add("chat-day"); date.setMaxWidth(Region.USE_PREF_SIZE);
                HBox divider = new HBox(date); divider.setAlignment(Pos.CENTER); messages.getChildren().add(divider); previousDay = day;
            }
            boolean outgoing = message.senderId().equals(ownerId);
            Label sender = Ui.text(outgoing ? "You" : selectedName); sender.getStyleClass().add("chat-sender");
            Label body = Ui.text(message.body()); body.getStyleClass().add("chat-message-body");
            String meta = message.sentAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                + (message.editedAt() == null ? "" : " · Edited")
                + (outgoing ? message.readAt() == null ? " · Sent" : " · Read" : "");
            Label details = Ui.muted(meta); details.getStyleClass().add("chat-message-meta");
            VBox bubble = Ui.card(sender, body, details); bubble.setMinWidth(0); bubble.setMaxWidth(610);
            bubble.getStyleClass().add("message-bubble");
            if (outgoing) {
                bubble.getStyleClass().add("message-outgoing");
                Button edit = Ui.secondary("Edit", () -> beginEdit(message)); edit.setId("message-edit-" + message.id());
                edit.setMinHeight(26); edit.setPrefHeight(26); edit.setMaxWidth(65); edit.setDisable(sending);
                edit.getStyleClass().add("chat-edit-button"); bubble.getChildren().add(edit);
            }
            HBox row = new HBox(bubble); row.setMinWidth(0); row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messages.getChildren().add(row);
        }
        Platform.runLater(() -> {
            if (!active() || version != selectionVersion) return;
            transcript.applyCss(); transcript.layout(); messages.layout();
            if (follow) transcript.setVvalue(1);
            else {
                double range = Math.max(0, messages.getHeight() - transcript.getViewportBounds().getHeight());
                double offset = position.offset() + (olderAnchor == null ? 0 : messages.getHeight() - position.height());
                transcript.setVvalue(range == 0 ? 0 : Math.max(0, Math.min(1, offset / range)));
            }
        });
    }

    private void beginEdit(AppData.ChatMessage message) {
        if (sending || !message.senderId().equals(ownerId)) return;
        if (editing != null && editing.id().equals(message.id())) { composer.requestFocus(); return; }
        if (editing != null && !editing.id().equals(message.id()) && !composer.getText().equals(editing.body())
            && !Ui.confirm("Discard your unfinished message edit?")) return;
        if (editing == null && selectedId != null) drafts.put(selectedId, composer.getText());
        editing = message; composer.setText(message.body()); updateControls(); composer.requestFocus();
    }

    private void cancelEdit() {
        if (sending || editing == null) return;
        editing = null; if (selectedId != null) editDrafts.remove(selectedId);
        composer.setText(selectedId == null ? "" : drafts.getOrDefault(selectedId, ""));
        updateControls(); composer.requestFocus();
    }

    private void updateControls() {
        boolean valid = selectedId != null && !composer.getText().isBlank() && composer.getText().length() <= 4000;
        send.setDisable(sending || !valid || (editing == null && !selectedCanSend));
        send.setText(editing == null ? "Send message" : "Save edit");
        editNotice.setVisible(editing != null); editNotice.setManaged(editing != null); cancelEdit.setDisable(sending);
        recipient.setDisable(sending); search.setDisable(sending); inbox.setDisable(sending); backToInbox.setDisable(sending);
        inbox.getChildren().stream().filter(node -> node instanceof Button).forEach(node -> node.setDisable(sending));
        messages.lookupAll(".chat-edit-button").forEach(node -> node.setDisable(sending));
        composer.setDisable(sending || selectedId == null || (editing == null && !selectedCanSend));
        older.setDisable(sending || loading || loadingOlder || !moreOlder || selectedId == null);
        older.setVisible(selectedId != null && (moreOlder || loadingOlder)); older.setManaged(older.isVisible());
        historyStatus.setText(selectedId == null ? "Select a conversation to view its history."
            : !historyLoaded ? "Loading history…" : moreOlder ? displayed.size() + " messages loaded · older messages are available"
            : displayed.isEmpty() ? "No messages yet" : "Beginning of conversation · " + displayed.size() + " messages loaded");
    }

    private void send() {
        if (!active() || sending || selectedId == null) return;
        String target = selectedId, body = composer.getText(); AppData.ChatMessage original = editing;
        if (body.isBlank() || body.length() > 4000) { Ui.feedback(feedback, "Write a message between 1 and 4,000 characters.", false); return; }
        if (original == null && !selectedCanSend) { Ui.feedback(feedback, "New messages are unavailable for this conversation.", false); return; }
        sending = true; mutationVersion++; updateControls();
        Ui.feedback(feedback, original == null ? "Sending…" : "Saving your edit…", true);
        run(send, () -> original == null ? data.sendMessage(target, body) : data.editMessage(original, body), saved -> {
            sending = false;
            merge(List.of(saved));
            if (original == null) { drafts.remove(target); composer.clear(); }
            else { editing = null; editDrafts.remove(target); composer.setText(drafts.getOrDefault(target, "")); }
            updateControls();
            if (!active()) return;
            renderMessages(original == null, original != null, null);
            Ui.feedback(feedback, original == null ? "Message sent." : "Message updated.", true);
            load(false);
        }, error -> {
            sending = false;
            updateControls();
            if (active()) {
                String problem = error.getMessage();
                if (original != null && problem != null && problem.toLowerCase(Locale.ROOT).contains("changed elsewhere"))
                    problem = "This message changed in another session. Your text is still here. Copy it if needed, cancel this edit, then refresh and edit the latest version.";
                Ui.feedback(feedback, problem, false);
            }
        });
    }

    public VBox getView() { return view; }
}
