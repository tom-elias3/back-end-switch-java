package com.tom.backendswitch.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.MDC;
import org.slf4j.Marker;

public class RequestLevelTurboFilter extends TurboFilter {

    public static final String MDC_KEY = "requestLevel";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        String requested = MDC.get(MDC_KEY);
        if (requested == null) return FilterReply.NEUTRAL;

        Level requestedLevel = Level.toLevel(requested, null);
        if (requestedLevel == null) return FilterReply.NEUTRAL;

        return level.isGreaterOrEqual(requestedLevel) ? FilterReply.ACCEPT : FilterReply.DENY;
    }
}
