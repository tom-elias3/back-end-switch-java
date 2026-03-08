package com.tom.backendswitch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.backendswitch.model.OriginalRequest;
import com.tom.backendswitch.model.Pattern;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DecisionHelper {
    public static void proxyRequest(OriginalRequest originalRequest, String token, String destination, RestClient client, HttpServletResponse response) throws IOException {
        RestClient.RequestHeadersSpec<?> spec = client.method(originalRequest.getMethod())
                .uri(destination)
                .headers(h -> {
                    if (originalRequest.getHeaders() != null) {
                        originalRequest.getHeaders().forEach(h::add);
                    }
                    if(token != null) {
                        h.set("Authorization", token);
                    }
                });

        if (originalRequest.getJsonPayload() != null) {
            spec = ((RestClient.RequestBodySpec) spec).body(originalRequest.getJsonPayload());
        }

        ResponseEntity<byte[]> upstream = spec.retrieve()
                .onStatus(status -> true, (req, res) -> {})
                .toEntity(byte[].class);

        response.setStatus(upstream.getStatusCode().value());
        upstream.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value))
        );
        if (upstream.getBody() != null) {
            response.getOutputStream().write(upstream.getBody());
        }
    }

    public static Map<String, Object> extractClaims(String token) {
        if(token == null || token.isBlank() || !token.startsWith("Bearer ")) {
            return Collections.emptyMap();
        }

        try {
            String claimsJson = new String(
                    Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                    StandardCharsets.UTF_8
            );

            Map<String, Object> claims = new ObjectMapper().readValue(claimsJson, new TypeReference<Map<String, Object>>() {
            });
            return claims;
        } catch(Exception e) {
            return Collections.emptyMap();
        }
    }

    public static Map<String, String> extractRequestParams(String url) {
        final String qmark = "?";
        final String equalsSign = "=";
        Map<String, String> result = new HashMap<>();

        if(url != null && !url.isBlank() && url.indexOf(qmark) > 0) {
            String queryParams = url.substring(url.indexOf(qmark) + 1);
            String[] queryParamTokens = queryParams.split("&");
            for(String param : queryParamTokens) {
                if(param != null && !param.isBlank() && param.indexOf(equalsSign) > 0) {
                    String[] tokens = param.split(equalsSign, 2);
                    result.put(tokens[0], tokens[1]);
                }
            }
        }

        return result;
    }

    public static boolean matchUrl(HttpMethod method, String url, Pattern pattern) {
        if(!method.equals(pattern.getMethod())) return false;

        String[] tokens = pattern.getUrl().split("\\*");
        boolean trailingWildcard = pattern.getUrl().endsWith("*");
        String remaining = url;
        for (int i=0; i < tokens.length; i++) {
            if(i == tokens.length - 1 && !trailingWildcard && !remaining.equals(tokens[i])) {
                return false;
            }
            if (!remaining.startsWith(tokens[i])) {
                return false;
            }

            if(i+1 < tokens.length) {
                int foundIndex = remaining.indexOf(tokens[i+1]);
                if(foundIndex > -1) {
                    remaining = remaining.substring(foundIndex);
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    public static void flattenPayload(Map<String, Object> map, String prefix, Map<String, String> context) {
        map.forEach((k, v) -> {
            String key = prefix + k;
            if (v instanceof Map) {
                flattenPayload((Map<String, Object>) v, key + ".", context);
            } else {
                context.put(key, v != null ? v.toString() : null);
            }
        });
    }

    public static String probabilityDecision(Pattern pattern) {
        int probability = Integer.parseInt(pattern.getLogic().split(":")[1]);
        if (probability < 0 || probability > 100) throw new RuntimeException("Probability value must be between 0 and 100: " + probability);
        if (probability == 100) return pattern.getDestination();
        if (probability == 0) return null;

        int random = new Random().nextInt(100);
        return random < probability ? pattern.getDestination() : null;
    }
}
