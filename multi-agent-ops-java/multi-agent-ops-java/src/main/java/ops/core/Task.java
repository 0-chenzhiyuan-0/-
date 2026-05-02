package ops.core;

import ops.config.Config;
import java.util.*;

/**
 * 任务对象
 */
public class Task {
    public final String id;
    public final String name;
    public final String type;
    public final Map<String, Object> payload;
    public final int priority;
    public final String priorityName;
    public final String assignedAgent;
    public final List<String> dependencies;
    public final int maxRetries;
    public final long timeout;
    public final long createdAt;
    public final Map<String, Object> metadata;

    public volatile String status;       // pending|queued|running|completed|failed|timeout
    public volatile long startedAt;
    public volatile long completedAt;
    public volatile Object result;
    public volatile String error;
    public volatile int retries;

    public Task(String name, String type, Map<String, Object> payload, int priority,
                String priorityName, String assignedAgent, List<String> dependencies,
                int maxRetries, long timeout, Map<String, Object> metadata) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.type = type;
        this.payload = payload != null ? payload : new HashMap<>();
        this.priority = priority;
        this.priorityName = priorityName;
        this.assignedAgent = assignedAgent;
        this.dependencies = dependencies != null ? dependencies : List.of();
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.createdAt = System.currentTimeMillis();
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.status = "pending";
        this.retries = 0;
    }

    /** 转为Map用于JSON序列化 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type);
        m.put("status", status);
        m.put("priority", priorityName);
        m.put("assignedAgent", assignedAgent);
        m.put("retries", retries);
        m.put("createdAt", createdAt);
        if (startedAt > 0) m.put("startedAt", startedAt);
        if (completedAt > 0) m.put("completedAt", completedAt);
        if (error != null) m.put("error", error);
        if (result != null) m.put("result", result);
        return m;
    }
}
