package com.jujin.freeway.web.test;

import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.web.WebModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: starts a real HTTP server on random port, sends HTTP
 * requests. Routes defined via Handler mode (TestRoutes).
 */
class WebIntegrationTest {

    private int port;
    private Registry registry;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        System.setProperty("freeway.server.port", String.valueOf(port));

        registry = Registry.Builder.startAndBuild(
            WebModule.class,
            TestRoutes.class);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
        System.clearProperty("freeway.server.port");
    }

    @Test
    void testGetHello() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/hello"))
            .GET()
            .build();
        var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Hello, Freeway!", response.body().trim());
    }

    @Test
    void testEchoWithQueryParam() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/echo?msg=world"))
            .GET()
            .build();
        var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Echo: world", response.body().trim());
    }

    @Test
    void testEchoDefaultQueryParam() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/echo"))
            .GET()
            .build();
        var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Echo: none", response.body().trim());
    }

    @Test
    void testPathVariable() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/users/42"))
            .GET()
            .build();
        var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("User 42", response.body().trim());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPostWithJsonBody() throws Exception {
        String jsonBody = "{\"name\":\"freeway\",\"version\":1}";
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/data"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .contains("application/json"));

        var parsed = com.jujin.freeway.commons.json.JSONUtils.fromJson(
            response.body(),
            Map.class);
        assertEquals("ok", parsed.get("status"));
        var received = (Map<String, Object>) parsed.get("received");
        assertEquals("freeway", received.get("name"));
        assertEquals(1, ((Number) received.get("version")).intValue());
    }

    @Test
    void testNotFound() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/nonexistent"))
            .GET()
            .build();
        var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    private static int findFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
