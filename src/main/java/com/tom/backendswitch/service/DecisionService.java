package com.tom.backendswitch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tom.backendswitch.expression.ExpressionParser;
import com.tom.backendswitch.model.OriginalRequest;
import com.tom.backendswitch.model.Pattern;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;

@Service
public class DecisionService {

    private static final String ROUTING_PROPERTIES_FILE_NAME = "routing.properties";
    private static final String PATTERN = "pattern.";
    private static final String METHOD = ".method";
    private static final String URL = ".url";
    private static final String LOGIC = ".logic";
    private static final String DESTINATION = ".destination";

    private static final Map<Integer, Pattern> patterns = new TreeMap<>();

    private static final BiPredicate<Properties, Integer> checkAllExist = (props, id) -> props.containsKey(PATTERN + id + METHOD)
            && props.containsKey(PATTERN + id + URL)
            && props.containsKey(PATTERN + id + LOGIC)
            && props.containsKey(PATTERN + id + DESTINATION);

    @PostConstruct
    public void init() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(ROUTING_PROPERTIES_FILE_NAME)) {
            Properties routingProperties = new Properties();
            routingProperties.load(is);

            routingProperties.stringPropertyNames().stream()
                    .map(key -> key.split("\\.")[1]).distinct().map(Integer::parseInt)
                    .filter(id -> checkAllExist.test(routingProperties, id))
                    .forEach(id -> patterns.put(id, new Pattern(
                        id,
                        HttpMethod.valueOf(routingProperties.getProperty(PATTERN + id + METHOD)),
                        routingProperties.getProperty(PATTERN + id + URL),
                        routingProperties.getProperty(PATTERN + id + LOGIC),
                        routingProperties.getProperty(PATTERN + id + DESTINATION)
                    )));
        }
    }

    public Map<String, String> extractClaims(String token) throws JsonProcessingException {
        if(!token.startsWith("Bearer ")) {
            return Collections.emptyMap();
        }

        String claimsJson = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );

        Map<String, String> claims = new ObjectMapper().readValue(claimsJson, new TypeReference<Map<String, String>>() {});
        return claims;
    }

    public Map<String, String> extractRequestParams(String url) {
        final String qmark = "?";
        final String equalsSign = "=";
        Map<String, String> result = new HashMap<>();

        if(url != null && !url.isBlank() && url.indexOf(qmark) > 0) {
            String queryParams = url.substring(url.indexOf(qmark) + 1);
            String[] queryParamTokens = queryParams.split("&");
            for(String param : queryParamTokens) {
                if(param != null && !param.isBlank() && param.indexOf(equalsSign) > 0) {
                    String[] tokens = param.split(equalsSign);
                    result.put(tokens[0], tokens[1]);
                }
            }
        }

        return result;
    }

    public Pattern matchPattern(OriginalRequest originalUrl) {
        Pattern found = patterns.values().parallelStream()
                .filter(pattern -> matchUrl(originalUrl.getMethod(), originalUrl.getUrl(), pattern))
                .min(Comparator.comparingInt(Pattern::getId))
                .orElse(null);
        return found;
    }

    private boolean matchUrl(HttpMethod method, String url, Pattern pattern) {
        if(!method.equals(pattern.getMethod())) return false;

        String[] tokens = pattern.getUrl().split("\\*");
        String remaining = url;
        for (int i=0; i < tokens.length; i++) {
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

    public String evaluateLogic(Pattern pattern, Map<String, String> claims, Map<String, String> params) {
        Map<String, String> context = new HashMap<>();
        claims.forEach((k, v) -> context.put("claim." + k, v));
        params.forEach((k, v) -> context.put("param." + k, v));

        boolean result = ExpressionParser.parse(pattern.getLogic(), context).evaluate();
        return result ? pattern.getDestination() : null;
    }
}