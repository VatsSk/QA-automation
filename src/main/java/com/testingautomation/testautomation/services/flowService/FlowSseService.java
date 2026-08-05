package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.FlowStepEvent;
import com.testingautomation.testautomation.entities.flow.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages SSE (Server-Sent Events) emitters for real-time flow execution progress.
 * <p>
 * Multiple UI clients can subscribe to the same flow's events. Each client gets its own
 * SseEmitter, stored in a thread-safe list keyed by flow ID.
 */
@Service
public class FlowSseService {

    private static final Logger logger = LoggerFactory.getLogger(FlowSseService.class);

    /**
     * Map of flowId → list of active SSE emitters.
     * ConcurrentHashMap + CopyOnWriteArrayList for thread-safety across async executor threads.
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Register a new SSE emitter for a given flow.
     * Called from the controller when a client subscribes to flow progress.
     */
    public SseEmitter register(String flowId) {
        // 10 minute timeout — long-running flows may take a while
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        List<SseEmitter> flowEmitters = emitters.computeIfAbsent(flowId, k -> new CopyOnWriteArrayList<>());
        flowEmitters.add(emitter);

        // Cleanup on completion, timeout, or error
        Runnable cleanup = () -> {
            flowEmitters.remove(emitter);
            if (flowEmitters.isEmpty()) {
                emitters.remove(flowId);
            }
            logger.debug("SSE emitter removed for flow [{}]. Remaining: {}", flowId, flowEmitters.size());
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> {
            logger.debug("SSE emitter error for flow [{}]: {}", flowId, e.getMessage());
            cleanup.run();
        });

        logger.info("SSE emitter registered for flow [{}]. Total emitters: {}", flowId, flowEmitters.size());
        return emitter;
    }

    /**
     * Send a flow progress event to all subscribed clients.
     *
     * @param flowId    the flow ID
     * @param eventName SSE event name (e.g., "step-update", "flow-started", "flow-completed")
     * @param data      the payload to send (will be serialized to JSON by Spring)
     */
    public void send(String flowId, String eventName, Object data) {
        logger.info("sending progress status");
        List<SseEmitter> flowEmitters = emitters.get(flowId);
        if (flowEmitters == null || flowEmitters.isEmpty()) {
            return; // No subscribers — nothing to send
        }

        // Iterate over a snapshot; CopyOnWriteArrayList handles concurrent removal safely
        for (SseEmitter emitter : flowEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException | IllegalStateException e) {
                logger.debug("Failed to send SSE event [{}] for flow [{}], removing emitter: {}",
                        eventName, flowId, e.getMessage());
                flowEmitters.remove(emitter);
            }
        }
    }

    /**
     * Send the full flow state as a progress update.
     * Deprecated in favor of fine-grained events.
     */
    @Deprecated
    public void sendFlowUpdate(Flow flow) {
        send(flow.getId(), "flow-update", flow);
    }

    public void sendFlowStarted(Flow flow) {
        send(flow.getId(), "flow-started", flow);
    }

    public void sendStepStarted(String flowId, FlowStepEvent event) {
        send(flowId, "step-started", event);
    }

    public void sendStepUpdated(String flowId, FlowStepEvent event) {
        send(flowId, "step-updated", event);
    }

    public void sendStepFailed(String flowId, FlowStepEvent event) {
        send(flowId, "step-failed", event);
    }

    public void sendFlowCompleted(Flow flow) {
        send(flow.getId(), "flow-completed", flow);
        completeEmitters(flow.getId());
    }

    public void sendFlowFailed(Flow flow) {
        send(flow.getId(), "flow-failed", flow);
        completeEmitters(flow.getId());
    }

    private void completeEmitters(String flowId) {
        List<SseEmitter> flowEmitters = emitters.remove(flowId);
        if (flowEmitters != null) {
            for (SseEmitter emitter : flowEmitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    logger.debug("Error completing SSE emitter for flow [{}]: {}", flowId, e.getMessage());
                }
            }
            logger.info("All SSE emitters completed for flow [{}]", flowId);
        }
    }

    /**
     * Complete all emitters for a flow (called when execution finishes).
     * Sends a final "flow-completed" event before completing the streams.
     * Deprecated in favor of sendFlowCompleted / sendFlowFailed.
     */
    @Deprecated
    public void complete(String flowId, Flow flow) {
        sendFlowCompleted(flow);
    }
}
