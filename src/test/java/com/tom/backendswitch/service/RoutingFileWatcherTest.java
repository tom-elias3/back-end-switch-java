package com.tom.backendswitch.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingFileWatcherTest {

    @Mock Environment environment;
    @Mock DecisionService decisionService;
    @TempDir Path tempDir;

    private RoutingFileWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) watcher.stop();
    }

    @Test
    void noExternalPathSetDoesNothing() throws Exception {
        when(environment.getProperty("routing.properties.path")).thenReturn(null);
        watcher = new RoutingFileWatcher(environment, decisionService);

        watcher.start();
        Thread.sleep(100);

        verify(decisionService, never()).init();
    }

    @Test
    void fileModificationTriggersReload() throws Exception {
        Path file = tempDir.resolve("routing.properties");
        Files.writeString(file, "initial");
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        watcher = new RoutingFileWatcher(environment, decisionService);

        watcher.start();
        Thread.sleep(200);
        Files.writeString(file, "updated");

        verify(decisionService, timeout(5000).atLeastOnce()).init();
    }

    @Test
    void fileCreationTriggersReload() throws Exception {
        Path file = tempDir.resolve("routing.properties");
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        watcher = new RoutingFileWatcher(environment, decisionService);

        watcher.start();
        Thread.sleep(200);
        Files.writeString(file, "created");

        verify(decisionService, timeout(5000).atLeastOnce()).init();
    }

    @Test
    void differentFileInSameDirDoesNotTriggerReload() throws Exception {
        Path watchedFile = tempDir.resolve("routing.properties");
        Path otherFile = tempDir.resolve("other.properties");
        Files.writeString(watchedFile, "initial");
        when(environment.getProperty("routing.properties.path")).thenReturn(watchedFile.toString());
        watcher = new RoutingFileWatcher(environment, decisionService);

        watcher.start();
        Thread.sleep(200);
        Files.writeString(otherFile, "irrelevant change");
        Thread.sleep(500);

        verify(decisionService, never()).init();
    }

    @Test
    void stopWithoutStartDoesNotThrow() {
        watcher = new RoutingFileWatcher(environment, decisionService);

        assertThatNoException().isThrownBy(() -> watcher.stop());
    }

    @Test
    void stopPreventsSubsequentReloads() throws Exception {
        Path file = tempDir.resolve("routing.properties");
        Files.writeString(file, "initial");
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        watcher = new RoutingFileWatcher(environment, decisionService);

        watcher.start();
        Thread.sleep(200);
        watcher.stop();
        Thread.sleep(200);

        Files.writeString(file, "updated after stop");
        Thread.sleep(500);

        verify(decisionService, never()).init();
    }

    @Test
    void initExceptionDoesNotCrashWatcher() throws Exception {
        Path file = tempDir.resolve("routing.properties");
        Files.writeString(file, "initial");
        when(environment.getProperty("routing.properties.path")).thenReturn(file.toString());
        doThrow(new RuntimeException("reload failed")).when(decisionService).init();
        watcher = new RoutingFileWatcher(environment, decisionService);

        watcher.start();
        Thread.sleep(200);
        Files.writeString(file, "change 1");
        verify(decisionService, timeout(5000).atLeastOnce()).init();

        clearInvocations(decisionService);
        Files.writeString(file, "change 2");
        verify(decisionService, timeout(5000).atLeastOnce()).init();
    }
}
