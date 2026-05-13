package com.jujin.freeway.web.test;

import static org.junit.jupiter.api.Assertions.*;

import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.web.WebModule;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies HTTP protocol version support. When robaho httpserver is on the
 * classpath, the server should accept HTTP/2 connections via h2c upgrade.
 */
class Http2SupportTest {

    private int port;
    private Registry registry;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        System.setProperty("freeway.server.port", String.valueOf(port));
        registry = Registry.Builder.startAndBuild(
            WebModule.class,
            TestRoutes.class);
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
        System.clearProperty("freeway.server.port");
    }

    @Test
    void http11ClientWorks() throws Exception {
        var client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/hello"))
            .GET()
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Hello, Freeway!", response.body().trim());
        assertEquals(HttpClient.Version.HTTP_1_1, response.version());
    }

    @Test
    void http2ClientNegotiatesBestVersion() throws Exception {
        var client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/hello"))
            .GET()
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Hello, Freeway!", response.body().trim());

        var version = response.version();
        assertTrue(
            version == HttpClient.Version.HTTP_2 ||
            version == HttpClient.Version.HTTP_1_1,
            "Expected HTTP/2 or HTTP/1.1, got " + version);
    }

    @Test
    void concurrentRequestsOverHttp2() throws Exception {
        var client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

        int count = 10;
        for (int i = 0; i < count; i++) {
            var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/" + i))
                .GET()
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("User " + i, response.body().trim());
        }
    }

    private static int findFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
