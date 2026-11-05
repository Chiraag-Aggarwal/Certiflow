package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ChatDataTest {
    @TempDir Path directory;
    TestBackend backend; AppData data;
    static final String OTHER="00000000-0000-4000-8000-000000000088";
    @BeforeEach void start() throws Exception { backend=new TestBackend();data=new AppData(directory,backend.client()); }
    @AfterEach void stop(){backend.close();}
    private void login() throws Exception { data.authenticate(backend.role,"account0@example.invalid","long-password-test").orElseThrow(); }
    private JsonObject add(int sequence,String sender,String recipient,String body,String time){JsonObject row=TestBackend.messageRow(sequence,sender,recipient,body,time);backend.messages.add(row);return row;}

    @Test void keysetPagesRetainOldMessagesAndDoNotDuplicateWhenNewMessagesArrive() throws Exception {
        login();
        for(int i=1;i<=120;i++)add(i,TestBackend.ID,TestBackend.TEACHER_ID,"Message "+i,"2026-11-05T06:00:00Z");
        var first=data.conversationPage(TestBackend.TEACHER_ID,null,50);
        assertEquals(50,first.size());assertEquals("Message 71",first.get(0).body());assertEquals("Message 120",first.get(49).body());
        assertEquals(Set.of("other_id","before_id","page_size"),backend.lastBody.keySet());assertTrue(backend.lastBody.get("before_id").isJsonNull());
        add(121,TestBackend.TEACHER_ID,TestBackend.ID,"New arrival","2026-11-05T06:10:00Z");
        var second=data.conversationPage(TestBackend.TEACHER_ID,first.get(0).id(),50);
        var third=data.conversationPage(TestBackend.TEACHER_ID,second.get(0).id(),50);
        assertEquals(50,second.size());assertEquals(20,third.size());assertEquals("Message 1",third.get(0).body());
        Set<String> ids=new HashSet<>();for(var page:List.of(first,second,third))for(var message:page)assertTrue(ids.add(message.id()));
        assertEquals(120,ids.size());assertTrue(data.conversationPage(TestBackend.TEACHER_ID,third.get(0).id(),50).isEmpty());
        var latest=data.conversationPage(TestBackend.TEACHER_ID,"",50);assertEquals("New arrival",latest.get(latest.size()-1).body());
    }

    @Test void historyReadsOnlySelectedParticipantPairAndRejectsForeignAnchors() throws Exception {
        login();add(1,TestBackend.ID,TestBackend.TEACHER_ID,"Our message","2026-11-05T06:00:00Z");
        JsonObject foreign=add(2,TestBackend.ID,OTHER,"Another chat","2026-11-05T06:01:00Z");
        add(3,OTHER,TestBackend.TEACHER_ID,"Not our conversation","2026-11-05T06:02:00Z");
        assertEquals(List.of("Our message"),data.conversationPage(TestBackend.TEACHER_ID,null,50).stream().map(AppData.ChatMessage::body).toList());
        assertEquals(List.of("Our message"),data.conversation(TestBackend.TEACHER_ID).stream().map(AppData.ChatMessage::body).toList());
        assertThrows(IOException.class,()->data.conversationPage(TestBackend.TEACHER_ID,foreign.get("id").getAsString(),50));
    }

    @Test void historyInputValidationPreventsMalformedRequests() throws Exception {
        login();backend.calls.clear();
        for(int size:new int[]{0,101})assertThrows(IllegalArgumentException.class,()->data.conversationPage(TestBackend.TEACHER_ID,null,size));
        assertThrows(IllegalArgumentException.class,()->data.conversationPage(TestBackend.ID,null,50));
        assertThrows(IllegalArgumentException.class,()->data.conversationPage("not-an-id",null,50));
        assertThrows(IllegalArgumentException.class,()->data.conversationPage(TestBackend.TEACHER_ID,"not-an-anchor",50));
        assertTrue(backend.calls.isEmpty());data.logout();assertThrows(IllegalStateException.class,()->data.conversationPage(TestBackend.TEACHER_ID,null,50));
    }

    @Test void ownEditsPreserveIdentityTimestampsAndReceiptsAndRejectStaleEditors() throws Exception {
        login();JsonObject row=add(1,TestBackend.ID,TestBackend.TEACHER_ID,"Original text","2026-11-05T06:00:00Z");row.addProperty("read_at","2026-11-05T07:00:00Z");
        var original=data.conversationPage(TestBackend.TEACHER_ID,null,50).get(0);
        var edited=data.editMessage(original,"  Corrected text  ");
        assertEquals(Set.of("message_id","new_body","original_body"),backend.lastBody.keySet());assertEquals("Original text",backend.lastBody.get("original_body").getAsString());
        assertEquals("Corrected text",edited.body());assertNotNull(edited.editedAt());assertEquals(original.id(),edited.id());
        assertEquals(original.senderId(),edited.senderId());assertEquals(original.recipientId(),edited.recipientId());assertEquals(original.sentAt(),edited.sentAt());assertEquals(original.readAt(),edited.readAt());
        assertEquals(edited,data.editMessage(edited,"Corrected text"));
        assertTrue(assertThrows(IOException.class,()->data.editMessage(original,"Stale overwrite")).getMessage().contains("changed elsewhere"));
        assertEquals("Corrected text",data.conversationPage(TestBackend.TEACHER_ID,null,50).get(0).body());
    }

    @Test void receivedMessagesCannotBeEditedEvenWithForgedClientMetadata() throws Exception {
        login();add(1,TestBackend.TEACHER_ID,TestBackend.ID,"Staff message","2026-11-05T06:00:00Z");
        var incoming=data.conversationPage(TestBackend.TEACHER_ID,null,50).get(0);backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->data.editMessage(incoming,"Changed"));assertTrue(backend.calls.isEmpty());
        var forged=new AppData.ChatMessage(incoming.id(),TestBackend.ID,TestBackend.TEACHER_ID,incoming.body(),incoming.sentAt(),incoming.readAt(),incoming.editedAt());
        assertThrows(IOException.class,()->data.editMessage(forged,"Changed"));assertEquals("Staff message",backend.messages.get(0).get("body").getAsString());
    }

    @Test void invalidEditsDoNotReachTheServer() throws Exception {
        login();add(1,TestBackend.ID,TestBackend.TEACHER_ID,"Original","2026-11-05T06:00:00Z");
        var original=data.conversationPage(TestBackend.TEACHER_ID,null,50).get(0);backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->data.editMessage(original," \t\n "));assertThrows(IllegalArgumentException.class,()->data.editMessage(original,"x".repeat(4001)));
        assertThrows(IllegalArgumentException.class,()->data.editMessage(null,"Text"));assertTrue(backend.calls.isEmpty());
    }

    @Test void inboxKeepsHistoricalContactsWhileRespectingCurrentSendEligibility() throws Exception {
        backend.chatContacts.add(TestBackend.contactRow(OTHER,"Former teacher",AppData.Role.STUDENT));login();
        assertTrue(data.contacts().stream().noneMatch(c->c.id().equals(OTHER)));
        add(1,OTHER,TestBackend.ID,"Past reply","2026-11-05T06:00:00Z");
        add(2,TestBackend.ID,TestBackend.TEACHER_ID,"Current conversation","2026-11-05T06:01:00Z");
        var inbox=data.conversations();assertEquals(2,inbox.size());assertEquals(TestBackend.TEACHER_ID,inbox.get(0).contactId());
        var old=inbox.stream().filter(c->c.contactId().equals(OTHER)).findFirst().orElseThrow();assertFalse(old.canSend());assertEquals(1,old.unreadCount());
        assertEquals("Past reply",data.conversationPage(OTHER,null,50).get(0).body());assertThrows(IllegalArgumentException.class,()->data.sendMessage(OTHER,"New message"));
        data.markMessagesRead(OTHER);assertEquals(0,data.conversations().stream().filter(c->c.contactId().equals(OTHER)).findFirst().orElseThrow().unreadCount());
    }

    @Test void inboxRetrievesMoreThanOneServerPageWithoutOtherPeoplesMessages() throws Exception {
        login();
        for(int i=1;i<=501;i++){String id=String.format("30000000-0000-4000-8000-%012d",i);backend.chatContacts.add(TestBackend.contactRow(id,"Contact "+i,AppData.Role.TEACHER));add(i,TestBackend.ID,id,"Discussion "+i,"2026-11-05T06:00:00Z");}
        add(999,OTHER,TestBackend.TEACHER_ID,"Private between others","2026-11-05T06:30:00Z");
        var inbox=data.conversations();assertEquals(501,inbox.size());assertEquals(501,inbox.stream().map(AppData.Conversation::contactId).distinct().count());
        assertTrue(backend.calls.stream().anyMatch(call->call.contains("/rpc/chat_inbox?")&&call.contains("offset=500")));
        assertTrue(inbox.stream().noneMatch(c->c.lastMessage().equals("Private between others")));
    }

    @Test void chatPreferencesPersistPerAccountWithoutMessageContents() throws Exception {
        assertEquals(ChatSettings.defaults(),ChatSettings.load(directory,TestBackend.ID));
        ChatSettings preferred=new ChatSettings(true,true,true,false,false);preferred.save(directory,TestBackend.ID);
        assertEquals(preferred,ChatSettings.load(directory,TestBackend.ID));assertEquals(ChatSettings.defaults(),ChatSettings.load(directory,OTHER));
        Properties properties=new Properties();try(var in=Files.newInputStream(directory.resolve("chat-"+TestBackend.ID+".properties"))){properties.load(in);}
        assertEquals(Set.of("enterToSend","compact","largeText","showPreviews","autoScroll"),properties.stringPropertyNames());
        assertThrows(IllegalArgumentException.class,()->preferred.save(directory,"../../outside"));
    }
}
