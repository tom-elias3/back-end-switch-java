package com.tom.backendswitch.logging;

import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfig {

    @PostConstruct
    public void registerTurboFilter() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        RequestLevelTurboFilter filter = new RequestLevelTurboFilter();
        filter.setName("requestLevelOverride");
        filter.start();
        context.addTurboFilter(filter);
    }
}
