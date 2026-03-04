package com.tom.backendswitch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Value;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

@Value
public class Pattern {
    int id;
    HttpMethod method;
    String url;
    String logic;
    String destination;
    ResolutionType resolution;
    Integer timeout;
    @JsonIgnore RestClient restClient;
}
