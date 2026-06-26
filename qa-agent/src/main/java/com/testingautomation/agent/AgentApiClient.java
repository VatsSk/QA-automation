package com.testingautomation.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

/**
 * Handles all HTTP communication with the Spring Boot backend.
 * Execution logic never calls HTTP directly — it goes through this class.
 */
public class AgentApiClient {

    private static final Logger log = LoggerFactory.getLogger(AgentApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final String baseUrl;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    // Token stored after login (if backend requires auth)
    private String token = "";

    public AgentApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http    = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mapper  = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void setToken(String token) { this.token = token; }

    // ── Agent lifecycle ───────────────────────────────────────────────

    public void register(String agentId, String registeredBy) throws IOException {
        String hostname = safeHostname();
        String os       = System.getProperty("os.name", "unknown");
        String body     = mapper.writeValueAsString(Map.of(
                "agentId",      agentId,
                "hostname",     hostname,
                "os",           os,
                "agentVersion", "1.0",
                "registeredBy", registeredBy
        ));
        post("/api/agent/register", body);
        log.info("Registered agent {} (host={}, os={})", agentId, hostname, os);
    }

    public void heartbeat(String agentId) throws IOException {
        post("/api/agent/" + agentId + "/heartbeat", "{}");
    }

    /**
     * @return null when IDLE, runId string when READY
     */
    public String getNextRun(String agentId) throws IOException {
        String response = get("/api/agent/" + agentId + "/next-run");
        JsonNode node   = mapper.readTree(response);
        String status   = node.path("status").asText();
        if ("READY".equals(status)) {
            return node.path("runId").asText();
        }
        return null;
    }

    public void acknowledgeRun(String agentId) throws IOException {
        post("/api/agent/" + agentId + "/acknowledge", "{}");
    }

    // ── Run data ──────────────────────────────────────────────────────

    public JsonNode downloadRun(String runId) throws IOException {
        return mapper.readTree(get("/api/runs/" + runId));
    }

    // ── Result reporting ──────────────────────────────────────────────

    public void uploadLog(String runId, int step, String status, String message) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "step", step, "status", status, "message", message));
            post("/api/agent/runs/" + runId + "/log", body);
        } catch (Exception e) {
            log.warn("Failed to upload log for run {}: {}", runId, e.getMessage());
        }
    }

    public void uploadScreenshot(String runId, File screenshot) {
        try {
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", screenshot.getName(),
                            RequestBody.create(screenshot, MediaType.parse("image/png")))
                    .build();
            Request req = new Request.Builder()
                    .url(baseUrl + "/api/agent/runs/" + runId + "/screenshot")
                    .header("Authorization", "Bearer " + token)
                    .post(requestBody)
                    .build();
            try (Response res = http.newCall(req).execute()) {
                if (!res.isSuccessful())
                    log.warn("Screenshot upload failed: {}", res.code());
            }
        } catch (Exception e) {
            log.warn("Failed to upload screenshot for run {}: {}", runId, e.getMessage());
        }
    }

    public void completeRun(String agentId, String runId, String status, String reason) throws IOException {
        String body = mapper.writeValueAsString(Map.of("status", status, "reason", reason));
        post("/api/agent/" + agentId + "/runs/" + runId + "/complete", body);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────

    private String get(String path) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response res = http.newCall(req).execute()) {
            String body = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful())
                throw new IOException("GET " + path + " failed (" + res.code() + "): " + body);
            return body;
        }
    }

    private void post(String path, String jsonBody) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                String body = res.body() != null ? res.body().string() : "";
                throw new IOException("POST " + path + " failed (" + res.code() + "): " + body);
            }
        }
    }

    private static String safeHostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
