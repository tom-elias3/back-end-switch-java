package com.tom.backendswitch.service;

import com.sun.net.httpserver.HttpServer;
import com.tom.backendswitch.model.OriginalRequest;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionServiceHandleRequestTest {

    @Mock Environment environment;
    @Mock HttpServletResponse response;
    @TempDir Path tempDir;

    private DecisionService service;
    private HttpServer upstreamServer;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() throws IOException {
        responseBody = new ByteArrayOutputStream();
        lenient().when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            public void write(int b) { responseBody.write(b); }
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener l) {}
        });
    }

    @AfterEach
    void tearDown() {
        if (upstreamServer != null) upstreamServer.stop(0);
    }

    @Test
    void noPatternMatchRedirectsToOriginalUrl() throws Exception {
        service = serviceWith("""
                pattern.1.method=POST
                pattern.1.url=https://other.example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://original.example.com", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setStatus(307);
        verify(response).setHeader("Location", "https://original.example.com");
    }

    @Test
    void matchedRedirectPatternRedirectsToDestination() throws Exception {
        service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=https://dest.example.com
                pattern.1.resolution=REDIRECT
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setStatus(307);
        verify(response).setHeader("Location", "https://dest.example.com");
    }

    @Test
    void logicFalseRedirectsToOriginalUrl() throws Exception {
        service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:0
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setStatus(307);
        verify(response).setHeader("Location", "https://example.com/api");
    }

    @Test
    void matchedFollowPatternProxiesResponse() throws Exception {
        upstreamServer = startUpstream(200, "upstream-body");
        String upstreamUrl = "http://localhost:" + upstreamServer.getAddress().getPort() + "/";

        service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=%s
                pattern.1.resolution=FOLLOW
                """.formatted(upstreamUrl));

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setStatus(200);
        verify(response, never()).setHeader(eq("Location"), anyString());
        assertThat(responseBody.toString()).isEqualTo("upstream-body");
    }

    @Test
    void matchedFollowPatternPropagatesUpstreamErrorStatus() throws Exception {
        upstreamServer = startUpstream(503, "service unavailable");
        String upstreamUrl = "http://localhost:" + upstreamServer.getAddress().getPort() + "/";

        service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api
                pattern.1.logic=RANDOM:100
                pattern.1.destination=%s
                pattern.1.resolution=FOLLOW
                """.formatted(upstreamUrl));

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setStatus(503);
    }

    @Test
    void expressionLogicTrueRedirectsToDestination() throws Exception {
        service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api?x=*
                pattern.1.logic=({param.x} == 5)
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api?x=5", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setHeader("Location", "https://dest.example.com");
    }

    @Test
    void expressionLogicFalseRedirectsToOriginalUrl() throws Exception {
        service = serviceWith("""
                pattern.1.method=GET
                pattern.1.url=https://example.com/api?x=*
                pattern.1.logic=({param.x} == 5)
                pattern.1.destination=https://dest.example.com
                """);

        OriginalRequest request = new OriginalRequest(HttpMethod.GET, "https://example.com/api?x=9", null, null);
        service.handleRequest(request, null, UUID.randomUUID(), response);

        verify(response).setHeader("Location", "https://example.com/api?x=9");
    }

    // --- helpers ---

    private DecisionService serviceWith(String propertiesContent) throws Exception {
        Path file = tempDir.resolve("routing.properties");
        Files.writeString(file, propertiesContent);
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        DecisionService svc = new DecisionService(environment);
        svc.init();
        return svc;
    }

    private HttpServer startUpstream(int statusCode, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        server.start();
        return server;
    }
}
