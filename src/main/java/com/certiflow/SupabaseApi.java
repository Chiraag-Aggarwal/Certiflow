package com.certiflow;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Supabase HTTP transport and in-memory session lifecycle. The publishable API key
 * identifies the project; the signed-in user's token determines database access.
 * Synchronized entry points prevent token refresh and logout from racing requests.
 */
final class SupabaseApi {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private final URI base;
    private final String key;
    private final HttpClient http;
    private String accessToken;
    private String refreshToken;
    private Instant expiresAt = Instant.EPOCH;
    private String userId;

    SupabaseApi(CloudConfig config) {
        this(config.url(), config.publicKey(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    // Package-private transport injection supports local contract tests without live accounts.
    SupabaseApi(URI base, String key, HttpClient http) { this.base = base; this.key = key; this.http = http; }

    synchronized JsonObject signIn(String email, String password) throws IOException {
        clearSession();
        JsonObject response = json("POST", "/auth/v1/token?grant_type=password", Map.of("email",email,"password",password), false).getAsJsonObject();
        JsonObject user = response.getAsJsonObject("user");
        if (!user.has("email_confirmed_at") || user.get("email_confirmed_at").isJsonNull())
            throw new IOException("Verify your email before signing in.");
        acceptSession(response);
        return user;
    }
    synchronized void signUp(String email, String password, String name, String department, String number, String accountType) throws IOException {
        json("POST", "/auth/v1/signup", Map.of("email",email,"password",password,"data",
            Map.of("full_name",name,"department",department,"student_number",number,"account_type",accountType)), false);
    }
    synchronized void resendConfirmation(String email) throws IOException {
        json("POST", "/auth/v1/resend", Map.of("type","signup","email",email), false);
    }
    synchronized void verifySignup(String email, String code) throws IOException {
        json("POST", "/auth/v1/verify", Map.of("type","signup","email",email,"token",code), false);
    }
    synchronized void requestRecovery(String email) throws IOException {
        json("POST", "/auth/v1/recover", Map.of("email",email), false);
    }
    synchronized void resetPassword(String email, String code, String password) throws IOException {
        clearSession();
        try {
            // Use the recovery session only for this password change, then discard it.
            acceptSession(json("POST", "/auth/v1/verify", Map.of("type","recovery","email",email,"token",code), false).getAsJsonObject());
            json("PUT", "/auth/v1/user", Map.of("password",password), true);
            signOut();
        } finally { clearSession(); }
    }
    synchronized void signOut() {
        try { if (accessToken != null) json("POST", "/auth/v1/logout?scope=local", Map.of(), true); }
        catch (IOException ignored) { /* Local logout still clears credentials when offline. */ }
        finally { clearSession(); }
    }
    synchronized void clearSession() { accessToken = null; refreshToken = null; userId = null; expiresAt = Instant.EPOCH; }
    synchronized String userId() { return userId; }

    synchronized JsonElement json(String method, String path, Object body, boolean authenticated) throws IOException {
        byte[] bytes = body == null ? new byte[0] : JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        byte[] response = send(method, path, bytes, "application/json", authenticated, true);
        try { return response.length == 0 ? JsonNull.INSTANCE : JsonParser.parseString(new String(response, StandardCharsets.UTF_8)); }
        catch (JsonParseException ex) { throw new IOException("The server returned an unreadable response.", ex); }
    }
    synchronized byte[] binary(String method, String path, byte[] body, String mime) throws IOException {
        return send(method, path, body, mime, true, true);
    }
    private byte[] send(String method, String path, byte[] body, String mime, boolean authenticated, boolean retry) throws IOException {
        // Only project-relative paths are accepted, so tokens cannot be sent to another host.
        if (!path.startsWith("/") || path.startsWith("//")) throw new IOException("Invalid API path.");
        if (authenticated) ensureSession();
        var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(45))
            .header("apikey", key).header("Content-Type", mime).header("Prefer", "return=representation")
            .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (authenticated) builder.header("Authorization", "Bearer " + accessToken);
        HttpResponse<byte[]> response;
        try { response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("The operation was interrupted.", ex); }
        catch (IOException ex) { throw new IOException("Could not reach Certiflow. Check your internet connection and try again.", ex); }
        if (response.statusCode() == 401 && authenticated && retry) {
            // Refresh once for an expired token; never loop indefinitely on permission failures.
            expiresAt = Instant.EPOCH;
            return send(method,path,body,mime,true,false);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw error(response);
        return response.body();
    }
    private void ensureSession() throws IOException {
        if (accessToken == null || refreshToken == null) throw new IOException("Please sign in again.");
        if (Instant.now().plusSeconds(60).isBefore(expiresAt)) return;
        // Renew slightly early, and reject a response that changes the authenticated identity.
        String expectedUser = userId;
        try {
            JsonObject refreshed = json("POST", "/auth/v1/token?grant_type=refresh_token", Map.of("refresh_token",refreshToken), false).getAsJsonObject();
            acceptSession(refreshed);
            if (!expectedUser.equals(userId)) { clearSession(); throw new IOException("Please sign in again."); }
        } catch (IOException | RuntimeException ex) { clearSession(); throw new IOException("Your session expired. Sign in again.", ex); }
    }
    private void acceptSession(JsonObject response) throws IOException {
        try {
            accessToken = response.get("access_token").getAsString();
            refreshToken = response.get("refresh_token").getAsString();
            userId = response.getAsJsonObject("user").get("id").getAsString();
            expiresAt = Instant.now().plusSeconds(response.get("expires_in").getAsLong());
        } catch (RuntimeException ex) { clearSession(); throw new IOException("Sign-in did not return a valid session.", ex); }
    }
    private static IOException error(HttpResponse<byte[]> response) {
        String code = ""; String message = "The operation could not be completed (" + response.statusCode() + ").";
        try {
            JsonObject body = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String field : List.of("error_code","code")) if (body.has(field)) code = body.get(field).getAsString();
            for (String field : List.of("msg","message","error_description","error"))
                if (body.has(field) && body.get(field).isJsonPrimitive()) { message = body.get(field).getAsString(); break; }
        } catch (RuntimeException ignored) { }
        if (code.equals("23505")) message = "You already have a pending application of this type.";
        if (code.equals("invalid_credentials")) message = "Incorrect email or password.";
        if (code.equals("email_not_confirmed")) message = "Verify your email before signing in.";
        if (code.equals("email_address_not_authorized") || code.equals("over_email_send_rate_limit"))
            message = "Email delivery is not ready or its limit has been reached. Ask your administrator to check the mail settings.";
        if (code.equals("42P01") || code.equals("PGRST205")) message = "The database setup is incomplete. Ask your administrator to complete the Certiflow migration.";
        return new IOException(message.length() > 300 ? message.substring(0,300) : message);
    }
}
