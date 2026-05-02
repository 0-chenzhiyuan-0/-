package ops.agents;

import ops.core.*;
import ops.config.Config;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 监控Agent - 系统资源采集、阈值告警
 */
public class MonitorAgent extends BaseAgent {

    private final List<Map<String, Object>> metricsHistory = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MonitorAgent(EventBus bus, TaskScheduler sched) {
        super("monitor", "MonitorAgent", bus, sched);
    }

    @Override
    protected void onStart() {
        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, Config.MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onStop() {
        scheduler.shutdown();
    }

    @Override
    protected boolean canHandle(Task task) {
        return Set.of("health_check", "metric_query", "service_check").contains(task.type);
    }

    @Override
    protected Object process(Task task) throws Exception {
        return switch (task.type) {
            case "health_check" -> healthCheck();
            case "metric_query" -> queryMetrics(task.payload);
            default -> Map.of("status", "ok");
        };
    }

    /** 采集系统指标 */
    private void collectMetrics() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

            double cpuLoad = osBean.getSystemLoadAverage();
            if (cpuLoad < 0) cpuLoad = 0;
            int cores = osBean.getAvailableProcessors();

            MemoryUsage heap = memBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();
            long usedMem = heap.getUsed() + nonHeap.getUsed();
            long maxMem = Runtime.getRuntime().maxMemory();
            double memPercent = (double) usedMem / maxMem * 100;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestamp", System.currentTimeMillis());
            m.put("cpu", Map.of("usage", cpuLoad, "cores", cores));
            m.put("memory", Map.of(
                "used", usedMem, "max", maxMem,
                "usagePercent", Math.round(memPercent * 10.0) / 10.0
            ));
            m.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());

            metricsHistory.add(m);
            while (metricsHistory.size() > 360) metricsHistory.remove(0);

            // 阈值检查
            if (cpuLoad > Config.CPU_THRESHOLD) {
                eventBus.emit("alert.trigger", Map.of(
                    "severity", "warning", "title", "CPU使用率过高",
                    "message", String.format("当前: %.1f%% (阈值: %.0f%%)", cpuLoad, Config.CPU_THRESHOLD),
                    "source", id
                ), id);
            }
            if (memPercent > Config.MEMORY_THRESHOLD) {
                eventBus.emit("alert.trigger", Map.of(
                    "severity", "warning", "title", "内存使用率过高",
                    "message", String.format("当前: %.1f%% (阈值: %.0f%%)", memPercent, Config.MEMORY_THRESHOLD),
                    "source", id
                ), id);
            }

            eventBus.emit("metrics.collected", m, id);
        } catch (Exception e) {
            log("采集失败: " + e.getMessage());
        }
    }

    private Map<String, Object> healthCheck() {
        Map<String, Object> latest = metricsHistory.isEmpty() ? null : metricsHistory.get(metricsHistory.size() - 1);
        return Map.of(
            "status", "healthy",
            "timestamp", System.currentTimeMillis(),
            "currentMetrics", latest != null ? latest : Map.of(),
            "historySize", metricsHistory.size()
        );
    }

    private Object queryMetrics(Map<String, Object> payload) {
        String metric = (String) payload.get("metric");
        Number duration = (Number) payload.getOrDefault("duration", 60000);
        long since = System.currentTimeMillis() - duration.longValue();
        return metricsHistory.stream()
            .filter(m -> (long) m.get("timestamp") >= since)
            .toList();
    }

    @Override
    public Map<String, Object> getStatus() {
        var base = super.getStatus();
        var copy = new LinkedHashMap<>(base);
        copy.put("metricsHistorySize", metricsHistory.size());
        if (!metricsHistory.isEmpty()) copy.put("latestMetrics", metricsHistory.get(metricsHistory.size() - 1));
        return copy;
    }
}
