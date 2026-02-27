package com.tom.backendswitch.model;

import lombok.Value;
import org.springframework.http.HttpMethod;

import java.util.Map;

@Value
public class OriginalRequest {
    HttpMethod method;
    String url;
    String jsonPayload;
    Map<String, String> headers;
}
