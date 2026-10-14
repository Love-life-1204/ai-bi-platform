package com.aibi.platform.ai.manager;

import com.aibi.platform.ai.model.AiModels.TaskEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AiEventBroker {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long taskId, List<TaskEvent> replay) {
        SseEmitter emitter = new SseEmitter(65_000L);
        try {
            for (TaskEvent event : replay) {
                send(emitter, event);
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return emitter;
        }
        emitters.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> emitters.getOrDefault(taskId, new CopyOnWriteArrayList<>()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(error -> remove.run());
        return emitter;
    }

    public void publish(TaskEvent event) {
        for (SseEmitter emitter : emitters.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>())) {
            try {
                send(emitter, event);
                if ("done".equals(event.type())) {
                    emitter.complete();
                }
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            }
        }
        if ("done".equals(event.type())) {
            emitters.remove(event.taskId());
        }
    }

    private void send(SseEmitter emitter, TaskEvent event) throws IOException {
        emitter.send(SseEmitter.event().id(event.taskId() + ":" + event.sequence())
                .name(event.type()).data(event.payload()));
    }
}

