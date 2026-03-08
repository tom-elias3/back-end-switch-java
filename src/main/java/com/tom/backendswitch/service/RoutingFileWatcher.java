package com.tom.backendswitch.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.*;

@Slf4j
@Component
public class RoutingFileWatcher {

    private final Environment environment;
    private final DecisionService decisionService;
    private WatchService watchService;

    public RoutingFileWatcher(Environment environment, DecisionService decisionService) {
        this.environment = environment;
        this.decisionService = decisionService;
    }

    @PostConstruct
    public void start() {
        String externalPath = environment.getProperty("routing.properties.path");
        if (externalPath == null) {
            return;
        }

        Path file = Path.of(externalPath).toAbsolutePath();
        Path dir = file.getParent();

        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (Exception e) {
            log.warn("Could not start file watcher for {}: {}", externalPath, e.getMessage());
            return;
        }

        Thread thread = new Thread(() -> {
            log.debug("Watching {} for changes", externalPath);
            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    log.debug("File watcher stopped");
                    return;
                }
                boolean changed = key.pollEvents().stream()
                        .map(e -> (Path) e.context())
                        .anyMatch(p -> dir.resolve(p).equals(file));
                key.reset();
                if (changed) {
                    log.info("Routing file changed, reloading patterns");
                    try {
                        decisionService.init();
                    } catch (Exception e) {
                        log.error("Failed to reload routing patterns: {}", e.getMessage());
                    }
                }
            }
        });
        thread.setName("routing-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void stop() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                log.debug("Error closing file watcher: {}", e.getMessage());
            }
        }
    }
}
