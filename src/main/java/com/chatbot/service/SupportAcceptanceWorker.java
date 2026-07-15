package com.chatbot.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SupportAcceptanceWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SupportAcceptanceWorker.class);

    private final SupportAcceptanceQueue queue;
    private final SupportService supportService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SupportAcceptanceWorker(SupportAcceptanceQueue queue, SupportService supportService) {
        this.queue = queue;
        this.supportService = supportService;
    }

    @PostConstruct
    public void start() {
        executor.submit(this);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                AcceptRequest request = queue.pollRequest();
                try {
                    supportService.processAcceptance(request.sessionId(), request.technicianId());
                } catch (Exception e) {
                    log.error("Error processing support acceptance request from queue: {}", e.getMessage(), e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
