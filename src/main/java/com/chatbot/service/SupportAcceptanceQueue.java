package com.chatbot.service;

import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class SupportAcceptanceQueue {

    private final BlockingQueue<AcceptRequest> queue = new LinkedBlockingQueue<>();

    public void addRequest(UUID sessionId, UUID technicianId) {
        queue.add(new AcceptRequest(sessionId, technicianId));
    }

    public AcceptRequest pollRequest() throws InterruptedException {
        return queue.take();
    }
}
