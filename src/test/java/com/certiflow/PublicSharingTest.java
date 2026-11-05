package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PublicSharingTest {
    @TempDir Path directory;
    TestBackend backend; AppData data; AppData.User user;
    @BeforeEach void setup() throws Exception {
        backend=new TestBackend();data=new AppData(directory,backend.client());user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        var field=App.class.getDeclaredField("data");field.setAccessible(true);field.set(null,data);
    }
    @AfterEach void cleanup() throws Exception {var field=App.class.getDeclaredField("data");field.setAccessible(true);field.set(null,null);backend.close();}
    private AppData.Project project() throws Exception{return data.createProject(user,"Private project","Project","Private description","https://example.invalid/private",TestBackend.TEACHER_ID);}

    @Test void projectPublicationIncludesOnlyReviewedFieldsAndExplicitlyChosenFiles() throws Exception {
        var project=project();Path first=Files.writeString(directory.resolve("first.txt"),"First document"),second=Files.writeString(directory.resolve("second.txt"),"Private second document");
        var included=data.addPortfolio(user,"Included evidence",first,project.id());data.addPortfolio(user,"Not included",second,project.id());
        assertNull(PublicSharing.getOwn("PROJECT",project.id()));
        var published=PublicSharing.publish("PROJECT",project.id(),"Public title","Public summary","Public name","https://example.invalid/published","BOTH",List.of(included.id(),included.id()));
        assertEquals(Set.of("source_kind","source_id","public_title","public_summary","public_author","public_link","audience","file_ids"),backend.lastBody.keySet());
        assertEquals(1,backend.lastBody.getAsJsonArray("file_ids").size());assertEquals("Public title",published.title());assertEquals("Public summary",published.summary());assertEquals("Public name",published.displayName());
        assertEquals(List.of(included.id()),published.files().stream().map(PublicSharing.FileItem::id).toList());
        assertFalse(backend.lastBody.toString().contains("Private description"));assertFalse(backend.lastBody.toString().contains("account0@example.invalid"));
        assertFalse(backend.lastBody.has("recipient_id"));assertFalse(backend.lastBody.has("student_number"));assertEquals(published,PublicSharing.getOwn("PROJECT",project.id()));
    }

    @Test void supportedAudiencesAreExplicitAndInvalidChoicesNeverReachTheServer() throws Exception {
        var project=project();
        for(String audience:List.of("COMMUNITY","LINK","BOTH")){var published=PublicSharing.publish("PROJECT",project.id(),"Title","Summary","Author","",audience,List.of());assertEquals(audience,published.audience());}
        backend.calls.clear();
        for(String audience:List.of("PRIVATE","PUBLIC","community",""))assertThrows(IllegalArgumentException.class,()->PublicSharing.publish("PROJECT",project.id(),"Title","Summary","Author","",audience,List.of()));
        assertThrows(IllegalArgumentException.class,()->PublicSharing.publish("PROFILE",project.id(),"Title","","Author","","BOTH",List.of()));
        assertThrows(IllegalArgumentException.class,()->PublicSharing.publish("PROJECT",project.id(),"Title","","Author","javascript:alert(1)","BOTH",List.of()));
        assertTrue(backend.calls.isEmpty());
    }

    @Test void publicLinkUsesAnOpaqueTokenAndRevocationRequiresOnlyTheOwnedSource() throws Exception {
        var project=project();var published=PublicSharing.publish("PROJECT",project.id(),"Title","Summary","Author","","LINK",List.of());
        String url=PublicSharing.publicUrl(published);assertTrue(url.startsWith(PublicSharing.PUBLIC_BASE+"#"));assertEquals(UUID.fromString(published.token()).toString(),url.substring(url.indexOf('#')+1));
        assertFalse(url.contains(project.id()));assertFalse(url.contains(user.email()));assertFalse(url.contains("?"));
        PublicSharing.unpublish("PROJECT",project.id());assertEquals(Set.of("source_kind","source_id"),backend.lastBody.keySet());assertEquals(project.id(),backend.lastBody.get("source_id").getAsString());
        assertNull(PublicSharing.getOwn("PROJECT",project.id()));assertTrue(PublicSharing.community().isEmpty());
        var republished=PublicSharing.publish("PROJECT",project.id(),"Title","Summary","Author","","LINK",List.of());assertNotEquals(published.token(),republished.token());
    }

    @Test void nocPublicationDoesNotSubmitPrivateReasonStaffNotesOrStatusClaims() throws Exception {
        var request=data.submit(user,"Scholarship","Private financial reason",TestBackend.TEACHER_ID);
        var published=PublicSharing.publish("NOC",request.id(),"Scholarship application","Publicly chosen summary","Chosen name","","COMMUNITY",List.of());
        assertEquals("PENDING",published.status());assertEquals("Scholarship",published.category());assertTrue(published.files().isEmpty());
        for(String name:List.of("reason","decision_note","student_id","recipient_id","email","status"))assertFalse(backend.lastBody.has(name));
        assertFalse(backend.lastBody.toString().contains("Private financial reason"));backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->PublicSharing.publish("NOC",request.id(),"Title","Summary","Author","","BOTH",List.of(TestBackend.ID)));
        assertTrue(backend.calls.isEmpty());
    }

    @Test void communityShowsOnlyCommunityAudiencesAndSharingOutagesDoNotCreateSuccess() throws Exception {
        for(String audience:List.of("COMMUNITY","LINK","BOTH")){var project=project();PublicSharing.publish("PROJECT",project.id(),audience,"Summary","Author","",audience,List.of());}
        assertEquals(Set.of("COMMUNITY","BOTH"),new HashSet<>(PublicSharing.community().stream().map(PublicSharing.PublishedItem::audience).toList()));
        backend.sharingUnavailable=true;
        assertThrows(IOException.class,()->PublicSharing.getOwn("PROJECT",TestBackend.ID));
        assertThrows(IOException.class,()->PublicSharing.publish("PROJECT",TestBackend.ID,"Title","Summary","Author","","BOTH",List.of()));
        assertThrows(IOException.class,()->PublicSharing.unpublish("PROJECT",TestBackend.ID));assertEquals(3,backend.shares.size());
    }

    @Test void parsingEmptySharesAndSharedFilePathValidationFailSafely() throws Exception {
        assertNull(PublicSharing.parseNullable(JsonNull.INSTANCE));assertNull(PublicSharing.parseNullable(new JsonArray()));
        backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->PublicSharing.download(new PublicSharing.FileItem(TestBackend.ID,"File","x.txt","../../outside.txt")));
        assertTrue(backend.calls.isEmpty());
    }
}
