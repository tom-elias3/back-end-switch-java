package com.tom.backendswitch.service;

import com.tom.backendswitch.model.OriginalRequest;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionHelperProxyRequestTest {

    @Mock RestClient mockClient;
    @Mock RestClient.RequestBodyUriSpec mockBodyUriSpec;
    @Mock RestClient.RequestBodySpec mockBodySpec;
    @Mock RestClient.ResponseSpec mockResponseSpec;
    @Mock HttpServletResponse mockResponse;

    ByteArrayOutputStream capturedOutput;

    @BeforeEach
    void setUp() throws IOException {
        when(mockClient.method(any())).thenReturn(mockBodyUriSpec);
        when(mockBodyUriSpec.uri(anyString())).thenReturn(mockBodySpec);
        when(mockBodySpec.headers(any())).thenReturn(mockBodySpec);
        lenient().when(mockBodySpec.body(anyString())).thenReturn(mockBodySpec);
        when(mockBodySpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);

        capturedOutput = new ByteArrayOutputStream();
        lenient().when(mockResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            public void write(int b) { capturedOutput.write(b); }
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener l) {}
        });
    }

    @Test
    void copiesUpstreamStatusToResponse() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body(null));

        DecisionHelper.proxyRequest(request(null, null), "Bearer token", "https://dest", mockClient, mockResponse);

        verify(mockResponse).setStatus(202);
    }

    @Test
    void copiesUpstreamBodyToResponse() throws IOException {
        byte[] body = "hello upstream".getBytes();
        when(mockResponseSpec.toEntity(byte[].class))
                .thenReturn(ResponseEntity.ok(body));

        DecisionHelper.proxyRequest(request(null, null), "Bearer token", "https://dest", mockClient, mockResponse);

        assertThat(capturedOutput.toByteArray()).isEqualTo(body);
    }

    @Test
    void nullUpstreamBodyDoesNotWriteToOutputStream() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class))
                .thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request(null, null), "Bearer token", "https://dest", mockClient, mockResponse);

        verify(mockResponse, never()).getOutputStream();
    }

    @Test
    void copiesUpstreamResponseHeaders() throws IOException {
        HttpHeaders upstreamHeaders = new HttpHeaders();
        upstreamHeaders.put("X-Custom", List.of("value1", "value2"));
        when(mockResponseSpec.toEntity(byte[].class))
                .thenReturn(ResponseEntity.ok().headers(upstreamHeaders).body(null));

        DecisionHelper.proxyRequest(request(null, null), "Bearer token", "https://dest", mockClient, mockResponse);

        verify(mockResponse).addHeader("X-Custom", "value1");
        verify(mockResponse).addHeader("X-Custom", "value2");
    }

    @Test
    void setsAuthorizationHeaderFromToken() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request(null, null), "Bearer mytoken", "https://dest", mockClient, mockResponse);

        HttpHeaders captured = captureRequestHeaders();
        assertThat(captured.getFirst("Authorization")).isEqualTo("Bearer mytoken");
    }

    @Test
    void nullTokenDoesNotSetAuthorizationHeader() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request(null, null), null, "https://dest", mockClient, mockResponse);

        HttpHeaders captured = captureRequestHeaders();
        assertThat(captured.getFirst("Authorization")).isNull();
    }

    @Test
    void forwardsRequestHeaders() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request(null, Map.of("X-Tenant", "acme")), "Bearer token", "https://dest", mockClient, mockResponse);

        HttpHeaders captured = captureRequestHeaders();
        assertThat(captured.getFirst("X-Tenant")).isEqualTo("acme");
    }

    @Test
    void nullRequestHeadersDoesNotThrow() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request(null, null), "Bearer token", "https://dest", mockClient, mockResponse);

        HttpHeaders captured = captureRequestHeaders();
        assertThat(captured.getFirst("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    void withJsonPayloadSetsBodyOnSpec() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request("{\"key\":\"val\"}", null), "Bearer token", "https://dest", mockClient, mockResponse);

        verify(mockBodySpec).body("{\"key\":\"val\"}");
    }

    @Test
    void withoutJsonPayloadDoesNotSetBodyOnSpec() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(request(null, null), "Bearer token", "https://dest", mockClient, mockResponse);

        verify(mockBodySpec, never()).body(anyString());
    }

    @Test
    void usesCorrectHttpMethodAndDestination() throws IOException {
        when(mockResponseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(null));

        DecisionHelper.proxyRequest(
                new OriginalRequest(HttpMethod.POST, "https://original", null, null),
                "Bearer token", "https://destination", mockClient, mockResponse);

        verify(mockClient).method(HttpMethod.POST);
        verify(mockBodyUriSpec).uri("https://destination");
    }

    // --- helpers ---

    private OriginalRequest request(String jsonPayload, Map<String, String> headers) {
        return new OriginalRequest(HttpMethod.GET, "https://original", jsonPayload, headers);
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders captureRequestHeaders() {
        ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockBodySpec).headers(captor.capture());
        HttpHeaders headers = new HttpHeaders();
        captor.getValue().accept(headers);
        return headers;
    }
}
