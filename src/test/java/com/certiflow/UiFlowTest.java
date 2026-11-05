package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class UiFlowTest {
    @TempDir Path directory;
    TestBackend backend; AppData data; Stage stage;
    @BeforeAll static void toolkit() throws Exception {
        System.setProperty("prism.order","sw"); CountDownLatch started=new CountDownLatch(1);
        Platform.startup(()->{Platform.setImplicitExit(false);started.countDown();}); assertTrue(started.await(15,TimeUnit.SECONDS));
    }
    @BeforeEach void setup() throws Exception {
        backend=new TestBackend(); data=new AppData(directory,backend.client());
        fx(()->{ UiSettings.load(directory); stage=new Stage(); Scene scene=new Scene(new VBox(),1200,800);
            scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm()); scene.getStylesheets().add(App.class.getResource("/auth.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("/chat.css").toExternalForm());
            stage.setScene(scene); field("primaryStage",stage); field("data",data); field("currentUser",null); App.showOptionView(); return null; });
    }
    @AfterEach void cleanup() throws Exception {
        fx(()->{assertFalse(stage.isShowing());stage.setScene(null);return null;});
        CountDownLatch idle=new CountDownLatch(1); Background.execute(idle::countDown); assertTrue(idle.await(10,TimeUnit.SECONDS));
        fx(()->{stage.close();field("primaryStage",null);field("currentUser",null);field("data",null);return null;}); backend.close();
    }
    @AfterAll static void shutdown() { Platform.exit(); }
    @Test void emailLoginUsesBackgroundRequestAndDisplaysOnlyServerIdentity() throws Exception {
        fx(()->{assertNull(stage.getScene().lookup("#role-student"));assertNull(stage.getScene().lookup("#role-admin"));button("login-submit").fire(); assertTrue(label("login-error").isVisible());
            control("login-email",TextField.class).setText("account0@example.invalid");control("login-password",PasswordField.class).setText("long-password-test");button("login-submit").fire();return null;});
        await(()->stage.getScene().lookup("#page-content")!=null);
        fx(()->{assertEquals("account0@example.invalid",App.user().email());assertTrue(data.requests().isEmpty());assertNoDemoText(stage.getScene().getRoot());return null;});
    }
    @Test void oneLoginRoutesEachAccountUsingOnlyTheServerRole() throws Exception {
        backend.authMetadata.addProperty("role","ADMIN");
        for(var role:AppData.Role.values()){
            backend.role=role;
            fx(()->{App.showLoginView("Admin","");assertNull(stage.getScene().lookup("#role-student"));assertNull(stage.getScene().lookup("#role-admin"));
                control("login-email",TextField.class).setText("account0@example.invalid");control("login-password",PasswordField.class).setText("long-password-test");button("login-submit").fire();return null;});
            await(()->stage.getScene().lookup("#page-content")!=null);
            fx(()->{
                assertEquals(role,App.user().role());String expected=role==AppData.Role.STUDENT?"student-home":role==AppData.Role.VIEWER?"viewer-home":"staff-home";
                var route=App.class.getDeclaredField("currentRoute");route.setAccessible(true);assertEquals(expected,route.get(null));
                if(role==AppData.Role.VIEWER){
                    assertNotNull(stage.getScene().lookup("#community-search"));
                    for(String privateAction:List.of("NOC","Requests","History","Students","Accounts","Portfolios"))assertFalse(containsText(stage.getScene().getRoot(),privateAction),privateAction);
                    assertNull(stage.getScene().lookup("#noc-submit"));
                }
                return null;
            });
        }
    }
    @Test void registrationWithoutInstitutionIdOpensVerificationAndAcceptsEmailCode() throws Exception {
        fx(()->{
            App.showAccountView("register","Student");
            control("account-email",TextField.class).setText("  person@example.invalid  ");
            control("account-name",TextField.class).setText("Real Name");
            control("account-department",TextField.class).setText("Science");
            assertEquals("",control("account-number",TextField.class).getText());
            control("account-password",PasswordField.class).setText("long-password-test");
            control("account-confirm",PasswordField.class).setText("long-password-test");
            button("account-submit").fire();return null;
        });
        await(()->stage.getScene().lookup("#account-code")!=null);
        fx(()->{
            assertTrue(control("account-code",TextField.class).isVisible());
            assertEquals("person@example.invalid",control("account-email",TextField.class).getText());
            assertTrue(backend.calls.contains("POST /auth/v1/signup"));
            assertEquals("",backend.lastBody.getAsJsonObject("data").get("student_number").getAsString());
            button("account-send-code").fire();return null;
        });
        await(()->label("account-feedback").getText().contains("a code will arrive"));
        fx(()->{
            assertTrue(backend.calls.contains("POST /auth/v1/resend"));
            assertEquals("person@example.invalid",backend.lastBody.get("email").getAsString());
            assertEquals("signup",backend.lastBody.get("type").getAsString());
            control("account-code",TextField.class).setText("123456");button("account-submit").fire();return null;
        });
        await(()->label("account-feedback").getText().contains("Email verified"));
        fx(()->{
            assertTrue(backend.calls.contains("POST /auth/v1/verify"));
            assertEquals("person@example.invalid",backend.lastBody.get("email").getAsString());
            assertEquals("123456",backend.lastBody.get("token").getAsString());
            assertEquals("signup",backend.lastBody.get("type").getAsString());
            assertEquals("",control("account-code",TextField.class).getText());assertNull(data.currentUser());return null;
        });
    }
    @Test void verificationLinksPreserveTypedEmailFromRegistrationAndLogin() throws Exception {
        for(String source:List.of("register","login")) fx(()->{
            if(source.equals("register")) App.showAccountView("register","Student"); else App.showLoginView("Student","");
            control(source.equals("register")?"account-email":"login-email",TextField.class).setText("  person@example.invalid  ");
            control("open-verification",Hyperlink.class).fire();
            assertTrue(control("account-code",TextField.class).isVisible());
            assertEquals("person@example.invalid",control("account-email",TextField.class).getText());return null;
        });
        assertTrue(backend.calls.isEmpty());
    }
    @Test void registrationOffersOnlyStudentAndViewerAndViewerNeedsNoInstitutionId() throws Exception {
        fx(()->{
            App.showAccountView("register","Student");ComboBox<AppData.Role> roles=control("account-role",ComboBox.class);
            assertEquals(List.of(AppData.Role.STUDENT,AppData.Role.VIEWER),roles.getItems());assertEquals(AppData.Role.STUDENT,roles.getValue());
            roles.setValue(AppData.Role.ADMIN);button("account-submit").fire();assertTrue(label("account-feedback").getText().contains("Choose Student"));assertTrue(backend.calls.isEmpty());
            control("account-number",TextField.class).setText("SHOULD-NOT-BE-SENT");roles.setValue(AppData.Role.VIEWER);
            control("account-email",TextField.class).setText("viewer@example.invalid");control("account-name",TextField.class).setText("Recruiting Person");
            control("account-department",TextField.class).setText("");control("account-password",PasswordField.class).setText("long-password-test");control("account-confirm",PasswordField.class).setText("long-password-test");
            button("account-submit").fire();return null;
        });
        await(()->stage.getScene().lookup("#account-code")!=null);
        fx(()->{
            var metadata=backend.lastBody.getAsJsonObject("data");assertEquals("VIEWER",metadata.get("account_type").getAsString());
            assertEquals("",metadata.get("student_number").getAsString());assertFalse(metadata.has("role"));assertFalse(metadata.has("is_admin"));
            assertEquals("viewer@example.invalid",control("account-email",TextField.class).getText());assertNull(data.currentUser());
            control("account-back-to-login",Hyperlink.class).fire();assertEquals("viewer@example.invalid",control("login-email",TextField.class).getText());
            assertNull(stage.getScene().lookup("#role-admin"));return null;
        });
    }
    @Test void backButtonIsBelowMenuAndHistoryWorksForAllRolesAndSizes() throws Exception {
        for(var role:AppData.Role.values()) {
            backend.role=role; var user=data.authenticate("account0@example.invalid","long-password-test").orElseThrow();
            fx(()->{App.completeLogin(user);return null;});
            for(double width:new double[]{320,390,768,1366}) fx(()->{
                resize(width,width<500?844:800);
                Node back=button("return-button"),menu=button("navigation-toggle");
                assertTrue(back.localToScene(back.getBoundsInLocal()).getMinY()>menu.localToScene(menu.getBoundsInLocal()).getMaxY(),"Back must be below the menu");
                assertTrue(back.isDisabled());assertHorizontalFit(stage.getScene().getRoot(),width);return null;
            });
            fx(()->{if(role==AppData.Role.STUDENT)App.showStudentNoc();else if(role==AppData.Role.VIEWER)App.showViewerProfile();else App.showTeacherDashboard();assertFalse(button("return-button").isDisabled());button("return-button").fire();assertTrue(button("return-button").isDisabled());return null;});
        }
    }
    @Test void allAccountFormsFitNarrowAndWideScreensWithoutDemoCredentials() throws Exception {
        for(String mode:List.of("register","verify","recover")) for(double width:new double[]{320,390,1280}) fx(()->{
            App.showAccountView(mode,"Student");resize(width,844);assertHorizontalFit(stage.getScene().getRoot(),width);
            assertNotNull(control("account-email",TextField.class));assertNoDemoText(stage.getScene().getRoot());return null;
        });
    }
    @Test void submittingApplicationPersistsThroughTheHttpClient() throws Exception {
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        fx(()->{App.completeLogin(user);App.showStudentNoc();control("noc-type",ComboBox.class).setValue("Internship");control("noc-reason",TextArea.class).setText("Internship approval request");button("noc-submit").fire();return null;});
        await(()->label("noc-feedback").getText().contains("submitted"));
        fx(()->{assertEquals(1,data.requests().size());assertEquals("",control("noc-reason",TextArea.class).getText());return null;});
    }
    @Test void savedProfileUsesRealEmailAndUpdatesName() throws Exception {
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        fx(()->{App.completeLogin(user);App.showStudentProfile();control("profile-name",TextField.class).setText("Updated Account Name");button("profile-save").fire();return null;});
        await(()->label("profile-feedback").getText().equals("Profile updated."));
        fx(()->{assertEquals("Updated Account Name",App.user().name());assertEquals("account0@example.invalid",App.user().email());return null;});
    }
    @Test void expandedProfileFieldsPersistForEveryRoleWithoutSubmittingProtectedFields() throws Exception {
        for (AppData.Role role : AppData.Role.values()) {
            backend.role=role; backend.profileChanges.addProperty("identity_verified",true);
            var user=data.authenticate("account0@example.invalid","long-password-test").orElseThrow();
            fx(()->{
                App.completeLogin(user); if(role==AppData.Role.STUDENT)App.showStudentProfile();else if(role==AppData.Role.VIEWER)App.showViewerProfile();else App.showTeacherProfile();
                assertTrue(label("profile-verification").getText().contains("checked"));
                control("profile-name",TextField.class).setText("Updated Person");
                control("profile-department",TextField.class).setText("Engineering");
                control("profile-number",TextField.class).setText("ID-42");
                control("profile-programme",TextField.class).setText("Computer Science");
                control("profile-bio",TextArea.class).setText("Researching accessible academic software.");
                control("profile-skills",TextField.class).setText("Java, research");
                control("profile-website",TextField.class).setText("https://example.invalid/profile");
                button("profile-save").fire();return null;
            });
            await(()->label("profile-feedback").getText().equals("Profile updated."));
            fx(()->{
                assertEquals("Updated Person",App.user().name());assertEquals("Engineering",App.user().department());
                assertEquals("ID-42",App.user().studentNumber());assertEquals("Computer Science",App.user().programme());
                assertEquals("Researching accessible academic software.",App.user().bio());
                assertEquals("Java, research",App.user().skills());assertEquals("https://example.invalid/profile",App.user().website());
                assertEquals(role,App.user().role());assertEquals("account0@example.invalid",App.user().email());
                assertFalse(App.user().identityVerified());assertTrue(label("profile-verification").getText().contains("self-reported"));
                assertEquals(7,backend.lastBody.size());
                for(String protectedField:List.of("role","email","identity_verified","verified_by","verified_at"))assertFalse(backend.lastBody.has(protectedField));
                return null;
            });
        }
    }
    @Test void collaborationPagesFitPhoneTabletAndLaptopWidths() throws Exception {
        for(AppData.Role role:AppData.Role.values()) {
            backend.role=role;var user=data.authenticate("account0@example.invalid","long-password-test").orElseThrow();
            fx(()->{App.completeLogin(user);return null;});
            var pages=new java.util.ArrayList<PageCase>();
            if(role==AppData.Role.STUDENT) {
                pages.add(new PageCase("portfolio",App::showStudentPortfolio));pages.add(new PageCase("noc",App::showStudentNoc));
                pages.add(new PageCase("profile",App::showStudentProfile));
            } else if(role==AppData.Role.VIEWER) {
                pages.add(new PageCase("discover",App::showViewerHome));pages.add(new PageCase("profile",App::showViewerProfile));
            } else {
                pages.add(new PageCase("shared-portfolios",App::showSharedPortfolios));pages.add(new PageCase("profile",App::showTeacherProfile));
                if(role==AppData.Role.ADMIN)pages.add(new PageCase("accounts",App::showAccountManagement));
            }
            pages.add(new PageCase("messages",App::showMessages));
            for(PageCase page:pages) fx(()->{
                page.open().run();
                for(int width:new int[]{320,390,768,1366}) {
                    resize(width,844);assertHorizontalFit(stage.getScene().getRoot(),width);assertNoDemoText(stage.getScene().getRoot());
                    if(width==390||width==1366)capture(role.name().toLowerCase()+"-"+page.name()+"-"+width);
                }
                App.showHome();return null;
            });
        }
    }
    @Test void onlyFoundingAdminSeesAdministratorRoleChoicesAndSelfAccessHasNoControls() throws Exception {
        String student="00000000-0000-4000-8000-000000000002",otherAdmin="00000000-0000-4000-8000-000000000003";
        backend.role=AppData.Role.ADMIN;backend.profileCount=3;backend.profileRoles.put(student,AppData.Role.STUDENT);backend.profileRoles.put(otherAdmin,AppData.Role.ADMIN);
        backend.authMetadata.addProperty("can_manage_admins",true);
        for(boolean owner:new boolean[]{false,true}){
            backend.canManageAdmins=owner;var user=data.authenticate("account0@example.invalid","long-password-test").orElseThrow();
            fx(()->{
                App.completeLogin(user);App.showAccountManagement();ComboBox<AppData.Role> choices=control("account-role-"+student,ComboBox.class);
                assertEquals(owner,choices.getItems().contains(AppData.Role.ADMIN));assertTrue(choices.getItems().containsAll(List.of(AppData.Role.STUDENT,AppData.Role.VIEWER,AppData.Role.TEACHER)));
                assertNull(stage.getScene().lookup("#account-role-"+TestBackend.ID));assertNull(stage.getScene().lookup("#change-role-"+TestBackend.ID));
                assertTrue(button("change-role-"+student).isDisabled());
                if(owner){
                    assertNotNull(stage.getScene().lookup("#account-role-"+otherAdmin));choices.setValue(AppData.Role.ADMIN);assertFalse(button("change-role-"+student).isDisabled());
                }else assertNull(stage.getScene().lookup("#account-role-"+otherAdmin));
                resize(390,844);assertHorizontalFit(stage.getScene().getRoot(),390);capture(owner?"owner-account-access-390":"admin-account-access-390");return null;
            });
        }
        assertTrue(backend.calls.stream().noneMatch(call->call.contains("/set_account_role")),"Inspecting role choices must not change any account");
    }
    @Test void profileIsTheLastMenuItemAboveLogoutForEveryRoleAndOpensTheCorrectAccount() throws Exception {
        for(var role:AppData.Role.values()){
            backend.role=role;var user=data.authenticate("account0@example.invalid","long-password-test").orElseThrow();
            fx(()->{App.completeLogin(user);return null;});
            for(int width:new int[]{320,390,1366})fx(()->{
                resize(width,844);VBox footer=control("sidebar-footer",VBox.class);Button profile=button("nav-profile"),logout=button("logout-button");
                assertSame(footer,profile.getParent());assertSame(footer,logout.getParent());
                assertEquals(footer.getChildren().indexOf(logout)-1,footer.getChildren().indexOf(profile));
                assertNull(control("desktop-primary-navigation",VBox.class).lookup("#nav-profile"));
                assertNull(control("mobile-navigation",HBox.class).lookup("#nav-profile"));assertNull(control("sidebar-scroll",ScrollPane.class).lookup("#nav-profile"));
                assertEquals(1,stage.getScene().getRoot().lookupAll("#nav-profile").size());
                assertTrue(profile.localToScene(profile.getBoundsInLocal()).getMaxY()<=logout.localToScene(logout.getBoundsInLocal()).getMinY()+1);
                if(System.getProperty("certiflow.snapshots")!=null&&role==AppData.Role.STUDENT&&(width==390||width==1366)){
                    button("navigation-toggle").fire();layout();capture("student-drawer-profile-footer-"+width);button("navigation-close").fire();
                }
                profile.fire();assertEquals(user.name(),control("profile-name",TextField.class).getText());
                var route=App.class.getDeclaredField("currentRoute");route.setAccessible(true);assertEquals(role==AppData.Role.STUDENT?"student-profile":role==AppData.Role.VIEWER?"viewer-profile":"staff-profile",route.get(null));
                button("return-button").fire();return null;
            });
        }
    }
    @Test void originalLogoLoadsAsBoundedProportionalImagesOnLoginAndApplicationPages() throws Exception {
        fx(()->{
            for(int width:new int[]{320,390,1366}){
                resize(width,844);assertLogo("auth-hero-logo",225,180);assertLogo("auth-compact-logo",40,40);assertHorizontalFit(stage.getScene().getRoot(),width);
            }return null;
        });
        var user=data.authenticate("account0@example.invalid","long-password-test").orElseThrow();
        fx(()->{App.completeLogin(user);for(int width:new int[]{320,390,1366}){
            resize(width,844);assertLogo("header-logo",32,32);assertLogo("sidebar-logo",32,32);
            assertSame(control("header-logo",ImageView.class).getImage(),control("sidebar-logo",ImageView.class).getImage());assertHorizontalFit(stage.getScene().getRoot(),width);
        }return null;});
    }
    @Test void chatPreservesContactDraftAndSendsUsingOnlyTheSelectedRecipient() throws Exception {
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        AppData.Contact teacher=data.contacts().stream().filter(contact->contact.id().equals(TestBackend.TEACHER_ID)).findFirst().orElseThrow();
        fx(()->{
            App.completeLogin(user);App.showMessages();
            ComboBox<AppData.Contact> people=control("messages-recipient",ComboBox.class);
            assertFalse(people.isEditable());people.setValue(teacher);
            control("message-body",TextArea.class).setText("Could we discuss my research project?");
            people.setValue(null);assertEquals("",control("message-body",TextArea.class).getText());
            people.setValue(teacher);assertEquals("Could we discuss my research project?",control("message-body",TextArea.class).getText());
            assertFalse(button("message-send").isDisabled());button("message-send").fire();return null;
        });
        await(()->label("messages-feedback").getText().equals("Message sent."));
        await(()->containsText(control("messages-list",VBox.class),"Could we discuss my research project?"));
        fx(()->{
            assertEquals(1,backend.messages.size());assertEquals(TestBackend.TEACHER_ID,backend.messages.get(0).get("recipient_id").getAsString());
            assertEquals("Could we discuss my research project?",backend.messages.get(0).get("body").getAsString());
            assertEquals("",control("message-body",TextArea.class).getText());assertTrue(button("message-send").isDisabled());
            control("message-body",TextArea.class).setText("x".repeat(4001));assertTrue(button("message-send").isDisabled());
            Parent oldPage=stage.getScene().getRoot();App.showHome();assertNull(oldPage.getScene());return null;
        });
    }
    @Test void recentChatLoadsOldHistoryAndOwnMessageEditingPreservesTheDraft() throws Exception {
        for(int i=1;i<=120;i++)backend.messages.add(TestBackend.messageRow(i,i%2==0?TestBackend.ID:TestBackend.TEACHER_ID,
            i%2==0?TestBackend.TEACHER_ID:TestBackend.ID,"History message "+i,"2026-11-05T06:00:00Z"));
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        fx(()->{App.completeLogin(user);App.showMessages();resize(390,844);return null;});
        await(()->stage.getScene().lookup("#conversation-"+TestBackend.TEACHER_ID)!=null);
        fx(()->{assertHorizontalFit(stage.getScene().getRoot(),390);capture("chat-inbox-populated-390");button("conversation-"+TestBackend.TEACHER_ID).fire();return null;});
        await(()->label("messages-history-status").getText().contains("50 messages loaded"));
        fx(()->{assertTrue(containsText(control("messages-list",VBox.class),"History message 120"));assertFalse(containsText(control("messages-list",VBox.class),"History message 1"));button("messages-load-older").fire();return null;});
        await(()->label("messages-history-status").getText().contains("100 messages loaded"));
        fx(()->{button("messages-load-older").fire();return null;});
        await(()->label("messages-history-status").getText().contains("120 messages loaded"));
        String ownId=backend.messages.get(119).get("id").getAsString(),receivedId=backend.messages.get(118).get("id").getAsString();
        fx(()->{
            assertTrue(containsText(control("messages-list",VBox.class),"History message 1"));assertFalse(button("messages-load-older").isVisible());
            assertNull(stage.getScene().lookup("#message-edit-"+receivedId));
            control("message-body",TextArea.class).setText("My unsent follow-up");button("message-edit-"+ownId).fire();
            assertEquals("History message 120",control("message-body",TextArea.class).getText());assertEquals("Save edit",button("message-send").getText());
            control("message-body",TextArea.class).setText("Discarded edit");button("message-edit-"+ownId).fire();
            assertEquals("Discarded edit",control("message-body",TextArea.class).getText());button("message-edit-cancel").fire();
            assertEquals("My unsent follow-up",control("message-body",TextArea.class).getText());
            button("message-edit-"+ownId).fire();control("message-body",TextArea.class).setText("Updated research question");button("message-send").fire();return null;
        });
        await(()->label("messages-feedback").getText().equals("Message updated."));
        fx(()->{
            assertEquals(120,backend.messages.size());assertEquals("Updated research question",backend.messages.get(119).get("body").getAsString());
            assertEquals("My unsent follow-up",control("message-body",TextArea.class).getText());
            assertTrue(containsText(control("messages-list",VBox.class),"Updated research question"));
            for(int width:new int[]{320,390,768,1366}){resize(width,844);assertHorizontalFit(stage.getScene().getRoot(),width);assertChatComposerInsideViewport();if(width==320||width==390||width==1366)capture("chat-conversation-populated-"+width);}
            return null;
        });
    }
    @Test void chatSettingsPersistForTheAccountAndAreAppliedOnReopening() throws Exception {
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        fx(()->{
            App.completeLogin(user);App.showMessages(TestBackend.TEACHER_ID);control("chat-settings",TitledPane.class).setExpanded(true);
            control("chat-enter-to-send",CheckBox.class).fire();control("chat-compact",CheckBox.class).fire();control("chat-large-text",CheckBox.class).fire();
            control("chat-show-previews",CheckBox.class).fire();control("chat-auto-scroll",CheckBox.class).fire();return null;
        });
        CountDownLatch saved=new CountDownLatch(1);Background.execute(saved::countDown);assertTrue(saved.await(10,TimeUnit.SECONDS));
        assertEquals(new ChatSettings(true,true,true,false,false),ChatSettings.load(directory,TestBackend.ID));
        fx(()->{
            App.showHome();App.showMessages(TestBackend.TEACHER_ID);control("chat-settings",TitledPane.class).setExpanded(true);
            assertTrue(control("chat-enter-to-send",CheckBox.class).isSelected());assertTrue(control("chat-compact",CheckBox.class).isSelected());assertTrue(control("chat-large-text",CheckBox.class).isSelected());
            assertFalse(control("chat-show-previews",CheckBox.class).isSelected());assertFalse(control("chat-auto-scroll",CheckBox.class).isSelected());
            resize(390,844);assertHorizontalFit(stage.getScene().getRoot(),390);capture("chat-settings-390");
            control("chat-settings",TitledPane.class).setExpanded(false);layout();assertChatComposerInsideViewport();return null;
        });
    }
    @Test void refreshingAfterALargeArrivalBurstKeepsSkippedHistoryReachable() throws Exception {
        for(int i=1;i<=60;i++)backend.messages.add(TestBackend.messageRow(i,TestBackend.ID,TestBackend.TEACHER_ID,"Earlier "+i,"2026-11-05T06:00:00Z"));
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        fx(()->{App.completeLogin(user);App.showMessages(TestBackend.TEACHER_ID);return null;});
        await(()->label("messages-history-status").getText().contains("50 messages loaded"));
        for(int i=61;i<=160;i++)backend.messages.add(TestBackend.messageRow(i,TestBackend.ID,TestBackend.TEACHER_ID,"Later "+i,"2026-11-05T06:10:00Z"));
        fx(()->{button("messages-refresh").fire();return null;});
        await(()->label("messages-feedback").getText().equals("Conversation refreshed."));
        fx(()->{assertTrue(containsText(control("messages-list",VBox.class),"Later 160"));button("messages-load-older").fire();return null;});
        await(()->label("messages-history-status").getText().contains("100 messages loaded"));
        fx(()->{assertTrue(containsText(control("messages-list",VBox.class),"Later 61"));button("messages-load-older").fire();return null;});
        await(()->label("messages-history-status").getText().contains("150 messages loaded"));
        fx(()->{button("messages-load-older").fire();return null;});
        await(()->label("messages-history-status").getText().contains("160 messages loaded"));
        fx(()->{assertTrue(containsText(control("messages-list",VBox.class),"Earlier 1"));return null;});
    }
    @Test void sharingPreviewStartsPrivateAndPublishesOnlyAfterExplicitAudienceAndFileChoice() throws Exception {
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        var project=data.createProject(user,"Research project","Project","Sensitive source description","https://example.invalid/private",TestBackend.TEACHER_ID);
        Path attachment=Files.writeString(directory.resolve("evidence.txt"),"Supporting evidence");var file=data.addPortfolio(user,"Evidence",attachment,project.id());
        Scene panel=fx(()->{App.completeLogin(user);return openHiddenSharingDialog((Button)SharingControls.forProject(project));});
        await(()->!shareControl(panel,"share-save",Button.class).isDisabled());
        fx(()->{
            assertEquals(0,shareControl(panel,"share-audience",ComboBox.class).getSelectionModel().getSelectedIndex());
            assertEquals("",shareControl(panel,"share-summary",TextArea.class).getText());assertEquals("",shareControl(panel,"share-link",TextField.class).getText());
            assertTrue(shareControl(panel,"share-copy",Button.class).isDisabled());assertTrue(backend.shares.isEmpty());
            assertFalse(containsText(shareControl(panel,"share-preview",VBox.class),"Sensitive source description"));
            var boxes=panel.getRoot().lookupAll(".check-box").stream().filter(CheckBox.class::isInstance).map(CheckBox.class::cast).toList();assertEquals(1,boxes.size());assertFalse(boxes.get(0).isSelected());
            shareControl(panel,"share-author",TextField.class).setText("Chosen public name");shareControl(panel,"share-summary",TextArea.class).setText("Only this chosen summary");
            shareControl(panel,"share-audience",ComboBox.class).getSelectionModel().select(3);boxes.get(0).setSelected(true);
            assertTrue(containsText(shareControl(panel,"share-preview",VBox.class),"Only this chosen summary"));
            assertTrue(containsText(shareControl(panel,"share-preview",VBox.class),"Included file: evidence.txt"));assertTrue(backend.shares.isEmpty());
            shareControl(panel,"share-save",Button.class).fire();return null;
        });
        await(()->shareControl(panel,"share-feedback",Label.class).getText().startsWith("Published exactly"));
        fx(()->{
            assertEquals(1,backend.shares.size());assertEquals("BOTH",backend.lastBody.get("audience").getAsString());
            assertEquals(file.id(),backend.lastBody.getAsJsonArray("file_ids").get(0).getAsString());assertFalse(shareControl(panel,"share-copy",Button.class).isDisabled());
            shareControl(panel,"share-audience",ComboBox.class).getSelectionModel().select(0);shareControl(panel,"share-save",Button.class).fire();return null;
        });
        await(()->shareControl(panel,"share-feedback",Label.class).getText().startsWith("Publication removed"));
        fx(()->{assertTrue(backend.shares.isEmpty());assertTrue(shareControl(panel,"share-copy",Button.class).isDisabled());return null;});
    }
    @Test void unavailableSharingDisablesSavingAndNocPrivateDetailsNeverFillThePreview() throws Exception {
        var user=data.authenticate(AppData.Role.STUDENT,"account0@example.invalid","long-password-test").orElseThrow();
        var request=data.submit(user,"Scholarship","Private personal application reason",TestBackend.TEACHER_ID);backend.sharingUnavailable=true;
        Scene panel=fx(()->{App.completeLogin(user);return openHiddenSharingDialog((Button)SharingControls.forRequest(request));});
        await(()->shareControl(panel,"share-feedback",Label.class).getText().startsWith("Could not load the current sharing choice"));
        fx(()->{
            assertTrue(shareControl(panel,"share-save",Button.class).isDisabled());assertTrue(shareControl(panel,"share-copy",Button.class).isDisabled());assertTrue(shareControl(panel,"share-open",Button.class).isDisabled());
            assertEquals("",shareControl(panel,"share-summary",TextArea.class).getText());
            assertFalse(containsText(shareControl(panel,"share-preview",VBox.class),"Private personal application reason"));
            assertFalse(containsText(shareControl(panel,"share-preview",VBox.class),"account0@example.invalid"));
            shareControl(panel,"share-audience",ComboBox.class).getSelectionModel().select(3);shareControl(panel,"share-save",Button.class).fire();
            assertTrue(backend.shares.isEmpty());assertTrue(backend.calls.stream().noneMatch(call->call.endsWith("/publish_share")||call.endsWith("/unpublish_share")));return null;
        });
    }
    private Scene openHiddenSharingDialog(Button launch) {
        stage.setWidth(1200);stage.setHeight(800);launch.fire();
        var window=javafx.stage.Window.getWindows().stream().filter(w->w!=stage&&w.getScene()!=null&&w.getScene().lookup("#share-preview")!=null).findFirst().orElseThrow();
        Scene scene=window.getScene();window.hide();return scene;
    }
    private static <T extends Node>T shareControl(Scene scene,String id,Class<T> type) {
        Parent root=scene.getRoot();root.applyCss();root.resize(640,720);root.layout();Node node=scene.lookup("#"+id);assertNotNull(node,id);return type.cast(node);
    }
    private void assertChatComposerInsideViewport() {
        layout();double bottom=stage.getScene().getHeight();Node navigation=stage.getScene().lookup("#mobile-navigation");
        if(navigation!=null&&navigation.isVisible()&&navigation.isManaged())bottom=Math.min(bottom,navigation.localToScene(navigation.getBoundsInLocal()).getMinY());
        for(Node node:List.of(control("message-body",TextArea.class),button("message-send"))){
            assertTrue(node.isVisible()&&node.isManaged(),node.getId()+" must be visible");
            var bounds=node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinY()>=-1&&bounds.getMaxY()<=bottom+1,node.getId()+" must fit above navigation: "+bounds+" bottom="+bottom);
        }
    }
    private void assertLogo(String id,double maxWidth,double maxHeight){
        ImageView logo=control(id,ImageView.class);var image=logo.getImage();assertNotNull(image,id);assertFalse(image.isError(),id);assertNotNull(image.getPixelReader(),id);
        assertTrue(image.getWidth()>0&&image.getHeight()>0,id);assertTrue(logo.isPreserveRatio(),id);
        assertTrue(logo.getFitWidth()>0&&logo.getFitWidth()<=maxWidth&&logo.getFitHeight()>0&&logo.getFitHeight()<=maxHeight,id);
        var viewport=logo.getViewport();assertNotNull(viewport,id);assertTrue(viewport.getWidth()>0&&viewport.getHeight()>0,id);
        assertTrue(viewport.getMinX()>=0&&viewport.getMinY()>=0&&viewport.getMaxX()<=image.getWidth()&&viewport.getMaxY()<=image.getHeight(),id);
        var bounds=logo.getBoundsInLocal();assertTrue(bounds.getWidth()<=maxWidth+1&&bounds.getHeight()<=maxHeight+1,id);
        assertEquals(viewport.getWidth()/viewport.getHeight(),bounds.getWidth()/bounds.getHeight(),0.02,id+" must preserve the original artwork proportions");
    }
    private record PageCase(String name,Runnable open) {}
    private void capture(String name) throws Exception {
        String output=System.getProperty("certiflow.snapshots");if(output==null)return;
        var image=stage.getScene().getRoot().snapshot(null,null);
        var buffered=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[(int)image.getWidth()*(int)image.getHeight()];
        image.getPixelReader().getPixels(0,0,(int)image.getWidth(),(int)image.getHeight(),javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,(int)image.getWidth());
        buffered.setRGB(0,0,(int)image.getWidth(),(int)image.getHeight(),pixels,0,(int)image.getWidth());
        Files.createDirectories(Path.of(output));javax.imageio.ImageIO.write(buffered,"png",Path.of(output,name+".png").toFile());
    }
    private static boolean containsText(Node node,String text) {
        if(node instanceof Labeled label&&text.equals(label.getText()))return true;
        if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())if(containsText(child,text))return true;
        return false;
    }
    @Test void capturesCurrentLoginAtLaptopAndPhoneSizes() throws Exception {
        String output=System.getProperty("certiflow.snapshots");if(output==null)return;
        fx(()->{App.showLoginView("Admin","");for(int width:new int[]{390,1280}){resize(width,844);var image=stage.getScene().getRoot().snapshot(null,null);
            var buffered=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            int[] pixels=new int[(int)image.getWidth()*(int)image.getHeight()];image.getPixelReader().getPixels(0,0,(int)image.getWidth(),(int)image.getHeight(),javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,(int)image.getWidth());
            buffered.setRGB(0,0,(int)image.getWidth(),(int)image.getHeight(),pixels,0,(int)image.getWidth());Files.createDirectories(Path.of(output));javax.imageio.ImageIO.write(buffered,"png",Path.of(output,"login-"+width+".png").toFile());}return null;});
    }
    private void resize(double width,double height){ Parent root=stage.getScene().getRoot();stage.getScene().setRoot(new VBox());Scene scene=new Scene(root,width,height);scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());scene.getStylesheets().add(App.class.getResource("/auth.css").toExternalForm());scene.getStylesheets().add(App.class.getResource("/chat.css").toExternalForm());stage.setScene(scene);root.applyCss();root.resize(width,height);root.layout(); }
    private static void assertHorizontalFit(Node node,double width){if(!node.isVisible()||!node.isManaged()||node.getId()!=null&&node.getId().equals("desktop-sidebar"))return;if(node instanceof Control){var b=node.localToScene(node.getBoundsInLocal());if(b.getMaxX()>0&&b.getMinX()<width)assertTrue(b.getMaxX()<=width+2,"Overflow: "+node.getId()+" "+b);}if(node instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())assertHorizontalFit(child,width);}
    private static void assertNoDemoText(Node node){if(node instanceof Labeled l){String s=l.getText()==null?"":l.getText().toLowerCase();assertFalse(s.contains("demo")||s.contains("sarah")||s.contains("student123"),s);}if(node instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())assertNoDemoText(child);}
    private void await(BooleanSupplier check)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<until){if(fx(()->{layout();return check.getAsBoolean();}))return;Thread.sleep(25);}fail("Timed out waiting for background operation");}
    private Button button(String id){return control(id,Button.class);}private Label label(String id){return control(id,Label.class);}
    private void layout(){Parent root=stage.getScene().getRoot();root.applyCss();root.resize(stage.getScene().getWidth(),stage.getScene().getHeight());root.layout();}
    private <T extends Node>T control(String id,Class<T> type){layout();Node n=stage.getScene().lookup("#"+id);assertNotNull(n,id);return type.cast(n);}
    private static void field(String name,Object value)throws Exception{var f=App.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
}
