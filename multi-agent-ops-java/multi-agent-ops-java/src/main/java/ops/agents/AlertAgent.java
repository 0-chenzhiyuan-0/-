package ops.agents;

import ops.core.*;
import ops.config.Config;
import java.util.*;
import java.util.concurrent.*;

/**
 * 告警Agent - 告警去重、分级、升级
 */
public class AlertAgent extends BaseAgent {

    private final Map<String, Map<String, Object>> alerts = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> history = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> dedupCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public AlertAgent(EventBus bus, TaskScheduler sched) {
        super("alert", "AlertAgent", bus, sched);
    }

    @Override
    protected void onStart() {
        eventBus.on("alert.trigger", e -> handleAlert(e.payload()), id);
        timer.scheduleAtFixedRate(this::checkEscalations, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void onStop() { timer.shutdown(); }

    @Override
    protected boolean canHandle(Task task) {
        return Set.of("create_alert", "query_alerts").contains(task.type);
    }

    @Override
    protected Object process(Task task) throws Exception {
        return switch (task.type) {
            case "create_alert" -> handleAlert(task.payload);
            case "query_alerts" -> queryAlerts(task.payload);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleAlert(Object payload) {
        Map<String, Object> data = (Map<String, Object>) payload;
        String severity = (String) data.get("severity");
        String title = (String) data.get("title");
        String message = (String) data.get("message");
        String source = (String) data.get("source");

        // 去重
        String key = severity + ":" + title + ":" + source;
        Long last = dedupCache.get(key);
        if (last != null && System.currentTimeMillis() - last < Config.ALERT_DEDUP_WINDOW) return null;
        dedupCache.put(key, System.currentTimeMillis());

        String alertId = "alert-" + System.currentTimeMillis() + "-" + (int)(Math.random()*9000+1000);
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", alertId);
        alert.put("severity", severity);
        alert.put("title", title);
        alert.put("message", message);
        alert.put("source", source);
        alert.put("status", "active");
        alert.put("createdAt", System.currentTimeMillis());
        alert.put("escalated", false);

        alerts.put(alertId, alert);
        history.add(alert);
        while (history.size() > 500) history.remove(0);

        String emoji = switch (severity) {
            case "critical" -> "🔴";
            case "warning" -> "🟡";
            default -> "🔵";
        };
        log(emoji + " [" + severity.toUpperCase() + "] " + title + ": " + message);

        eventBus.emit("alert.created", alert, id);
        if ("critical".equals(severity)) eventBus.emit("alert.critical", alert, id);
        return alert;
    }

    @SuppressWarnings("unchecked")
    private void checkEscalations() {
        long now = System.currentTimeMillis();
        for (var entry : alerts.entrySet()) {
            Map<String, Object> a = entry.getValue();
            if (!"active".equals(a.get("status"))) continue;
            if ((boolean) a.getOrDefault("escalated", false)) continue;

            String severity = (String) a.get("severity");
            long timeout = "critical".equals(severity) ? Config.CRITICAL_ESCALATE_AFTER : Config.WARNING_ESCALATE_AFTER;
            long createdAt = (long) a.get("createdAt");

            if (now - createdAt > timeout) {
                a.put("escalated", true);
                logWarn("⬆️ 告警升级: " + a.get("title"));
                eventBus.emit("alert.escalated", Map.of("alert", a, "escalatedTo", "executor"), id);
            }
        }
    }

    private Object queryAlerts(Map<String, Object> payload) {
        String severity = (String) payload.get("severity");
        return alerts.values().stream()
            .filter(a -> severity == null || severity.equals(a.get("severity")))
            .toList();
    }

    @Override
    public Map<String, Object> getStatus() {
        var base = super.getStatus();
        var copy = new LinkedHashMap<>(base);
        copy.put("activeAlerts", alerts.values().stream().filter(a -> "active".equals(a.get("status"))).count());
        copy.put("totalAlerts", alerts.size());
        return copy;
    }
}
