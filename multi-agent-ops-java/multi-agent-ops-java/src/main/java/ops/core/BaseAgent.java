package ops.core;

import java.util.*;
import java.util.concurrent.*;

/**
 * Agent基类 - 生命周期、任务处理、心跳
 */
public abstract class BaseAgent {

    public final String id;
    public final String type;
    public final String name;
    protected final EventBus eventBus;
    protected final TaskScheduler taskScheduler;
    protected final ScheduledExecutorService heartbeatTimer = Executors.newSingleThreadScheduledExecutor();

    public volatile String status = "idle";  // idle|busy|error|stopped
    public volatile Task currentTask;
    public volatile int processedCount;
    public volatile int errorCount;
    public volatile long lastHeartbeat;
    public final long startedAt;

    protected BaseAgent(String type, String name, EventBus eventBus, TaskScheduler taskScheduler) {
        this.id = type + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.type = type;
        this.name = name;
        this.eventBus = eventBus;
        this.taskScheduler = taskScheduler;
        this.startedAt = System.currentTimeMillis();
        this.lastHeartbeat = startedAt;
    }

    /** 启动Agent */
    public void start() {
        log("启动");
        status = "idle";

        // 监听任务就绪
        eventBus.on("task.ready", e -> onTaskReady(e.payload()), this.id);
        eventBus.on("task.assigned", e -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) e.payload();
            if (type.equals(data.get("assignedAgent"))) onTaskReady(data);
        }, this.id);

        // 心跳
        heartbeatTimer.scheduleAtFixedRate(() -> {
            lastHeartbeat = System.currentTimeMillis();
            eventBus.emit("agent.heartbeat", getStatus(), this.id);
        }, 5, 5, TimeUnit.SECONDS);

        onStart();
        eventBus.emit("agent.started", Map.of("id", id, "type", type), this.id);
    }

    /** 停止Agent */
    public void stop() {
        log("停止");
        status = "stopped";
        heartbeatTimer.shutdown();
        onStop();
        eventBus.emit("agent.stopped", Map.of("id", id, "type", type), this.id);
    }

    /** 处理任务（子类实现） */
    protected abstract Object process(Task task) throws Exception;

    /** 子类钩子 */
    protected void onStart() {}
    protected void onStop() {}

    /** 能否处理该任务 */
    protected boolean canHandle(Task task) { return true; }

    /** 获取状态 */
    public Map<String, Object> getStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("name", name);
        m.put("status", status);
        m.put("processedCount", processedCount);
        m.put("errorCount", errorCount);
        m.put("uptime", System.currentTimeMillis() - startedAt);
        if (currentTask != null) {
            m.put("currentTask", Map.of("id", currentTask.id, "name", currentTask.name));
        }
        return m;
    }

    // --- 内部 ---

    @SuppressWarnings("unchecked")
    private void onTaskReady(Object payload) {
        Map<String, Object> data = payload instanceof Map ? (Map<String, Object>) payload : null;
        if (data == null) return;
        String taskId = (String) data.get("id");
        if (taskId == null) return;

        // 查找任务
        Task task = taskScheduler.getQueue().stream()
            .filter(t -> t.id.equals(taskId))
            .findFirst().orElse(null);
        if (task == null) return;
        if (task.assignedAgent != null && !task.assignedAgent.equals(type)) return;
        if (!"idle".equals(status)) return;
        if (!canHandle(task)) return;

        // 领取任务
        boolean claimed = taskScheduler.getQueue().remove(task);
        if (!claimed) return;
        task.status = "running";
        task.startedAt = System.currentTimeMillis();
        taskScheduler.getRunning().put(task.id, task);

        status = "busy";
        currentTask = task;
        log("领取任务: " + task.name + " [" + task.id + "]");

        try {
            Object result = process(task);
            taskScheduler.completeTask(task.id, result);
            processedCount++;
            log("完成任务: " + task.name);
        } catch (Exception e) {
            errorCount++;
            log("任务失败: " + task.name + " - " + e.getMessage());
            taskScheduler.failTask(task.id, e.getMessage());
        } finally {
            status = "idle";
            currentTask = null;
        }
    }

    protected void log(String msg) {
        System.out.printf("[%s] %s%n", name, msg);
    }

    protected void logWarn(String msg) {
        System.err.printf("[%s] ⚠ %s%n", name, msg);
    }
}
