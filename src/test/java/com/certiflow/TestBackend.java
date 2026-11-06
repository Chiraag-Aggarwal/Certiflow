package com.certiflow;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Isolated HTTP contract fixture. Never used by the application or a live project. */
final class TestBackend implements AutoCloseable {
    static final String ID="00000000-0000-4000-8000-000000000001";
    static final String TEACHER_ID="00000000-0000-4000-8000-000000000099";
    final HttpServer server;
    final List<JsonObject> requests=new CopyOnWriteArrayList<>(), portfolios=new CopyOnWriteArrayList<>();
    final List<JsonObject> projects=new CopyOnWriteArrayList<>(), messages=new CopyOnWriteArrayList<>();
    final List<JsonObject> chatContacts=new CopyOnWriteArrayList<>();
    final List<JsonObject> remarks=new CopyOnWriteArrayList<>();
    volatile boolean remarksUnavailable=false;
    final Map<String,JsonObject> shares=new java.util.concurrent.ConcurrentHashMap<>();
    final Map<String,AppData.Role> profileRoles=new java.util.concurrent.ConcurrentHashMap<>();
    final List<String> calls=new CopyOnWriteArrayList<>();
    volatile AppData.Role role=AppData.Role.STUDENT;
    volatile JsonObject lastBody=new JsonObject();
    volatile JsonObject profileChanges=new JsonObject();
    volatile JsonObject authMetadata=new JsonObject();
    volatile boolean confirmed=true, expireImmediately=false, rejectMetadata=false;
    volatile boolean sharingUnavailable=false;
    volatile boolean canManageAdmins=false, capabilitiesUnavailable=false;
    volatile String capabilitiesResponse=null;
    volatile int profileCount=1, refreshCount=0;
    volatile byte[] document="actual document bytes".getBytes(StandardCharsets.UTF_8);
    TestBackend() throws IOException {
        chatContacts.add(contactRow(TEACHER_ID,"Registered Teacher",AppData.Role.TEACHER));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",this::handle); server.start();
    }
    SupabaseApi client() { return new SupabaseApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"public-test-key",HttpClient.newHttpClient()); }
    private JsonObject profile(int index) {
        JsonObject o=new JsonObject(); o.addProperty("id",index==0?ID:String.format("00000000-0000-4000-8000-%012d",index+1));
        o.addProperty("email","account"+index+"@example.invalid"); o.addProperty("full_name","Registered Account "+index);
        o.addProperty("department","Science"); o.addProperty("role",profileRoles.getOrDefault(o.get("id").getAsString(),role).name());
        if(index==0)for(var entry:profileChanges.entrySet())o.add(entry.getKey(),entry.getValue());return o;
    }
    private JsonObject session() {
        JsonObject o=new JsonObject(); o.addProperty("access_token","access-token"); o.addProperty("refresh_token","refresh-token");
        o.addProperty("expires_in",expireImmediately?1:3600);
        JsonObject u=new JsonObject(); u.addProperty("id",ID); u.addProperty("email","account0@example.invalid");
        u.add("user_metadata",authMetadata.deepCopy());
        if(confirmed) u.addProperty("email_confirmed_at","2026-11-05T00:00:00Z"); else u.add("email_confirmed_at",JsonNull.INSTANCE);
        o.add("user",u); return o;
    }
    private void handle(HttpExchange ex) throws IOException {
        try {
            String path=ex.getRequestURI().getPath(), query=Objects.toString(ex.getRequestURI().getQuery(),""), method=ex.getRequestMethod();
            calls.add(method+" "+ex.getRequestURI());
            byte[] bytes=ex.getRequestBody().readAllBytes();
            if("application/json".equals(ex.getRequestHeaders().getFirst("Content-Type")) && bytes.length>0) lastBody=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!"public-test-key".equals(ex.getRequestHeaders().getFirst("apikey"))) { reply(ex,401,"{}"); return; }
            if(path.equals("/auth/v1/token")) {
                if(query.contains("refresh_token")) refreshCount++;
                if(lastBody.has("password") && lastBody.get("password").getAsString().equals("wrong")) { reply(ex,400,"{\"error_code\":\"invalid_credentials\"}"); return; }
                reply(ex,200,session().toString()); return;
            }
            if(path.equals("/auth/v1/verify")) { reply(ex,200,session().toString()); return; }
            if(path.startsWith("/auth/v1/")) { reply(ex,200,"{}"); return; }
            if(!"Bearer access-token".equals(ex.getRequestHeaders().getFirst("Authorization"))) { reply(ex,401,"{}"); return; }
            if(path.equals("/rest/v1/rpc/account_capabilities")) {
                if(capabilitiesUnavailable){reply(ex,503,"{\"message\":\"Capabilities unavailable\"}");return;}
                if(capabilitiesResponse!=null){reply(ex,200,capabilitiesResponse);return;}
                reply(ex,200,"{\"can_manage_admins\":"+(role==AppData.Role.ADMIN&&canManageAdmins)+"}");return;
            }
            if(path.equals("/rest/v1/private_remarks")) {
                if(remarksUnavailable){reply(ex,503,"{\"message\":\"Remarks temporarily unavailable\"}");return;}
                List<JsonObject> found=remarks.stream().filter(r->role!=AppData.Role.VIEWER&&(role==AppData.Role.ADMIN
                    ||role==AppData.Role.STUDENT&&ID.equals(r.get("student_id").getAsString())
                    ||role==AppData.Role.TEACHER&&ID.equals(r.get("author_id").getAsString())))
                    .filter(r->query.contains("request_id=eq."+r.get("source_id").getAsString())&&r.get("source_kind").getAsString().equals("NOC")
                        ||query.contains("project_id=eq."+r.get("source_id").getAsString())&&r.get("source_kind").getAsString().equals("PROJECT")).toList();
                reply(ex,200,new Gson().toJson(page(found,query)));return;
            }
            if(path.equals("/rest/v1/rpc/add_private_remark")) {
                if(!role.isStaff()){reply(ex,403,"{}");return;}
                if(remarksUnavailable){reply(ex,503,"{\"message\":\"Remarks temporarily unavailable\"}");return;}
                JsonObject remark=new JsonObject();remark.addProperty("id",UUID.randomUUID().toString());remark.addProperty("student_id",ID);
                remark.addProperty("author_id",ID);remark.addProperty("author_name","Registered staff member");
                remark.add("body",lastBody.get("remark_text"));remark.add("source_id",lastBody.get("source_id"));remark.add("source_kind",lastBody.get("source_kind"));
                remark.addProperty("created_at","2026-11-06T08:00:00Z");remarks.add(0,remark);reply(ex,200,remark.toString());return;
            }
            if(path.equals("/rest/v1/rpc/get_own_share")||path.equals("/rest/v1/rpc/publish_share")||path.equals("/rest/v1/rpc/unpublish_share")) {
                if(sharingUnavailable){reply(ex,503,"{\"message\":\"Sharing service unavailable.\"}");return;}
                String kind=lastBody.get("source_kind").getAsString(),sourceId=lastBody.get("source_id").getAsString(),shareKey=kind+":"+sourceId;
                if(path.endsWith("/get_own_share")){JsonObject saved=shares.get(shareKey);reply(ex,200,saved==null?"null":saved.toString());return;}
                if(path.endsWith("/unpublish_share")){shares.remove(shareKey);reply(ex,200,"null");return;}
                JsonObject result=new JsonObject(),previous=shares.get(shareKey);
                result.addProperty("id",previous==null?UUID.randomUUID().toString():previous.get("id").getAsString());result.addProperty("token",previous==null?UUID.randomUUID().toString():previous.get("token").getAsString());
                result.addProperty("source_kind",kind);result.add("title",lastBody.get("public_title"));result.add("summary",lastBody.get("public_summary"));result.add("display_name",lastBody.get("public_author"));
                result.add("link",lastBody.get("public_link"));result.add("audience",lastBody.get("audience"));result.addProperty("category",kind.equals("NOC")?"Scholarship":"Project");
                result.addProperty("status",kind.equals("NOC")?"PENDING":"");result.addProperty("published_at","2026-11-05T08:00:00Z");JsonArray files=new JsonArray();
                for(JsonElement fileId:lastBody.getAsJsonArray("file_ids"))portfolios.stream().filter(p->p.get("id").equals(fileId)).findFirst().ifPresent(p->{JsonObject file=new JsonObject();for(String key:List.of("id","title","file_name","stored_name"))file.add(key,p.get(key));files.add(file);});
                result.add("files",files);shares.put(shareKey,result);reply(ex,200,result.toString());return;
            }
            if(path.equals("/rest/v1/rpc/community_shares")){var rows=shares.values().stream().filter(p->List.of("COMMUNITY","BOTH").contains(p.get("audience").getAsString())).sorted(Comparator.comparing(p->p.get("id").getAsString())).toList();reply(ex,200,new Gson().toJson(page(rows,query)));return;}
            if(path.equals("/rest/v1/profiles")) {
                if(method.equals("PATCH")) { if(lastBody.has("role")||lastBody.has("email")||lastBody.has("identity_verified")){reply(ex,403,"{}");return;}profileChanges=lastBody.deepCopy();profileChanges.addProperty("identity_verified",false);reply(ex,200,"["+profile(0)+"]"); return; }
                int start=query.contains("offset=500")?500:0, end=query.contains("id=eq.")||role==AppData.Role.VIEWER?1:Math.min(profileCount,start+500);
                JsonArray page=new JsonArray(); for(int i=start;i<end;i++) page.add(profile(i)); reply(ex,200,page.toString()); return;
            }
            if(path.equals("/rest/v1/rpc/contact_directory")) {
                var rows=chatContacts.stream().filter(c->staff(role.name())||staff(c.get("role").getAsString())).toList();
                reply(ex,200,new Gson().toJson(page(rows,query)));return;
            }
            if(path.equals("/rest/v1/rpc/chat_inbox")) {
                Map<String,JsonObject> latest=new HashMap<>();Map<String,Integer> unread=new HashMap<>();
                for(JsonObject m:messages){if(!participant(m,ID))continue;String other=ID.equals(m.get("sender_id").getAsString())?m.get("recipient_id").getAsString():m.get("sender_id").getAsString();
                    latest.compute(other,(ignored,old)->old==null||messageOrder().compare(m,old)<0?m:old);
                    if(ID.equals(m.get("recipient_id").getAsString())&&isNull(m,"read_at"))unread.merge(other,1,Integer::sum);}
                List<JsonObject> rows=new ArrayList<>();
                for(var entry:latest.entrySet()){JsonObject profile=chatContacts.stream().filter(c->c.get("id").getAsString().equals(entry.getKey())).findFirst().orElse(null);if(profile==null)continue;
                    JsonObject row=new JsonObject();row.addProperty("contact_id",entry.getKey());row.add("full_name",profile.get("full_name"));row.add("role",profile.get("role"));row.add("department",profile.get("department"));
                    row.add("last_message",entry.getValue().get("body"));row.add("last_sent_at",entry.getValue().get("sent_at"));row.addProperty("unread_count",unread.getOrDefault(entry.getKey(),0));
                    row.addProperty("can_send",staff(role.name())||staff(profile.get("role").getAsString()));rows.add(row);}
                rows.sort(Comparator.<JsonObject,java.time.Instant>comparing(row->instant(row,"last_sent_at")).reversed().thenComparing(row->row.get("contact_id").getAsString()));
                reply(ex,200,new Gson().toJson(page(rows,query)));return;
            }
            if(path.equals("/rest/v1/rpc/chat_history")) {
                String other=lastBody.get("other_id").getAsString();List<JsonObject> rows=messages.stream().filter(m->pair(m,ID,other)).sorted(messageOrder()).toList();
                if(!isNull(lastBody,"before_id")){String anchorId=lastBody.get("before_id").getAsString();JsonObject anchor=rows.stream().filter(m->m.get("id").getAsString().equals(anchorId)).findFirst().orElse(null);
                    if(anchor==null){reply(ex,403,"{\"message\":\"This history position does not belong to your conversation.\"}");return;}
                    rows=rows.stream().filter(m->messageOrder().compare(m,anchor)>0).toList();}
                int count=Math.max(1,Math.min(100,lastBody.get("page_size").getAsInt()));reply(ex,200,new Gson().toJson(rows.subList(0,Math.min(count,rows.size()))));return;
            }
            if(path.equals("/rest/v1/rpc/edit_message")) {
                JsonObject m=messages.stream().filter(row->row.get("id").equals(lastBody.get("message_id"))).findFirst().orElse(null);
                if(m==null||!ID.equals(m.get("sender_id").getAsString())){reply(ex,403,"{\"message\":\"You can edit only your own messages.\"}");return;}
                if(!m.get("body").equals(lastBody.get("original_body"))){reply(ex,409,"{\"code\":\"40001\",\"message\":\"This message changed elsewhere. Refresh the conversation before editing again.\"}");return;}
                String body=lastBody.get("new_body").getAsString().trim();if(body.isBlank()||body.length()>4000){reply(ex,400,"{\"message\":\"Write a message between 1 and 4,000 characters.\"}");return;}
                if(!m.get("body").getAsString().equals(body)){m.addProperty("body",body);m.addProperty("edited_at","2026-11-05T08:00:00Z");}reply(ex,200,m.toString());return;
            }
            if(path.equals("/rest/v1/rpc/set_account_role") || path.equals("/rest/v1/rpc/verify_identity")) {
                if(role!=AppData.Role.ADMIN){reply(ex,403,"{}");return;}
                String target=lastBody.get("target_id").getAsString();AppData.Role existing=profileRoles.getOrDefault(target,target.equals(TEACHER_ID)?AppData.Role.TEACHER:role);
                if(lastBody.has("new_role")&&(target.equals(ID)||!canManageAdmins&&(existing==AppData.Role.ADMIN||lastBody.get("new_role").getAsString().equals("ADMIN")))){reply(ex,403,"{\"message\":\"Only the founding administrator can change administrator access. Your own access is protected.\"}");return;}
                JsonObject p=profile(1);p.add("id",lastBody.get("target_id"));
                if(lastBody.has("new_role")){p.add("role",lastBody.get("new_role"));profileRoles.put(target,AppData.Role.valueOf(lastBody.get("new_role").getAsString()));}else p.addProperty("identity_verified",true);
                reply(ex,200,p.toString());return;
            }
            if(path.equals("/rest/v1/portfolio_projects")) {
                if(role==AppData.Role.VIEWER){reply(ex,method.equals("GET")?200:403,method.equals("GET")?"[]":"{}");return;}
                if(method.equals("POST")){JsonObject p=lastBody.deepCopy();p.addProperty("id",UUID.randomUUID().toString());p.addProperty("student_id",ID);p.addProperty("created_at","2026-11-05T06:30:00Z");projects.add(p);reply(ex,201,"["+p+"]");return;}
                if(method.equals("PATCH") || method.equals("DELETE")){
                    JsonObject p=projects.stream().filter(row->query.contains("id=eq."+row.get("id").getAsString())).findFirst().orElseThrow();
                    if(method.equals("PATCH")){for(var entry:lastBody.entrySet())p.add(entry.getKey(),entry.getValue());}
                    else {projects.remove(p);portfolios.removeIf(row->row.has("project_id")&&row.get("project_id").equals(p.get("id")));}
                    reply(ex,200,"["+p+"]");return;
                }
                reply(ex,200,new Gson().toJson(projects));return;
            }
            if(path.equals("/rest/v1/messages")) {
                if(method.equals("POST")){JsonObject recipient=chatContacts.stream().filter(c->c.get("id").equals(lastBody.get("recipient_id"))).findFirst().orElse(null);if(recipient==null||!staff(role.name())&&!staff(recipient.get("role").getAsString())){reply(ex,403,"{}");return;}JsonObject m=lastBody.deepCopy();m.addProperty("id",UUID.randomUUID().toString());m.addProperty("sender_id",ID);m.addProperty("sent_at","2026-11-05T06:30:00Z");m.add("read_at",JsonNull.INSTANCE);m.add("edited_at",JsonNull.INSTANCE);messages.add(m);reply(ex,201,"["+m+"]");return;}
                List<JsonObject> result=new ArrayList<>(messages.stream().filter(m->participant(m,ID)).toList());
                if(query.contains("or=(and(")){var matcher=java.util.regex.Pattern.compile("[0-9a-f-]{36}").matcher(query);String other=null;while(matcher.find()){if(!ID.equals(matcher.group())){other=matcher.group();break;}}String contactId=other;result.removeIf(m->contactId==null||!pair(m,ID,contactId));}
                if(query.contains("read_at=is.null"))result.removeIf(m->!m.get("recipient_id").getAsString().equals(ID)||!isNull(m,"read_at"));
                result.sort(query.contains("order=id.asc")?Comparator.comparing(m->m.get("id").getAsString()):messageOrder());reply(ex,200,new Gson().toJson(page(result,query)));return;
            }
            if(path.equals("/rest/v1/rpc/mark_messages_read")) {for(JsonObject m:messages)if(m.get("recipient_id").getAsString().equals(ID)&&m.get("sender_id").equals(lastBody.get("other_id")))m.addProperty("read_at","2026-11-05T07:00:00Z");reply(ex,200,"null");return;}
            if(path.equals("/rest/v1/requests")) {
                if(role==AppData.Role.VIEWER){reply(ex,method.equals("GET")?200:403,method.equals("GET")?"[]":"{}");return;}
                if(method.equals("POST")) {
                    JsonObject r=lastBody.deepCopy(); r.addProperty("id",UUID.randomUUID().toString()); r.addProperty("student_id",ID);
                    r.addProperty("student_name","Registered Account 0"); r.addProperty("status","PENDING"); r.addProperty("submitted_at","2026-11-05T06:30:00Z");
                    r.add("decided_at",JsonNull.INSTANCE); r.addProperty("decision_note",""); requests.add(r); reply(ex,201,"["+r+"]"); return;
                }
                reply(ex,200,new Gson().toJson(requests)); return;
            }
            if(path.equals("/rest/v1/rpc/decide_request")) {
                if(!staff(role.name())){reply(ex,403,"{}");return;}
                JsonObject r=requests.stream().filter(row -> row.get("id").equals(lastBody.get("request_id"))).findFirst().orElseThrow().deepCopy();
                r.add("status",lastBody.get("decision")); r.add("decision_note",lastBody.get("note")); r.addProperty("decided_at","2026-11-05T07:00:00Z");
                reply(ex,200,"["+r+"]"); return;
            }
            if(path.equals("/rest/v1/portfolios")) {
                if(role==AppData.Role.VIEWER){reply(ex,method.equals("GET")?200:403,method.equals("GET")?"[]":"{}");return;}
                if(method.equals("PATCH") || method.equals("DELETE")){
                    JsonObject p=portfolios.stream().filter(row->query.contains("id=eq."+row.get("id").getAsString())).findFirst().orElseThrow();
                    if(method.equals("PATCH"))p.add("title",lastBody.get("title"));else portfolios.remove(p);reply(ex,200,"["+p+"]");return;
                }
                if(method.equals("POST")) {
                    if(rejectMetadata) { reply(ex,500,"{\"message\":\"metadata failed\"}"); return; }
                    JsonObject p=lastBody.deepCopy(); p.addProperty("id",UUID.randomUUID().toString()); p.addProperty("student_id",ID); p.addProperty("added_at","2026-11-05T06:30:00Z"); portfolios.add(p); reply(ex,201,"["+p+"]"); return;
                }
                reply(ex,200,new Gson().toJson(portfolios)); return;
            }
            if(path.startsWith("/storage/v1/")) {
                if(method.equals("GET")) { ex.sendResponseHeaders(200,document.length); ex.getResponseBody().write(document); ex.close(); return; }
                if(method.equals("POST")) document=bytes;
                reply(ex,200,"{}"); return;
            }
            reply(ex,404,"{\"message\":\"Unexpected test endpoint\"}");
        } catch(Exception error) { reply(ex,500,"{\"message\":\"Test backend failure\"}"); }
    }
    static JsonObject contactRow(String id,String name,AppData.Role role){JsonObject c=new JsonObject();c.addProperty("id",id);c.addProperty("full_name",name);c.addProperty("role",role.name());c.addProperty("department","Science");return c;}
    static JsonObject messageRow(int sequence,String sender,String recipient,String body,String time){JsonObject m=new JsonObject();m.addProperty("id",String.format("20000000-0000-4000-8000-%012d",sequence));m.addProperty("sender_id",sender);m.addProperty("recipient_id",recipient);m.addProperty("body",body);m.addProperty("sent_at",time);m.add("read_at",JsonNull.INSTANCE);m.add("edited_at",JsonNull.INSTANCE);return m;}
    private static boolean isNull(JsonObject row,String name){return !row.has(name)||row.get(name).isJsonNull();}
    private static boolean staff(String role){return role.equals("TEACHER")||role.equals("ADMIN");}
    private static boolean participant(JsonObject m,String id){return m.get("sender_id").getAsString().equals(id)||m.get("recipient_id").getAsString().equals(id);}
    private static boolean pair(JsonObject m,String one,String other){return m.get("sender_id").getAsString().equals(one)&&m.get("recipient_id").getAsString().equals(other)||m.get("sender_id").getAsString().equals(other)&&m.get("recipient_id").getAsString().equals(one);}
    private static java.time.Instant instant(JsonObject row,String field){return java.time.OffsetDateTime.parse(row.get(field).getAsString()).toInstant();}
    private static Comparator<JsonObject> messageOrder(){return Comparator.<JsonObject,java.time.Instant>comparing(m->instant(m,"sent_at")).thenComparing(m->m.get("id").getAsString()).reversed();}
    private static List<JsonObject> page(List<JsonObject> rows,String query){int offset=parameter(query,"offset",0),limit=parameter(query,"limit",1000);int start=Math.min(offset,rows.size());return rows.subList(start,Math.min(rows.size(),start+limit));}
    private static int parameter(String query,String name,int fallback){for(String part:query.split("&"))if(part.startsWith(name+"="))return Math.max(0,Integer.parseInt(part.substring(name.length()+1)));return fallback;}
    private static void reply(HttpExchange ex,int status,String text) throws IOException { byte[] bytes=text.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().add("Content-Type","application/json"); ex.sendResponseHeaders(status,bytes.length); ex.getResponseBody().write(bytes); ex.close(); }
    public void close() { server.stop(0); }
}
