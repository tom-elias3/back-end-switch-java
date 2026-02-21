package com.tom.backendswitch.service;

import com.tom.backendswitch.model.Pattern;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class DecisionService {

    private static final Map<Integer, Pattern> patterns = new HashMap<>();

    @PostConstruct
    public void init() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("routing.properties")) {
            Properties routingProperties = new Properties();
            routingProperties.load(is);


        }
    }

    public Map<String, String> extractClaims(String token) {
        String claimsJson = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );

        return null;
    }

    public Pattern matchPattern(Map<String, String> claims) {

        return patterns.get(1);
    }

    public String evaluateLogic(Pattern pattern) {

        return pattern.getDestination();
    }

}
