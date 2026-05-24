package ops.core;

import ops.agents.*;
import ops.config.Config;
import java.util.*;
import java.util.concurrent.*;

/**
 * 编排器 - 系统核心，管理Agent生命周期和任务协调
 */
public class Orchestrator {

    public final EventBus eventBus = new EventBus();
    public final TaskScheduler taskScheduler;
    private final Map<String, BaseAgent> agents = new LinkedHashMap<>();
    private final long startTime = System.currentTimeMillis();
    private final Map<String, Integer> metrics = new ConcurrentHashMap<>();

    public Orchestrator() {
        this.taskScheduler = new TaskScheduler(eventBus);
        metrics.put("totalTasks", 0);
        metrics.put("completedTasks", 0);
        metrics.put("failedTasks", 0);
        metrics.put("eventsProcessed", 0);
    }

    /** 注册Agent */
    public void registerAgent(BaseAgent agent) {
        agents.put(agent.type, agent);
        System.out.printf("✅ 注册Agent: %s (%s)%n", agent.name, agent.type);
    }

    /** 启动系统 */
    public void start() {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  🚀 多Agent协同运营自动化系统 启动中...");
        System.out.println("=".repeat(60));
        System.out.println();

        setupEvents();

        for (BaseAgent agent : agents.values()) {
            agent.start();
        }

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.printf("  ✅ 系统启动完成! 已注册 %d 个Agent%n", agents.size());
        System.out.println("=".repeat(60));
        System.out.println();

        eventBus.emit("system.started", Map.of("agents", agents.keySet()), "orchestrator");
    }

    /** 停止系统 */
    public void stop() {
        System.out.println("\n🛑 正在停止...");
        for (BaseAgent agent : agents.values()) {
            agent.stop();
        }
        taskScheduler.shutdown();
        eventBus.emit("system.stopped", Map.of(), "orchestrator");
        System.out.println("✅ 系统已停止");
    }

    /** 提交任务 */
    public Task submitTask(String name, String type, Map<String, Object> payload,
                           String priority, String assignedAgent) {
        metrics.merge("totalTasks", 1, Integer::sum);
        return taskScheduler.createTask(name, type, payload, priority, assignedAgent);
    }

    /** 获取系统状态 */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> agentStatus = new LinkedHashMap<>();
        for (var entry : agents.entrySet()) {
            agentStatus.put(entry.getKey(), entry.getValue().getStatus());
        }
        return Map.of(
            "uptime", System.currentTimeMillis() - startTime,
            "agents", agentStatus,
            "tasks", taskScheduler.getStatus(),
            "metrics", Map.copyOf(metrics)
        );
    }

    public Map<String, BaseAgent> getAgents() { return agents; }

    // --- 内部 ---

    private void setupEvents() {
        eventBus.on("task.completed", e -> metrics.merge("completedTasks", 1, Integer::sum), "metrics");
        eventBus.on("task.failed", e -> {
            metrics.merge("failedTasks", 1, Integer::sum);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) e.payload();
            if (agents.containsKey("alert")) {
                eventBus.emit("alert.trigger", Map.of(
                    "severity", "warning",
                    "title", "任务失败: " + data.get("name"),
                    "message", data.getOrDefault("error", "未知错误"),
                    "source", "orchestrator"
                ), "orchestrator");
            }
        }, "metrics");
        eventBus.on("*", e -> metrics.merge("eventsProcessed", 1, Integer::sum), "metrics");
    }
}
