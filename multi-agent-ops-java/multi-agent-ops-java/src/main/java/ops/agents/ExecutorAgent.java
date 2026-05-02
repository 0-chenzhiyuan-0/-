package ops.agents;

import ops.core.*;
import ops.config.Config;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行Agent - 服务重启、扩缩容、自动修复
 */
public class ExecutorAgent extends BaseAgent {

    private final List<Map<String, Object>> history = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger concurrency = new AtomicInteger(0);

    public ExecutorAgent(EventBus bus, TaskScheduler sched) {
        super("executor", "ExecutorAgent", bus, sched);
    }

    @Override
    protected void onStart() {
        eventBus.on("alert.escalated", e -> onEscalation(e.payload()), id);
    }

    @Override
    protected boolean canHandle(Task task) {
        return Set.of("restart_service", "scale_service", "flush_cache", "auto_remediate").contains(task.type);
    }

    @Override
    protected Object process(Task task) throws Exception {
        return switch (task.type) {
            case "restart_service" -> restartService(task.payload);
            case "scale_service" -> scaleService(task.payload);
            case "flush_cache" -> flushCache(task.payload);
            case "auto_remediate" -> autoRemediate(task.payload);
            default -> null;
        };
    }

    private Map<String, Object> restartService(Map<String, Object> payload) throws Exception {
        String service = (String) payload.get("service");
        String reason = (String) payload.getOrDefault("reason", "manual");

        if (concurrency.get() >= Config.MAX_CONCURRENCY) throw new RuntimeException("并发上限");
        concurrency.incrementAndGet();
        try {
            log("🔄 重启: " + service + " (" + reason + ")");
            Thread.sleep(2000);
            eventBus.emit("service.stopped", Map.of("service", service), id);
            Thread.sleep(3000);
            eventBus.emit("service.started", Map.of("service", service), id);
            Thread.sleep(1000);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("op", "restart"); r.put("service", service);
            r.put("status", "success"); r.put("ts", System.currentTimeMillis());
            history.add(r);
            return r;
        } finally {
            concurrency.decrementAndGet();
        }
    }

    private Map<String, Object> scaleService(Map<String, Object> payload) throws Exception {
        String service = (String) payload.get("service");
        Number replicas = (Number) payload.get("replicas");
        String direction = (String) payload.get("direction");
        log("📏 扩缩容: " + service + " -> " + replicas + " (" + direction + ")");
        Thread.sleep(3000);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("op", "scale"); r.put("service", service);
        r.put("replicas", replicas); r.put("status", "success"); r.put("ts", System.currentTimeMillis());
        history.add(r);
        return r;
    }

    private Map<String, Object> flushCache(Map<String, Object> payload) throws Exception {
        String cacheType = (String) payload.getOrDefault("cacheType", "all");
        log("🗑️ 清理缓存: " + cacheType);
        Thread.sleep(1500);
        return Map.of("op", "flush", "cacheType", cacheType, "status", "success", "ts", System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> autoRemediate(Map<String, Object> payload) throws Exception {
        String issue = (String) payload.get("issue");
        log("🔧 自动修复: " + issue);
        List<Map<String, Object>> results = new ArrayList<>();

        if (issue.contains("CPU")) {
            results.add(scaleService(Map.of("service", "auto", "replicas", 2, "direction", "up")));
        }
        if (issue.contains("内存")) {
            results.add(flushCache(Map.of("cacheType", "all")));
        }
        if (issue.contains("服务")) {
            results.add(restartService(Map.of("service", "auto", "reason", "auto_remediate")));
        }
        if (results.isEmpty()) {
            results.add(flushCache(Map.of("cacheType", "all")));
        }

        return Map.of("op", "auto_remediate", "issue", issue, "results", results, "ts", System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private void onEscalation(Object payload) {
        Map<String, Object> data = (Map<String, Object>) payload;
        Map<String, Object> alert = (Map<String, Object>) data.get("alert");
        logWarn("🚨 升级告警: " + alert.get("title"));
        taskScheduler.createTask(
            "修复: " + alert.get("title"), "auto_remediate",
            Map.of("issue", alert.get("title"), "severity", alert.get("severity")),
            "critical", "executor"
        );
    }

    @Override
    public Map<String, Object> getStatus() {
        var base = super.getStatus();
        var copy = new LinkedHashMap<>(base);
        copy.put("concurrency", concurrency.get());
        copy.put("historySize", history.size());
        return copy;
    }
}
