package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AppDataTest {
    @TempDir Path directory;
    TestBackend backend; AppData data;
    @BeforeEach void start() throws Exception { backend=new TestBackend(); data=new AppData(directory,backend.client()); }
    @AfterEach void stop() { backend.close(); }
    private AppData.User login() throws Exception { return data.authenticate("account0@example.invalid","long-password-test").orElseThrow(); }
    @Test void startsEmptyAndNeverReadsOrSeedsLegacyRecords() throws Exception {
        Files.writeString(directory.resolve("state.xml"),"legacy data must not be imported");
        AppData empty=new AppData(directory,null);
        assertTrue(empty.users().isEmpty()); assertTrue(empty.requests().isEmpty()); assertFalse(empty.configured());
        assertThrows(IllegalStateException.class,()->empty.authenticate(AppData.Role.STUDENT,"student@example.invalid","student123"));
        assertEquals("legacy data must not be imported",Files.readString(directory.resolve("state.xml")));
    }
    @Test void authenticatesRealServerProfileAndStartsWithoutRequests() throws Exception {
        var user=login(); assertEquals("account0@example.invalid",user.email()); assertEquals(TestBackend.ID,user.id());
        assertTrue(data.requests().isEmpty()); assertEquals(1,data.users().size());
        assertTrue(backend.calls.stream().anyMatch(s->s.contains("grant_type=password")));
    }
    @Test void rejectsIncorrectPasswordAndIgnoresForgedRoleHints() throws Exception {
        assertThrows(IOException.class,()->data.authenticate("account0@example.invalid","wrong"));
        assertNull(data.currentUser());
        backend.authMetadata.addProperty("role","ADMIN");backend.authMetadata.addProperty("is_admin",true);
        var user=data.authenticate(AppData.Role.ADMIN,"account0@example.invalid","long-password-test").orElseThrow();
        assertEquals(AppData.Role.STUDENT,user.role());assertEquals(user,data.currentUser());
        backend.calls.clear();assertThrows(IllegalArgumentException.class,()->data.setAccountRole(TestBackend.TEACHER_ID,AppData.Role.TEACHER));
        assertTrue(backend.calls.isEmpty());
    }
    @Test void unifiedLoginUsesEveryRoleFromTheServerProfileWithoutASubmittedRole() throws Exception {
        for(var role:AppData.Role.values()){
            backend.role=role;var user=login();assertEquals(role,user.role());
            assertEquals(java.util.Set.of("email","password"),backend.lastBody.keySet());
            assertEquals("account0@example.invalid",backend.lastBody.get("email").getAsString());
            data.logout();
        }
    }
    @Test void unverifiedEmailCannotSignIn() { backend.confirmed=false; assertThrows(IOException.class,this::login); assertNull(data.currentUser()); }
    @Test void noPasswordOrSessionIsWrittenToDisk() throws Exception { login(); try(var entries=Files.list(directory)) { assertEquals(0,entries.count()); } }
    @Test void registrationNeverSubmitsARoleOrAdminFlag() throws Exception {
        data.register("person@example.invalid","long-password-test","Real Name","Science","ID-17");
        var metadata=backend.lastBody.getAsJsonObject("data"); assertEquals(4,metadata.size()); assertFalse(metadata.has("role"));
        assertEquals("STUDENT",metadata.get("account_type").getAsString());assertFalse(metadata.has("is_admin"));
        assertEquals("Real Name",metadata.get("full_name").getAsString()); assertNull(data.currentUser());
    }
    @Test void publicSignupAllowsOnlyStudentOrViewerAndNeverStaffPermissions() throws Exception {
        for(var role:java.util.List.of(AppData.Role.STUDENT,AppData.Role.VIEWER)){
            data.register("person@example.invalid","long-password-test","Real Name",role==AppData.Role.VIEWER?"":"Science","",role);
            var metadata=backend.lastBody.getAsJsonObject("data");
            assertEquals(java.util.Set.of("full_name","department","student_number","account_type"),metadata.keySet());
            assertEquals(role.name(),metadata.get("account_type").getAsString());assertNull(data.currentUser());
        }
        backend.calls.clear();
        for(var role:new AppData.Role[]{AppData.Role.TEACHER,AppData.Role.ADMIN,null})
            assertThrows(IllegalArgumentException.class,()->data.register("person@example.invalid","long-password-test","Real Name","Science","",role));
        assertTrue(backend.calls.isEmpty());
    }
    @Test void viewerCanEditOwnProfileAndContactStaffButCannotUseStudentOrStaffActions() throws Exception {
        String otherStudent="00000000-0000-4000-8000-000000000202",otherViewer="00000000-0000-4000-8000-000000000203";
        backend.chatContacts.add(TestBackend.contactRow(otherStudent,"Student contact",AppData.Role.STUDENT));
        backend.chatContacts.add(TestBackend.contactRow(otherViewer,"Viewer contact",AppData.Role.VIEWER));
        backend.profileCount=2;backend.role=AppData.Role.VIEWER;var viewer=login();
        assertEquals(1,data.users().size());assertTrue(data.requests().isEmpty());assertTrue(data.projects().isEmpty());assertTrue(data.portfolios().isEmpty());
        assertEquals(java.util.List.of(TestBackend.TEACHER_ID),data.contacts().stream().map(AppData.Contact::id).toList());
        backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->data.submit(viewer,"Internship","Approval request"));
        assertThrows(IllegalArgumentException.class,()->data.createProject(viewer,"Work","Project","","",""));
        assertThrows(IllegalArgumentException.class,()->data.decide(viewer,TestBackend.ID,AppData.Status.APPROVED,""));
        assertThrows(IllegalArgumentException.class,()->data.setAccountRole(TestBackend.TEACHER_ID,AppData.Role.TEACHER));
        assertThrows(IllegalArgumentException.class,()->data.verifyIdentity(TestBackend.TEACHER_ID));
        assertThrows(IllegalArgumentException.class,()->data.sendMessage(otherStudent,"Hello"));
        assertThrows(IllegalArgumentException.class,()->data.sendMessage(otherViewer,"Hello"));
        assertTrue(backend.calls.isEmpty());
        assertEquals(TestBackend.TEACHER_ID,data.sendMessage(TestBackend.TEACHER_ID,"Hello, may we discuss the published work?").recipientId());
        var updated=data.updateProfile("Recruiting Person","Example organisation");assertEquals(AppData.Role.VIEWER,updated.role());assertEquals("Recruiting Person",updated.name());
        assertFalse(backend.lastBody.has("role"));
    }
    @Test void foundingAdministratorCanPromoteAndDemoteOtherAdminsWhileOwnAccessStaysProtected() throws Exception {
        String student="00000000-0000-4000-8000-000000000002",admin="00000000-0000-4000-8000-000000000003";
        backend.role=AppData.Role.ADMIN;backend.canManageAdmins=true;backend.profileCount=3;
        backend.profileRoles.put(student,AppData.Role.STUDENT);backend.profileRoles.put(admin,AppData.Role.ADMIN);login();
        assertTrue(data.canManageAdmins());assertTrue(backend.calls.stream().anyMatch(call->call.contains("/rpc/account_capabilities")));
        var promoted=data.setAccountRole(student,AppData.Role.ADMIN);assertEquals(AppData.Role.ADMIN,promoted.role());
        assertEquals(java.util.Set.of("target_id","new_role"),backend.lastBody.keySet());assertEquals("ADMIN",backend.lastBody.get("new_role").getAsString());
        assertEquals(AppData.Role.ADMIN,data.users().stream().filter(u->u.id().equals(student)).findFirst().orElseThrow().role());
        assertEquals(AppData.Role.TEACHER,data.setAccountRole(admin,AppData.Role.TEACHER).role());
        backend.calls.clear();
        for(var role:AppData.Role.values())assertThrows(IllegalArgumentException.class,()->data.setAccountRole(TestBackend.ID,role));
        assertTrue(backend.calls.isEmpty());assertEquals(AppData.Role.ADMIN,data.currentUser().role());
        data.logout();assertFalse(data.canManageAdmins());
    }
    @Test void ordinaryAdminCannotForgeOwnerCapabilityOrChangeAnotherAdminEvenThroughDirectRequests() throws Exception {
        String student="00000000-0000-4000-8000-000000000002",admin="00000000-0000-4000-8000-000000000003";
        backend.role=AppData.Role.ADMIN;backend.profileCount=3;backend.profileRoles.put(student,AppData.Role.STUDENT);backend.profileRoles.put(admin,AppData.Role.ADMIN);
        backend.authMetadata.addProperty("can_manage_admins",true);backend.authMetadata.addProperty("is_owner",true);login();
        assertFalse(data.canManageAdmins());backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->data.setAccountRole(student,AppData.Role.ADMIN));
        assertThrows(IllegalArgumentException.class,()->data.setAccountRole(admin,AppData.Role.STUDENT));
        assertTrue(backend.calls.isEmpty());
        assertThrows(IOException.class,()->data.cloudApi().json("POST","/rest/v1/rpc/set_account_role",java.util.Map.of("target_id",student,"new_role","ADMIN"),true));
        assertThrows(IOException.class,()->data.cloudApi().json("POST","/rest/v1/rpc/set_account_role",java.util.Map.of("target_id",admin,"new_role","STUDENT"),true));
        assertThrows(IOException.class,()->data.cloudApi().json("PATCH","/rest/v1/profiles?id=eq."+TestBackend.ID,java.util.Map.of("role","ADMIN","can_manage_admins",true),true));
        assertEquals(AppData.Role.TEACHER,data.setAccountRole(student,AppData.Role.TEACHER).role());
        assertEquals(AppData.Role.ADMIN,backend.profileRoles.get(admin));
    }
    @Test void staleOwnerCapabilityCannotOverrideTheServersCurrentDecision() throws Exception {
        String target="00000000-0000-4000-8000-000000000002";
        backend.role=AppData.Role.ADMIN;backend.canManageAdmins=true;backend.profileCount=2;backend.profileRoles.put(target,AppData.Role.STUDENT);login();
        assertTrue(data.canManageAdmins());backend.canManageAdmins=false;
        assertThrows(IOException.class,()->data.setAccountRole(target,AppData.Role.ADMIN));assertEquals(AppData.Role.STUDENT,backend.profileRoles.get(target));
        data.refresh();assertFalse(data.canManageAdmins());
        backend.calls.clear();assertThrows(IllegalArgumentException.class,()->data.setAccountRole(target,AppData.Role.ADMIN));assertTrue(backend.calls.isEmpty());
    }
    @Test void unavailableCapabilitiesFailClosedWithoutBlockingOrdinaryAdminLoginAndNeverLeakAcrossAccounts() throws Exception {
        backend.role=AppData.Role.ADMIN;backend.canManageAdmins=true;backend.capabilitiesUnavailable=true;
        assertEquals(AppData.Role.ADMIN,login().role());assertFalse(data.canManageAdmins());assertFalse(data.adminCapabilitiesAvailable());
        assertThrows(IllegalArgumentException.class,()->data.setAccountRole(TestBackend.TEACHER_ID,AppData.Role.ADMIN));
        assertEquals(AppData.Role.STUDENT,data.setAccountRole(TestBackend.TEACHER_ID,AppData.Role.STUDENT).role());
        backend.capabilitiesUnavailable=false;data.refresh();assertTrue(data.canManageAdmins());assertTrue(data.adminCapabilitiesAvailable());
        backend.capabilitiesUnavailable=true;data.refresh();assertFalse(data.canManageAdmins());assertFalse(data.adminCapabilitiesAvailable());
        data.logout();backend.role=AppData.Role.STUDENT;backend.capabilitiesUnavailable=false;backend.calls.clear();login();
        assertFalse(data.canManageAdmins());assertTrue(backend.calls.stream().noneMatch(call->call.contains("/rpc/account_capabilities")));
    }
    @Test void privilegedCapabilityRequiresATypedBooleanAndMalformedResponsesClearPreviousPermission() throws Exception {
        backend.role=AppData.Role.ADMIN;backend.canManageAdmins=true;login();assertTrue(data.canManageAdmins());
        for(String malformed:java.util.List.of("{\"can_manage_admins\":\"true\"}","{\"can_manage_admins\":1}","{\"can_manage_admins\":null}","{}","[]")){
            backend.capabilitiesResponse=malformed;data.refresh();assertFalse(data.canManageAdmins(),malformed);assertFalse(data.adminCapabilitiesAvailable(),malformed);
        }
        backend.capabilitiesResponse=null;backend.canManageAdmins=false;data.refresh();assertFalse(data.canManageAdmins());assertTrue(data.adminCapabilitiesAvailable());
        data.logout();assertFalse(data.adminCapabilitiesAvailable());
    }
    @Test void registrationAcceptsMissingInstitutionIdWithoutInventingOne() throws Exception {
        for(String number:new String[]{null,""," \t "}) {
            data.register("person@example.invalid","long-password-test","Real Name","Science",number);
            assertEquals("",backend.lastBody.getAsJsonObject("data").get("student_number").getAsString());
        }
    }
    @Test void registrationTrimsAndPreservesInstitutionIdIncludingMaximumLength() throws Exception {
        for(String number:new String[]{"ID-17","X".repeat(80)}) {
            data.register("person@example.invalid","long-password-test","Real Name","Science","  "+number+"  ");
            assertEquals(number,backend.lastBody.getAsJsonObject("data").get("student_number").getAsString());
        }
    }
    @Test void registrationRejectsOverlongInstitutionIdBeforeNetworkRequest() {
        assertThrows(IllegalArgumentException.class,()->data.register("person@example.invalid","long-password-test","Real Name","Science","X".repeat(81)));
        assertTrue(backend.calls.isEmpty());
    }
    @Test void displayIdUsesProvidedInstitutionIdOrAnExplicitMissingLabel() {
        for(String number:new String[]{null,""," \t ","ID-17"}) {
            var user=new AppData.User(TestBackend.ID,"person@example.invalid","Real Name",AppData.Role.STUDENT,"Science","person@example.invalid",number);
            assertEquals("ID-17".equals(number)?"ID-17":"Not provided",user.displayId());
            assertEquals(TestBackend.ID,user.id());
        }
    }
    @Test void validatesEmailPasswordAndProfileBeforeNetworkRequest() {
        assertThrows(IllegalArgumentException.class,()->data.register("invalid","long-password-test","Name","Science","17"));
        assertThrows(IllegalArgumentException.class,()->data.register("p@example.invalid","short","Name","Science","17"));
        assertThrows(IllegalArgumentException.class,()->data.register("p@example.invalid","long-password-test"," ","Science","17"));
        assertTrue(backend.calls.isEmpty());
    }
    @Test void requestPayloadLetsDatabaseChooseOwnerStatusAndTimestamp() throws Exception {
        var student=login(); var result=data.submit(student,"Internship","A real application reason");
        assertEquals(2,backend.lastBody.size()); assertTrue(backend.lastBody.has("type")); assertTrue(backend.lastBody.has("reason"));
        assertEquals(AppData.Status.PENDING,result.status()); assertEquals(student.id(),result.studentId()); assertEquals(1,data.requestsFor(student.id()).size());
    }
    @Test void blocksForgedActorAndInvalidRequestLocally() throws Exception {
        var user=login(); assertThrows(IllegalArgumentException.class,()->data.submit(user,"unknown","reason"));
        assertThrows(IllegalArgumentException.class,()->data.submit(user,"Internship"," "));
        var forged=new AppData.User(user.id(),user.username(),"Changed",user.role(),user.department(),user.email());
        assertThrows(IllegalArgumentException.class,()->data.submit(forged,"Internship","reason"));
        assertThrows(IllegalArgumentException.class,()->data.decide(user,TestBackend.ID,AppData.Status.APPROVED,""));
    }
    @Test void paginationReadsMoreThanOneServerPage() throws Exception { backend.profileCount=501; login(); assertEquals(501,data.users().size()); assertTrue(backend.calls.stream().anyMatch(s->s.contains("offset=500"))); }
    @Test void staffDecisionAcceptsPostgrestCompositeRowResponse() throws Exception {
        var student=login(); var request=data.submit(student,"Internship","Reason"); data.logout();
        backend.role=AppData.Role.TEACHER; var teacher=login();
        data.decide(teacher,request.id(),AppData.Status.APPROVED,"Verified");
        assertEquals(AppData.Status.APPROVED,data.requests().get(0).status());
        assertEquals("Verified",data.requests().get(0).decisionNote());
    }
    @Test void refreshTokenIsUsedWhenSessionExpires() throws Exception { backend.expireImmediately=true; login(); assertTrue(backend.refreshCount>0); }
    @Test void logoutClearsAccountAndCachedRows() throws Exception { var user=login(); data.submit(user,"Other","Reason"); data.logout(); assertNull(data.currentUser()); assertTrue(data.requests().isEmpty()); assertThrows(IllegalStateException.class,data::refresh); }
    @Test void profileUpdateCannotChangeRoleOrEmail() throws Exception { login(); var user=data.updateProfile("Updated Name","Mathematics"); assertEquals("Updated Name",user.name()); assertEquals(2,backend.lastBody.size()); assertFalse(backend.lastBody.has("role")); assertFalse(backend.lastBody.has("email")); }
    @Test void privateDocumentUploadDownloadAndPathValidation() throws Exception {
        var user=login(); Path file=directory.resolve("input.txt"); Files.writeString(file,"private document");
        var item=data.addPortfolio(user,"My document",file); assertTrue(item.storedName().startsWith(user.id()+"/"));
        Path downloaded=data.downloadPortfolio(item); assertEquals("private document",Files.readString(downloaded));
        assertTrue(backend.calls.stream().anyMatch(s->s.contains("/object/authenticated/portfolios/")));
        assertThrows(IllegalArgumentException.class,()->data.portfolioPath(new AppData.PortfolioItem("id",user.id(),"t","x.txt","../../x.txt",null)));
    }
    @Test void metadataFailureCleansUpUploadedOrphan() throws Exception {
        var user=login(); Path file=directory.resolve("input.txt"); Files.writeString(file,"data"); backend.rejectMetadata=true;
        assertThrows(IOException.class,()->data.addPortfolio(user,"title",file)); assertTrue(data.portfoliosFor(user.id()).isEmpty());
        assertTrue(backend.calls.stream().anyMatch(s->s.equals("DELETE /storage/v1/object/portfolios")));
    }
    @Test void verificationAndRecoveryUseEmailCodesAndClearRecoverySession() throws Exception {
        data.verifyEmail("p@example.invalid","123456"); assertEquals("signup",backend.lastBody.get("type").getAsString());
        data.requestRecovery("p@example.invalid"); data.resetPassword("p@example.invalid","123456","new-long-password");
        assertTrue(backend.calls.stream().anyMatch(s->s.equals("PUT /auth/v1/user"))); assertNull(data.currentUser());
    }
    @Test void configurationRejectsSecretsAndInsecureUrls() {
        assertThrows(IllegalArgumentException.class,()->CloudConfig.validate("http://project.supabase.co","sb_publishable_abc"));
        assertThrows(IllegalArgumentException.class,()->CloudConfig.validate("https://project.supabase.co","sb_secret_abc"));
        assertThrows(IllegalArgumentException.class,()->CloudConfig.validate("https://project.supabase.co.evil.invalid","sb_publishable_abc"));
        assertThrows(IllegalArgumentException.class,()->CloudConfig.validate("https://project.supabase.co/extra","sb_publishable_abc"));
        assertNotNull(CloudConfig.validate("https://project.supabase.co/","sb_publishable_abc"));
    }
}
