package com.certiflow;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/** Only a public project key belongs in a distributed desktop application. */
record CloudConfig(URI url, String publicKey) {
    static CloudConfig load() throws IOException {
        Properties values = new Properties();
        Path file = Path.of(System.getProperty("certiflow.config", "certiflow.properties"));
        if (Files.isRegularFile(file)) {
            try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(input); }
        }
        String url = System.getenv().getOrDefault("SUPABASE_URL", values.getProperty("supabase.url", "")).trim();
        String key = System.getenv().getOrDefault("SUPABASE_PUBLISHABLE_KEY", values.getProperty("supabase.publishableKey", "")).trim();
        if (url.isBlank() && key.isBlank()) return null;
        return validate(url, key);
    }

    static CloudConfig validate(String url, String key) {
        URI uri;
        try { uri = URI.create(url.strip().replaceAll("/+$", "")); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Enter a valid Supabase project URL."); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith(".supabase.co")
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().isEmpty() || uri.getPort() != -1)
            throw new IllegalArgumentException("Use the HTTPS project URL from Supabase (https://your-project.supabase.co).");
        boolean publicKey = key != null && key.matches("sb_publishable_[A-Za-z0-9_-]+");
        if (!publicKey && key != null && key.startsWith("eyJ")) {
            try {
                String[] parts = key.split("\\.");
                publicKey = parts.length == 3 && "anon".equals(JsonParser.parseString(new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)).getAsJsonObject().get("role").getAsString());
            } catch (RuntimeException ignored) { publicKey = false; }
        }
        if (!publicKey) throw new IllegalArgumentException("Use a publishable or legacy anon key. Secret and service-role keys cannot be used in this app.");
        return new CloudConfig(uri, key);
    }
}
