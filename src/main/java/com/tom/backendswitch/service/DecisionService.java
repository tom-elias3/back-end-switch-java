package com.tom.backendswitch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tom.backendswitch.expression.ExpressionParser;
import com.tom.backendswitch.model.Decision;
import com.tom.backendswitch.model.OriginalRequest;
import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.model.ResolutionType;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiPredicate;

@Slf4j
@Service
public class DecisionService {

    private static final String ROUTING_PROPERTIES_FILE_NAME = "routing.properties";
    private static final String PATTERN = "pattern.";
    private static final String METHOD = ".method";
    private static final String URL = ".url";
    private static final String LOGIC = ".logic";
    private static final String DESTINATION = ".destination";
    private static final String RESOLUTION = ".resolution";
    private static final String TIMEOUT = ".timeout";
    private static final String RANDOM = "RANDOM";

    private static final RestClient REST_CLIENT = RestClient.create();

    private final Environment environment;
    private final Map<Integer, Pattern> patterns = new TreeMap<>();
    private final ReadWriteLock mutex = new ReentrantReadWriteLock();

    public DecisionService(Environment environment) {
        this.environment = environment;
    }

    private static final BiPredicate<Properties, Integer> checkAllExist = (props, id) -> props.containsKey(PATTERN + id + METHOD)
            && props.containsKey(PATTERN + id + URL)
            && props.containsKey(PATTERN + id + LOGIC)
            && props.containsKey(PATTERN + id + DESTINATION);

    @PostConstruct
    public void init() throws Exception {
        String externalPath = environment.getProperty("routing.properties.path");
        InputStream is;
        if (externalPath != null) {
            log.debug("Loading routing patterns from external path: {}", externalPath);
            is = new FileInputStream(externalPath);
        } else {
            log.debug("Loading routing patterns from classpath: {}", ROUTING_PROPERTIES_FILE_NAME);
            is = getClass().getClassLoader().getResourceAsStream(ROUTING_PROPERTIES_FILE_NAME);
            if(is == null) {
                log.debug("no external routing file set, and default routing.properties cannot be found.");
                return;
            }
        }

        try (InputStream stream = is) {
            mutex.writeLock().lock();
            patterns.clear();

            Properties routingProperties = new Properties();
            routingProperties.load(stream);

            routingProperties.stringPropertyNames().stream()
                    .map(key -> key.split("\\.")[1]).distinct().map(Integer::parseInt)
                    .filter(id -> checkAllExist.test(routingProperties, id))
                    .forEach(id -> {
                        String resolutionStr = routingProperties.getProperty(PATTERN + id + RESOLUTION);
                        ResolutionType resolution;
                        try {
                            resolution = resolutionStr != null ? ResolutionType.valueOf(resolutionStr.toUpperCase()) : ResolutionType.REDIRECT;
                        } catch (IllegalArgumentException e) {
                            log.warn("Pattern {}: unrecognised resolution '{}', defaulting to REDIRECT", id, resolutionStr);
                            resolution = ResolutionType.REDIRECT;
                        }
                        String timeoutStr = routingProperties.getProperty(PATTERN + id + TIMEOUT);
                        Integer timeout = timeoutStr != null ? Integer.parseInt(timeoutStr) : null;
                        RestClient patternClient = null;
                        if (resolution == ResolutionType.FOLLOW && timeout != null) {
                            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                            factory.setReadTimeout(Duration.ofMillis(timeout));
                            factory.setConnectTimeout(Duration.ofMillis(timeout));
                            patternClient = RestClient.builder().requestFactory(factory).build();
                        }
                        patterns.put(id, new Pattern(
                            id,
                            HttpMethod.valueOf(routingProperties.getProperty(PATTERN + id + METHOD)),
                            routingProperties.getProperty(PATTERN + id + URL),
                            routingProperties.getProperty(PATTERN + id + LOGIC),
                            routingProperties.getProperty(PATTERN + id + DESTINATION),
                            resolution,
                            timeout,
                            patternClient
                        ));
                    });
        }
        finally {
            mutex.writeLock().unlock();
        }
        log.info("Loaded {} routing pattern(s)", patterns.size());
    }

    public void handleRequest(OriginalRequest originalRequest, String token, UUID requestId, HttpServletResponse response) throws Exception {
        Pattern pattern = this.matchPattern(originalRequest);

        Decision decision = null;
        if (pattern != null) {
            log.debug("[{}] request matched to pattern {}", requestId, pattern.getId());
            Map<String, Object> claims = DecisionHelper.extractClaims(token);
            Map<String, String> params = DecisionHelper.extractRequestParams(originalRequest.getUrl());
            Map<String, String> headers = originalRequest.getHeaders();
            String destination = this.evaluateLogic(pattern, claims, params, headers, originalRequest.getJsonPayload(), requestId);
            decision = destination != null ? new Decision(destination, pattern.getResolution()) : null;
        } else {
            log.debug("[{}] no pattern matched", requestId);
        }

        if (decision != null && decision.resolution() == ResolutionType.FOLLOW) {
            log.debug("[{}] following request to {}", requestId, decision.destination());
            DecisionHelper.proxyRequest(originalRequest, token, decision.destination(), pattern.getRestClient() != null ? pattern.getRestClient() : REST_CLIENT, response);
        } else {
            String redirect = decision != null ? decision.destination() : originalRequest.getUrl();
            response.setHeader("Location", redirect);
            response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
            log.debug("[{}] redirecting request to {}", requestId, redirect);
        }
    }

    public Map<Integer, Pattern> getPatterns() {
        return Collections.unmodifiableMap(patterns);
    }

    public Pattern matchPattern(OriginalRequest originalUrl) {
        mutex.readLock().lock();
        try {
            Pattern found = patterns.values().stream()
                    .filter(pattern -> DecisionHelper.matchUrl(originalUrl.getMethod(), originalUrl.getUrl(), pattern))
                    .findFirst()
                    .orElse(null);
            return found;
        } finally {
            mutex.readLock().unlock();
        }
    }

    public String evaluateLogic(Pattern pattern, Map<String, Object> claims, Map<String, String> params, Map<String, String> headers, String jsonPayload, UUID requestId) {
        if(pattern.getLogic().startsWith(RANDOM)) {
            log.trace("[{}] making random decision with pattern {}", requestId, pattern.getId());
            return DecisionHelper.probabilityDecision(pattern);
        }

        Map<String, String> context = new HashMap<>();
        claims.forEach((k, v) -> context.put("claim." + k, v.toString()));
        params.forEach((k, v) -> context.put("param." + k, v));
        if (headers != null) {
            headers.forEach((k, v) -> context.put("header." + k, v));
        }
        if (jsonPayload != null && pattern.getLogic().contains("{payload.")) {
            try {
                Map<String, Object> payload = new ObjectMapper().readValue(jsonPayload, new TypeReference<>() {});
                DecisionHelper.flattenPayload(payload, "payload.", context);
            } catch (JsonProcessingException e) {
                log.debug("[{}] could not parse JSON payload: {}", requestId, e.getLocalizedMessage());
                // unparseable payload — payload.* keys remain absent from context
            }
        }
        log.trace("[{}] request context values:\n {}", requestId, context);
        try {
            boolean result = ExpressionParser.parse(pattern.getLogic(), context).evaluate();
            return result ? pattern.getDestination() : null;
        } catch (Exception e) {
            log.debug("[{}] failed making decision: {}", requestId, e.getLocalizedMessage());
        }

        return null;
    }
}