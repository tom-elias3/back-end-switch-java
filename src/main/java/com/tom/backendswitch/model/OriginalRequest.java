package com.tom.backendswitch.model;

import lombok.Value;
import org.springframework.http.HttpMethod;

@Value
public class OriginalRequest {
    HttpMethod method;
    String url;
    String jsonPayload;
}
