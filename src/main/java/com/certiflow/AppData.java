package com.certiflow;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Data operations used by the JavaFX views: validate input, call Supabase, and keep
 * immutable snapshots for rendering. Call network methods through {@link Background}.
 * Client checks give useful errors; database row policies and RPCs enforce access.
 * No sample accounts, records, or offline authentication are supplied here.
 */
public final class AppData {
    // Immutable records mirror the server's account, application and collaboration data.
    public enum Role {
        STUDENT, VIEWER, TEACHER, ADMIN;
        public boolean isStaff() { return this == TEACHER || this == ADMIN; }
    }
    public enum Status { PENDING, APPROVED, REJECTED }
    public static final List<String> REQUEST_TYPES = List.of("Library Clearance", "Laboratory Access", "Sports Equipment", "Internship",
        "Hostel Clearance", "Fee Clearance", "Transfer Certificate", "Migration Certificate", "Bonafide Certificate", "Character Certificate",
        "Transcript", "Scholarship", "Exam Permission", "Event Participation", "Leave Application", "Research Project", "Other");
    public static final List<String> PROJECT_KINDS = List.of("Project", "Certificate", "Achievement", "Experience", "Document");
    public record User(String id, String username, String name, Role role, String department, String email, String studentNumber,
        String bio, String skills, String programme, String website, boolean identityVerified) {
        public User(String id,String username,String name,Role role,String department,String email,String studentNumber) { this(id,username,name,role,department,email,studentNumber,"","","","",false); }
        public User(String id,String username,String name,Role role,String department,String email) { this(id,username,name,role,department,email,""); }
        public String displayId() { return studentNumber == null || studentNumber.isBlank() ? "Not provided" : studentNumber; }
    }
    public record NocRequest(String id, String studentId, String studentName, String type, String reason,
        Status status, LocalDateTime submittedAt, LocalDateTime decidedAt, String decisionNote, String recipientId) {
        public NocRequest(String id,String studentId,String studentName,String type,String reason,Status status,LocalDateTime submittedAt,LocalDateTime decidedAt,String decisionNote) { this(id,studentId,studentName,type,reason,status,submittedAt,decidedAt,decisionNote,""); }
    }
    public record PortfolioItem(String id, String studentId, String title, String fileName, String storedName, LocalDateTime addedAt, String projectId) {
        public PortfolioItem(String id,String studentId,String title,String fileName,String storedName,LocalDateTime addedAt) { this(id,studentId,title,fileName,storedName,addedAt,""); }
    }
    public record Project(String id,String studentId,String title,String kind,String description,String link,String recipientId,LocalDateTime createdAt) {}
    public record Contact(String id,String name,Role role,String department) {
        @Override public String toString() { return name + " · " + Ui.roleName(role) + (department.isBlank() ? "" : " · " + department); }
    }
    public record ChatMessage(String id,String senderId,String recipientId,String body,LocalDateTime sentAt,LocalDateTime readAt,LocalDateTime editedAt) {
        public ChatMessage(String id,String senderId,String recipientId,String body,LocalDateTime sentAt,LocalDateTime readAt) { this(id,senderId,recipientId,body,sentAt,readAt,null); }
    }
    public record Conversation(String contactId,String name,Role role,String department,String lastMessage,LocalDateTime lastSentAt,int unreadCount,boolean canSend) {}
    public enum RemarkSource { NOC, PROJECT }
    public record PrivateRemark(String id,String studentId,String authorId,String authorName,String body,LocalDateTime createdAt) {}

    public List<PrivateRemark> privateRemarks(RemarkSource source,String sourceId,int offset) throws IOException {
        // Load remarks separately from the main snapshots; they never feed public sharing.
        User actor=requireCurrent();
        if(actor.role()==Role.VIEWER) throw new IllegalArgumentException("Private remarks are not available to viewers.");
        Objects.requireNonNull(source,"Choose an item type.");
        if(offset<0) throw new IllegalArgumentException("Invalid remark page.");
        String field=source==RemarkSource.NOC?"request_id":"project_id";
        JsonArray rows=service().json("GET","/rest/v1/private_remarks?select=*&"+field+"=eq."+UUID.fromString(sourceId)
            +"&order=created_at.desc,id.desc&limit=50&offset="+offset,null,true).getAsJsonArray();
        List<PrivateRemark> result=new ArrayList<>();
        for(JsonElement row:rows) result.add(remark(row.getAsJsonObject()));
        return List.copyOf(result);
    }
    public PrivateRemark addPrivateRemark(RemarkSource source,String sourceId,String body) throws IOException {
        if(!requireCurrent().role().isStaff()) throw new IllegalArgumentException("Only teachers and administrators can send private remarks.");
        Objects.requireNonNull(source,"Choose an item type.");
        // The RPC derives the sender and student from the session and source, not client IDs.
        return remark(one(service().json("POST","/rest/v1/rpc/add_private_remark",Map.of("source_kind",source.name(),
            "source_id",UUID.fromString(sourceId).toString(),"remark_text",required(body,"Remark",2000)),true)));
    }
    private static PrivateRemark remark(JsonObject row) { return new PrivateRemark(text(row,"id"),text(row,"student_id"),
        text(row,"author_id"),text(row,"author_name"),text(row,"body"),date(row,"created_at")); }
    private final Path directory;
    private final SupabaseApi api;
    // Replace lists instead of mutating them while JavaFX renders the previous snapshot.
    private volatile User current;
    private volatile List<User> users = List.of();
    private volatile List<NocRequest> requests = List.of();
    private volatile List<PortfolioItem> portfolios = List.of();
    private volatile List<Project> projects = List.of();
    private volatile List<Contact> contacts = List.of();
    private volatile boolean canManageAdmins;
    private volatile boolean adminCapabilitiesAvailable;
    private static final Set<String> EXTENSIONS = Set.of("pdf","png","jpg","jpeg","gif","webp","txt");

    public AppData(Path directory) throws IOException { this(directory, loadApi()); }
    AppData(Path directory, SupabaseApi api) throws IOException {
        this.directory = directory.toAbsolutePath().normalize(); this.api = api;
        Files.createDirectories(this.directory);
    }
    private static SupabaseApi loadApi() throws IOException { CloudConfig config = CloudConfig.load(); return config == null ? null : new SupabaseApi(config); }
    public boolean configured() { return api != null; }
    private SupabaseApi service() { if (api == null) throw new IllegalStateException("Certiflow is not connected yet. Ask your administrator to finish database setup."); return api; }
    SupabaseApi cloudApi() { requireCurrent(); return service(); }
    /** Compatibility entry point: a caller-supplied role never selects or grants access. */
    public Optional<User> authenticate(Role role, String email, String password) throws IOException {
        return authenticate(email, password);
    }
    public Optional<User> authenticate(String email, String password) throws IOException {
        logoutLocal();
        email = email(email);
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Enter your email and password.");
        service().signIn(email,password);
        try {
            // The verified server profile, not the login form, decides the workspace and role.
            List<User> accounts = readUsers("&id=eq." + UUID.fromString(api.userId()));
            if (accounts.size() != 1) throw new IOException("Your account profile is missing. Contact your administrator.");
            User user = accounts.get(0);
            current = user; refresh(); return Optional.of(current);
        } catch (IOException | RuntimeException ex) { logoutLocal(); throw ex; }
    }
    public void register(String email, String password, String name, String department, String number) throws IOException {
        register(email,password,name,department,number,Role.STUDENT);
    }
    public void register(String email, String password, String name, String department, String number, Role basicRole) throws IOException {
        if (basicRole != Role.STUDENT && basicRole != Role.VIEWER) throw new IllegalArgumentException("Choose Student or Recruiter / Viewer. Staff access is assigned by an administrator.");
        service().signUp(email(email), password(password), required(name,"Full name",120),
            basicRole == Role.VIEWER ? optional(department,120) : required(department,"Department",120), optional(number,80), basicRole.name());
    }
    public void verifyEmail(String email,String code) throws IOException { service().verifySignup(email(email),required(code,"Verification code",12)); }
    public void resendConfirmation(String email) throws IOException { service().resendConfirmation(email(email)); }
    public void requestRecovery(String email) throws IOException { service().requestRecovery(email(email)); }
    public void resetPassword(String email,String code,String password) throws IOException { service().resetPassword(email(email),required(code,"Recovery code",12),password(password)); }
    public void logout() { if (api != null) api.signOut(); logoutLocal(); }
    public void logoutLocal() { current = null; users = List.of(); requests = List.of(); portfolios = List.of(); projects = List.of(); contacts = List.of(); canManageAdmins = false; adminCapabilitiesAvailable = false; if (api != null) api.clearSession(); }
    public User currentUser() { return current; }
    public boolean canManageAdmins() { return current != null && current.role() == Role.ADMIN && canManageAdmins; }
    public boolean adminCapabilitiesAvailable() { return adminCapabilitiesAvailable; }
    /** Fetch only rows visible under the current session's server policies. */
    public void refresh() throws IOException {
        User account = requireCurrent();
        List<User> nextUsers = readUsers("");
        List<NocRequest> nextRequests = all("requests", "submitted_at.desc,id.asc").stream().map(AppData::request).toList();
        List<PortfolioItem> nextPortfolios = all("portfolios", "added_at.desc,id.asc").stream().map(AppData::portfolio).toList();
        List<Project> nextProjects = all("portfolio_projects", "created_at.desc,id.asc").stream().map(AppData::project).toList();
        List<Contact> nextContacts = all("rpc/contact_directory", "full_name.asc,id.asc").stream().map(AppData::contact).toList();
        User nextCurrent = nextUsers.stream().filter(u -> u.id().equals(account.id())).findFirst().orElse(account);
        boolean nextCanManageAdmins = false, nextCapabilitiesAvailable = false;
        if (nextCurrent.role() == Role.ADMIN) {
            try {
                JsonObject capabilities = service().json("GET", "/rest/v1/rpc/account_capabilities", null, true).getAsJsonObject();
                JsonElement value = capabilities.get("can_manage_admins");
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                    nextCanManageAdmins = value.getAsBoolean(); nextCapabilitiesAvailable = true;
                }
            } catch (IOException | RuntimeException ignored) { /* Privileged controls stay unavailable until a successful refresh. */ }
        }
        // Discard a response if logout or another sign-in replaced the account during the fetch.
        if (current != account) return;
        users = nextUsers; requests = nextRequests; portfolios = nextPortfolios; projects = nextProjects; contacts = nextContacts;
        current = nextCurrent; canManageAdmins = nextCanManageAdmins; adminCapabilitiesAvailable = nextCapabilitiesAvailable;
    }
    public List<User> users() { return users; }
    public List<NocRequest> requests() { return requests; }
    public List<Project> projects() { return projects; }
    public List<Project> projectsFor(String id) { return projects.stream().filter(p -> p.studentId().equals(id)).toList(); }
    public List<PortfolioItem> portfolios() { return portfolios; }
    public List<Contact> contacts() { return contacts; }
    public String contactName(String id) { return contacts.stream().filter(c -> c.id().equals(id)).map(Contact::name).findFirst()
        .orElseGet(() -> users.stream().filter(u -> u.id().equals(id)).map(User::name).findFirst().orElse("Assigned staff member")); }
    public List<NocRequest> requestsFor(String id) { return requests.stream().filter(r -> r.studentId().equals(id)).toList(); }
    public List<PortfolioItem> portfoliosFor(String id) { return portfolios.stream().filter(p -> p.studentId().equals(id)).toList(); }
    public NocRequest submit(User user,String type,String reason) throws IOException {
        return submit(user,type,reason,"");
    }
    public NocRequest submit(User user,String type,String reason,String recipientId) throws IOException {
        requireStudent(user);
        if (!REQUEST_TYPES.contains(type)) throw new IllegalArgumentException("Choose a request type.");
        Map<String,Object> body = new LinkedHashMap<>(); body.put("type",type); body.put("reason",required(reason,"Reason",2000));
        String recipient = recipient(recipientId); if (!recipient.isEmpty()) body.put("recipient_id",recipient);
        JsonArray response = service().json("POST","/rest/v1/requests",body,true).getAsJsonArray();
        NocRequest result = request(response.get(0).getAsJsonObject());
        List<NocRequest> next = new ArrayList<>(requests); next.add(0,result); requests = List.copyOf(next); return result;
    }
    public void decide(User user,String id,Status decision,String note) throws IOException {
        requireUser(user);
        if (!user.role().isStaff()) throw new IllegalArgumentException("Only staff can review applications.");
        if (decision != Status.APPROVED && decision != Status.REJECTED) throw new IllegalArgumentException("Choose Approve or Reject.");
        String cleanNote = decision == Status.REJECTED ? required(note,"Rejection reason",2000) : optional(note,2000);
        // The RPC checks the assigned reviewer and performs the status transition atomically.
        NocRequest result = request(one(service().json("POST","/rest/v1/rpc/decide_request",Map.of("request_id",UUID.fromString(id).toString(),"decision",decision.name(),"note",cleanNote),true)));
        requests = requests.stream().map(r -> r.id().equals(result.id()) ? result : r).toList();
    }
    public User updateProfile(String name,String department) throws IOException {
        User account = requireCurrent();
        JsonArray response = service().json("PATCH","/rest/v1/profiles?id=eq." + UUID.fromString(account.id()),
            Map.of("full_name",required(name,"Full name",120),"department",account.role()==Role.VIEWER?optional(department,120):required(department,"Department",120)),true).getAsJsonArray();
        current = user(response.get(0).getAsJsonObject());
        users = users.stream().map(u -> u.id().equals(current.id()) ? current : u).toList(); return current;
    }
    public User updateProfile(String name,String department,String number,String bio,String skills,String programme,String website) throws IOException {
        User account = requireCurrent();
        Map<String,Object> body = Map.of("full_name",required(name,"Full name",120),"department",account.role()==Role.VIEWER?optional(department,120):required(department,"Department",120),
            "student_number",optional(number,80),"bio",optional(bio,1000),"skills",optional(skills,300),"programme",optional(programme,120),"website",webLink(website));
        current = user(one(service().json("PATCH","/rest/v1/profiles?id=eq." + UUID.fromString(account.id()),body,true)));
        users = users.stream().map(u -> u.id().equals(current.id()) ? current : u).toList(); return current;
    }
    public User setAccountRole(String id,Role role) throws IOException {
        User account = requireAdmin(); String target = UUID.fromString(id).toString();
        if (account.id().equals(target) || role == null) throw new IllegalArgumentException("Choose access for another account. You cannot change your own access.");
        boolean targetAdmin = users.stream().anyMatch(user -> user.id().equals(target) && user.role() == Role.ADMIN);
        if ((role == Role.ADMIN || targetAdmin) && !canManageAdmins()) throw new IllegalArgumentException("Only the original administrator can grant or remove administrator access.");
        // Privilege changes go through the audited server function, never a profile PATCH.
        User result = user(one(service().json("POST","/rest/v1/rpc/set_account_role",Map.of("target_id",target,"new_role",role.name()),true)));
        replaceUser(result); refresh(); return result;
    }
    public User verifyIdentity(String id) throws IOException {
        requireAdmin();
        User result = user(one(service().json("POST","/rest/v1/rpc/verify_identity",Map.of("target_id",UUID.fromString(id).toString()),true)));
        replaceUser(result); return result;
    }
    private void replaceUser(User result) { users = users.stream().map(u -> u.id().equals(result.id()) ? result : u).toList(); if (current.id().equals(result.id())) current=result; }
    private User requireAdmin() { User account=requireCurrent(); if(account.role()!=Role.ADMIN)throw new IllegalArgumentException("Only an administrator can manage account access.");return account; }

    public Project createProject(User user,String title,String kind,String description,String link,String recipientId) throws IOException {
        requireStudent(user);
        Project result=project(one(service().json("POST","/rest/v1/portfolio_projects",projectBody(title,kind,description,link,recipientId),true)));
        List<Project> next=new ArrayList<>(projects);next.add(0,result);projects=List.copyOf(next);return result;
    }
    public Project updateProject(Project project,String title,String kind,String description,String link,String recipientId) throws IOException {
        ownProject(project);
        Project result=project(one(service().json("PATCH","/rest/v1/portfolio_projects?id=eq."+UUID.fromString(project.id()),projectBody(title,kind,description,link,recipientId),true)));
        projects=projects.stream().map(p->p.id().equals(result.id())?result:p).toList();return result;
    }
    private Map<String,Object> projectBody(String title,String kind,String description,String link,String recipientId) {
        if(!PROJECT_KINDS.contains(kind))throw new IllegalArgumentException("Choose an entry type.");
        Map<String,Object> body=new LinkedHashMap<>();body.put("title",required(title,"Title",150));body.put("kind",kind);
        body.put("description",optional(description,4000));body.put("link",webLink(link));String id=recipient(recipientId);body.put("recipient_id",id.isEmpty()?null:id);return body;
    }
    private void ownProject(Project project) {
        User user=requireCurrent();requireStudent(user);
        if(project==null || !user.id().equals(project.studentId()) || projects.stream().noneMatch(p->p.id().equals(project.id())))throw new IllegalArgumentException("Choose one of your portfolio entries.");
    }
    public void deleteProject(Project project) throws IOException {
        ownProject(project);
        List<PortfolioItem> attachments=portfolios.stream().filter(p->p.projectId().equals(project.id())).toList();
        // Remove database access first. Storage cleanup is a separate operation and may fail.
        one(service().json("DELETE","/rest/v1/portfolio_projects?id=eq."+UUID.fromString(project.id()),null,true));
        projects=projects.stream().filter(p->!p.id().equals(project.id())).toList();
        portfolios=portfolios.stream().filter(p->!p.projectId().equals(project.id())).toList();
        if(!attachments.isEmpty())try { service().json("DELETE","/storage/v1/object/portfolios",Map.of("prefixes",attachments.stream().map(PortfolioItem::storedName).toList()),true); }
        catch(IOException ex){ throw new IOException("Entry removed, but some stored files could not be cleaned up. Ask your administrator to remove the orphaned files.",ex); }
    }
    public PortfolioItem addPortfolio(User user,String title,Path source) throws IOException {
        return addPortfolio(user,title,source,"");
    }
    public PortfolioItem addPortfolio(User user,String title,Path source,String projectId) throws IOException {
        requireStudent(user); String cleanTitle = required(title,"Document title",150);
        String entryId=optional(projectId,36);
        if(!entryId.isEmpty()) ownProject(projects.stream().filter(p->p.id().equals(entryId)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose one of your portfolio entries.")));
        if (source == null || !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Choose a regular document file.");
        long size = Files.size(source);
        if (size < 1 || size > 25L*1024*1024) throw new IllegalArgumentException("Choose a non-empty file no larger than 25 MB.");
        String filename = source.getFileName().toString(); String ext = extension(filename);
        byte[] bytes;
        try (var input = Files.newInputStream(source)) { bytes = input.readNBytes(25*1024*1024+1); }
        if (bytes.length > 25*1024*1024) throw new IOException("The file grew beyond the 25 MB limit.");
        String stored = UUID.fromString(user.id()) + "/" + UUID.randomUUID() + "." + ext;
        String objectPath = "/storage/v1/object/portfolios/" + stored;
        service().binary("POST",objectPath,bytes,mime(ext));
        try {
            Map<String,Object> body=new LinkedHashMap<>();body.put("title",cleanTitle);body.put("file_name",filename);body.put("stored_name",stored);
            if(!entryId.isEmpty())body.put("project_id",entryId);
            JsonArray response = service().json("POST","/rest/v1/portfolios",body,true).getAsJsonArray();
            PortfolioItem result = portfolio(response.get(0).getAsJsonObject());
            List<PortfolioItem> next = new ArrayList<>(portfolios); next.add(0,result); portfolios = List.copyOf(next); return result;
        } catch (IOException | RuntimeException ex) {
            // Storage and Postgres cannot share a transaction; undo an unregistered upload.
            try { service().json("DELETE","/storage/v1/object/portfolios",Map.of("prefixes",List.of(stored)),true); }
            catch (Exception cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }
    public Path downloadPortfolio(PortfolioItem item) throws IOException {
        requireCurrent();
        if (!portfolios.contains(item)) throw new IllegalArgumentException("This document is not shared with your account.");
        Path path = portfolioPath(item);
        byte[] bytes = service().binary("GET","/storage/v1/object/authenticated/portfolios/" + item.storedName(),new byte[0],"application/octet-stream");
        Files.createDirectories(path.getParent()); Files.write(path,bytes); return path;
    }
    public Path portfolioPath(PortfolioItem item) {
        // Server storage names are UUID paths, never arbitrary local filenames or traversal.
        if (!item.storedName().matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.(pdf|png|jpg|jpeg|gif|webp|txt)")) throw new IllegalArgumentException("Invalid document path.");
        return directory.resolve("downloads").resolve(item.storedName());
    }
    public Path dataDirectory() { return directory; }
    public PortfolioItem updatePortfolio(PortfolioItem item,String title) throws IOException {
        ownAttachment(item);
        PortfolioItem result=portfolio(one(service().json("PATCH","/rest/v1/portfolios?id=eq."+UUID.fromString(item.id()),Map.of("title",required(title,"Document title",150)),true)));
        portfolios=portfolios.stream().map(p->p.id().equals(result.id())?result:p).toList();return result;
    }
    public void removePortfolio(PortfolioItem item) throws IOException {
        ownAttachment(item);
        one(service().json("DELETE","/rest/v1/portfolios?id=eq."+UUID.fromString(item.id()),null,true));
        portfolios=portfolios.stream().filter(p->!p.id().equals(item.id())).toList();
        try { service().json("DELETE","/storage/v1/object/portfolios",Map.of("prefixes",List.of(item.storedName())),true); }
        catch(IOException ex){ throw new IOException("Attachment removed, but the stored file could not be cleaned up. Ask your administrator to remove the orphaned file.",ex); }
    }
    private void ownAttachment(PortfolioItem item) { User user=requireCurrent();requireStudent(user);if(item==null || !item.studentId().equals(user.id()) || !portfolios.contains(item))throw new IllegalArgumentException("Choose one of your attachments."); }
    public List<ChatMessage> conversation(String contactId) throws IOException {
        User account=requireCurrent();String other=historyContact(contactId);
        String filter="or=(and(sender_id.eq."+account.id()+",recipient_id.eq."+other+"),and(sender_id.eq."+other+",recipient_id.eq."+account.id()+"))";
        JsonArray response=service().json("GET","/rest/v1/messages?select=*&"+filter+"&order=sent_at.desc,id.desc&limit=100",null,true).getAsJsonArray();
        List<ChatMessage> messages=new ArrayList<>();for(JsonElement row:response)messages.add(message(row.getAsJsonObject()));Collections.reverse(messages);return List.copyOf(messages);
    }
    public ChatMessage sendMessage(String contactId,String body) throws IOException {
        requireCurrent();String other=chatContact(contactId);
        return message(one(service().json("POST","/rest/v1/messages",Map.of("recipient_id",other,"body",required(body,"Message",4000)),true)));
    }
    public List<Conversation> conversations() throws IOException {
        requireCurrent();
        return all("rpc/chat_inbox","last_sent_at.desc,contact_id.asc").stream().map(o -> new Conversation(text(o,"contact_id"),text(o,"full_name"),
            Role.valueOf(text(o,"role")),text(o,"department"),text(o,"last_message"),date(o,"last_sent_at"),o.get("unread_count").getAsInt(),o.get("can_send").getAsBoolean())).toList();
    }
    public List<ChatMessage> conversationPage(String contactId,String beforeId,int pageSize) throws IOException {
        String other=historyContact(contactId);
        if(pageSize<1 || pageSize>100) throw new IllegalArgumentException("Load between 1 and 100 messages at a time.");
        Map<String,Object> body=new LinkedHashMap<>();body.put("other_id",other);body.put("before_id",beforeId==null||beforeId.isBlank()?null:UUID.fromString(beforeId).toString());body.put("page_size",pageSize);
        JsonArray result=service().json("POST","/rest/v1/rpc/chat_history",body,true).getAsJsonArray();
        // The server pages newest first; the conversation renders oldest first within a page.
        List<ChatMessage> rows=new ArrayList<>();for(JsonElement row:result)rows.add(message(row.getAsJsonObject()));Collections.reverse(rows);return List.copyOf(rows);
    }
    public ChatMessage editMessage(ChatMessage original,String body) throws IOException {
        User account=requireCurrent();
        if(original==null || !account.id().equals(original.senderId()))throw new IllegalArgumentException("You can edit only your own messages.");
        // original_body lets the server detect a concurrent edit instead of overwriting it.
        return message(one(service().json("POST","/rest/v1/rpc/edit_message",Map.of("message_id",UUID.fromString(original.id()).toString(),
            "new_body",required(body,"Message",4000),"original_body",original.body()),true)));
    }
    public void markMessagesRead(String contactId) throws IOException { service().json("POST","/rest/v1/rpc/mark_messages_read",Map.of("other_id",historyContact(contactId)),true); }
    private String historyContact(String id) { User account=requireCurrent();String other=UUID.fromString(id).toString();if(other.equals(account.id()))throw new IllegalArgumentException("Choose another person.");return other; }
    public int unreadMessageCount() throws IOException {
        User account=requireCurrent();int total=0;
        while(true){JsonArray page=service().json("GET","/rest/v1/messages?select=id&recipient_id=eq."+account.id()+"&read_at=is.null&order=id.asc&limit=500&offset="+total,null,true).getAsJsonArray();total+=page.size();if(page.size()<500)return total;}
    }
    private String chatContact(String id) {
        // Sending requires a current eligible contact; old history remains separately readable.
        User account=requireCurrent();String clean=UUID.fromString(id).toString();
        Contact target=contacts.stream().filter(c->c.id().equals(clean)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a registered contact."));
        if(clean.equals(account.id()) || (!account.role().isStaff() && !target.role().isStaff()))throw new IllegalArgumentException("Students and viewers can message teachers and administrators.");return clean;
    }
    private String recipient(String id) {
        String clean=optional(id,36);if(clean.isEmpty())return "";clean=UUID.fromString(clean).toString();String target=clean;
        if(contacts.stream().noneMatch(c->c.id().equals(target)&&c.role().isStaff()))throw new IllegalArgumentException("Choose a registered teacher or administrator.");return clean;
    }
    static String webLink(String value) {
        String clean=optional(value,500);if(clean.isEmpty())return clean;
        try{java.net.URI uri=java.net.URI.create(clean);if(!Set.of("https","http").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getUserInfo()!=null)throw new IllegalArgumentException();}
        catch(IllegalArgumentException ex){throw new IllegalArgumentException("Use a full http:// or https:// link, or leave it blank.");}return clean;
    }
    private User requireCurrent() { if (current == null) throw new IllegalStateException("Please sign in again."); return current; }
    private void requireUser(User user) { if (user == null || !user.equals(requireCurrent())) throw new IllegalArgumentException("Please sign in again."); }
    private void requireStudent(User user) { requireUser(user); if (user.role() != Role.STUDENT) throw new IllegalArgumentException("Only students can submit applications and documents."); }
    private List<User> readUsers(String filter) throws IOException {
        if (!filter.isEmpty()) return service().json("GET","/rest/v1/profiles?select=*"+filter,null,true).getAsJsonArray().asList().stream().map(e -> user(e.getAsJsonObject())).toList();
        return all("profiles","id.asc").stream().map(AppData::user).toList();
    }
    private List<JsonObject> all(String table,String order) throws IOException {
        // Stable ordering and pagination avoid silently truncating lists at the API row limit.
        List<JsonObject> result = new ArrayList<>(); int offset = 0;
        while (true) {
            JsonArray page = service().json("GET","/rest/v1/"+table+"?select=*&order="+order+"&limit=500&offset="+offset,null,true).getAsJsonArray();
            for (JsonElement row : page) result.add(row.getAsJsonObject());
            if (page.size() < 500) return result;
            offset += page.size();
        }
    }
    private static User user(JsonObject o) { return new User(text(o,"id"),text(o,"email"),text(o,"full_name"),Role.valueOf(text(o,"role")),text(o,"department"),text(o,"email"),text(o,"student_number"),text(o,"bio"),text(o,"skills"),text(o,"programme"),text(o,"website"),o.has("identity_verified")&&!o.get("identity_verified").isJsonNull()&&o.get("identity_verified").getAsBoolean()); }
    private static NocRequest request(JsonObject o) { return new NocRequest(text(o,"id"),text(o,"student_id"),text(o,"student_name"),text(o,"type"),text(o,"reason"),Status.valueOf(text(o,"status")),date(o,"submitted_at"),date(o,"decided_at"),text(o,"decision_note"),text(o,"recipient_id")); }
    private static PortfolioItem portfolio(JsonObject o) { return new PortfolioItem(text(o,"id"),text(o,"student_id"),text(o,"title"),text(o,"file_name"),text(o,"stored_name"),date(o,"added_at"),text(o,"project_id")); }
    private static Project project(JsonObject o) { return new Project(text(o,"id"),text(o,"student_id"),text(o,"title"),text(o,"kind"),text(o,"description"),text(o,"link"),text(o,"recipient_id"),date(o,"created_at")); }
    private static Contact contact(JsonObject o) { return new Contact(text(o,"id"),text(o,"full_name"),Role.valueOf(text(o,"role")),text(o,"department")); }
    private static ChatMessage message(JsonObject o) { return new ChatMessage(text(o,"id"),text(o,"sender_id"),text(o,"recipient_id"),text(o,"body"),date(o,"sent_at"),date(o,"read_at"),date(o,"edited_at")); }
    private static String text(JsonObject o,String key) { return !o.has(key) || o.get(key).isJsonNull() ? "" : o.get(key).getAsString(); }
    private static JsonObject one(JsonElement response) throws IOException {
        if (response.isJsonObject()) return response.getAsJsonObject();
        if (response.isJsonArray() && response.getAsJsonArray().size() == 1) return response.getAsJsonArray().get(0).getAsJsonObject();
        throw new IOException("The server did not return the saved record. Refresh to check its status.");
    }
    private static LocalDateTime date(JsonObject o,String key) { String value=text(o,key); return value.isBlank() ? null : OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); }
    static String email(String value) { String email=required(value,"Email",254).toLowerCase(Locale.ROOT); if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Enter a valid email address."); return email; }
    static String password(String value) { if (value == null || value.length() < 12 || value.length() > 128) throw new IllegalArgumentException("Use a password between 12 and 128 characters."); return value; }
    private static String required(String value,String label,int max) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label+" is required."); return optional(value,max); }
    private static String optional(String value,int max) { String result=value == null ? "" : value.trim(); if (result.length()>max) throw new IllegalArgumentException("Use no more than "+max+" characters."); return result; }
    private static String extension(String name) { int dot=name.lastIndexOf('.'); String ext=dot<0 ? "" : name.substring(dot+1).toLowerCase(Locale.ROOT); if (!EXTENSIONS.contains(ext)) throw new IllegalArgumentException("Choose a PDF, image, or text document."); return ext; }
    private static String mime(String ext) { return switch(ext) { case "pdf" -> "application/pdf"; case "jpg","jpeg" -> "image/jpeg"; case "txt" -> "text/plain"; default -> "image/"+ext; }; }
}
