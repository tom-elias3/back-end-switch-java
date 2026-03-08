package com.tom.backendswitch.controller;

import com.tom.backendswitch.logging.RequestLevelTurboFilter;
import com.tom.backendswitch.model.OriginalRequest;
import com.tom.backendswitch.model.Pattern;
import com.tom.backendswitch.service.DecisionService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class DecisionController {

    private final DecisionService decisionService;

    private static final String JWT_HEADER = "Authorization";
    private static final String LOG_LEVEL_HEADER = "X-logging-level";

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping(path = "/decide")
    public void decide(
            @RequestHeader(JWT_HEADER) String token,
            @RequestHeader(value = LOG_LEVEL_HEADER, required = false) String requestedLevel,
            @RequestBody OriginalRequest originalRequest,
            HttpServletResponse response) throws Exception {

        if (requestedLevel != null) {
            MDC.put(RequestLevelTurboFilter.MDC_KEY, requestedLevel.toLowerCase(Locale.ROOT));
        }
        try {
            decisionService.handleRequest(originalRequest, token, UUID.randomUUID(), response);
        } finally {
            if (requestedLevel != null) {
                MDC.remove(RequestLevelTurboFilter.MDC_KEY);
            }
        }
    }

    @PostMapping(path = "/reload")
    public void reload() throws Exception {
        decisionService.init();
    }

    @GetMapping(path = "/patterns")
    public Map<Integer, Pattern> getPatterns() {
        return decisionService.getPatterns();
    }
}