package com.tom.backendswitch.model;

import lombok.Value;
import org.springframework.http.HttpMethod;

@Value
public class Pattern {
    int id;
    HttpMethod method;
    String url;
    String logic;
    String destination;
}
