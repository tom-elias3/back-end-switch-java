package com.tom.backendswitch.controller;

import com.tom.backendswitch.model.OriginalRequest;
import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.service.DecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
public class DecisionController {

    private final DecisionService decisionService;

    private static final String JWT_HEADER = "X-Auth-Token";
    public static final String LOCATION = "Location";

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping(path = "/decide")
    public void decide(@RequestHeader(JWT_HEADER) String token, @RequestBody OriginalRequest originalRequest, HttpServletResponse response) throws Exception {
        Map<String, String> claims = decisionService.extractClaims(token);

        Pattern pattern = decisionService.matchPattern(originalRequest.getUrl());
        if(pattern != null) {
            String result = decisionService.evaluateLogic(pattern);
            response.setHeader(LOCATION, !result.isBlank() ? result : originalRequest.getUrl());
        } else {
            response.setHeader(LOCATION, originalRequest.getUrl());
        }

        response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
    }
}
