package com.certiflow;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Publishes only an owner-reviewed snapshot, never a private source record.
 * The database RPCs check ownership and selected files; this class sends only
 * explicitly public fields and converts their responses for both sharing views.
 */
final class PublicSharing {
    static final String PUBLIC_BASE = "https://chiraag-aggarwal.github.io/Certiflow/";
    record FileItem(String id,String title,String fileName,String storedName) {}
    record PublishedItem(String id,String token,String sourceKind,String title,String summary,String displayName,String link,
        String audience,String category,String status,LocalDateTime publishedAt,List<FileItem> files) {}
    private PublicSharing() {}
    private static SupabaseApi api() { return App.data().cloudApi(); }
    static PublishedItem getOwn(String kind,String id) throws IOException {
        return parseNullable(api().json("POST","/rest/v1/rpc/get_own_share",source(kind,id),true));
    }
    static PublishedItem publish(String kind,String id,String title,String summary,String author,String link,String audience,List<String> fileIds) throws IOException {
        // Keep this field allowlist separate from AppData's private source records and remarks.
        Map<String,Object> body=new LinkedHashMap<>(source(kind,id));
        body.put("public_title",required(title,150,"Title"));body.put("public_summary",text(summary,2000));
        body.put("public_author",required(author,120,"Public display name"));body.put("public_link",AppData.webLink(link));
        if(!List.of("COMMUNITY","LINK","BOTH").contains(audience))throw new IllegalArgumentException("Choose a sharing audience.");
        body.put("audience",audience);
        List<String> files=fileIds==null?List.of():fileIds.stream().map(value->UUID.fromString(value).toString()).distinct().toList();
        if(files.size()>30)throw new IllegalArgumentException("Choose up to 30 supporting files.");
        if(kind.equals("NOC")&&!files.isEmpty())throw new IllegalArgumentException("NOC publications cannot include project files.");
        body.put("file_ids",files);
        PublishedItem result=parseNullable(api().json("POST","/rest/v1/rpc/publish_share",body,true));
        if(result==null)throw new IOException("The publication was not returned. Refresh sharing to check its status.");return result;
    }
    static void unpublish(String kind,String id) throws IOException { api().json("POST","/rest/v1/rpc/unpublish_share",source(kind,id),true); }
    static List<PublishedItem> community() throws IOException {
        // Community results already contain the public projection, not full source records.
        List<PublishedItem> result=new ArrayList<>();int offset=0;
        while(true){JsonArray rows=api().json("GET","/rest/v1/rpc/community_shares?limit=200&offset="+offset,null,true).getAsJsonArray();
            for(JsonElement row:rows){PublishedItem item=parseNullable(row);if(item!=null)result.add(item);}if(rows.size()<200)return List.copyOf(result);offset+=rows.size();}
    }
    static Path download(FileItem file) throws IOException {
        // A UUID storage path prevents local path traversal; storage policies decide visibility.
        if(file==null||!file.storedName().matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.(pdf|png|jpg|jpeg|gif|webp|txt)"))throw new IllegalArgumentException("Invalid shared file path.");
        byte[] bytes=api().binary("GET","/storage/v1/object/authenticated/portfolios/"+file.storedName(),new byte[0],"application/octet-stream");
        Path path=App.data().dataDirectory().resolve("shared-downloads").resolve(file.storedName());Files.createDirectories(path.getParent());Files.write(path,bytes);return path;
    }
    // The static viewer reads the token from the fragment and asks the public-share RPC for it.
    static String publicUrl(PublishedItem item) { return PUBLIC_BASE+"#"+UUID.fromString(item.token()); }
    private static Map<String,Object> source(String kind,String id){if(!List.of("PROJECT","NOC").contains(kind))throw new IllegalArgumentException("Choose a project or NOC application.");return Map.of("source_kind",kind,"source_id",UUID.fromString(id).toString());}
    private static String required(String value,int max,String label){String result=text(value,max);if(result.isEmpty())throw new IllegalArgumentException(label+" is required.");return result;}
    private static String text(String value,int max){String result=value==null?"":value.trim();if(result.length()>max)throw new IllegalArgumentException("Use no more than "+max+" characters.");return result;}
    static PublishedItem parseNullable(JsonElement value) {
        if(value==null||value.isJsonNull())return null;
        if(value.isJsonArray()){if(value.getAsJsonArray().isEmpty())return null;value=value.getAsJsonArray().get(0);}
        JsonObject o=value.getAsJsonObject();List<FileItem> files=new ArrayList<>();
        if(o.has("files")&&o.get("files").isJsonArray())for(JsonElement row:o.getAsJsonArray("files")){JsonObject f=row.getAsJsonObject();files.add(new FileItem(string(f,"id"),string(f,"title"),string(f,"file_name"),string(f,"stored_name")));}
        String date=string(o,"published_at");return new PublishedItem(string(o,"id"),string(o,"token"),string(o,"source_kind"),string(o,"title"),string(o,"summary"),string(o,"display_name"),string(o,"link"),string(o,"audience"),string(o,"category"),string(o,"status"),date.isBlank()?null:OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),List.copyOf(files));
    }
    private static String string(JsonObject o,String key){return !o.has(key)||o.get(key).isJsonNull()?"":o.get(key).getAsString();}
}
