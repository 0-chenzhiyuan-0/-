package ops.agents;

import ops.core.*;
import ops.config.Config;
import java.util.*;
import java.util.concurrent.*;

/**
 * 分析Agent - 异常检测(Z-Score)、趋势预测、报告生成
 */
public class AnalystAgent extends BaseAgent {

    private final List<Map<String, Object>> data = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> reports = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> anomalies = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public AnalystAgent(EventBus bus, TaskScheduler sched) {
        super("analyst", "AnalystAgent", bus, sched);
    }

    @Override
    protected void onStart() {
        eventBus.on("metrics.collected", e -> ingest(e.payload()), id);
        timer.scheduleAtFixedRate(() -> {
            if (data.size() >= 5) { log("📊 生成定期报告"); generateReport(Map.of()); }
        }, Config.REPORT_INTERVAL, Config.REPORT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onStop() { timer.shutdown(); }

    @Override
    protected boolean canHandle(Task task) {
        return Set.of("analyze", "detect_anomaly", "generate_report").contains(task.type);
    }

    @Override
    protected Object process(Task task) throws Exception {
        return switch (task.type) {
            case "analyze" -> analyze(task.payload);
            case "generate_report" -> generateReport(task.payload);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private void ingest(Object payload) {
        Map<String, Object> m = (Map<String, Object>) payload;
        data.add(m);
        long cutoff = System.currentTimeMillis() - Config.ANALYSIS_WINDOW;
        data.removeIf(d -> (long) d.get("timestamp") < cutoff);

        if (data.size() < 10) return;

        // Z-Score异常检测
        for (String field : new String[]{"cpu", "memory"}) {
            Map<String, Object> sub = (Map<String, Object>) m.get(field);
            if (sub == null) continue;
            Object usageObj = sub.get("usage");
            if (usageObj == null) usageObj = sub.get("usagePercent");
            if (usageObj == null) continue;
            double val = ((Number) usageObj).doubleValue();

            List<Double> values = new ArrayList<>();
            for (Map<String, Object> d : data) {
                Map<String, Object> s = (Map<String, Object>) d.get(field);
                if (s == null) continue;
                Object v = s.get("usage");
                if (v == null) v = s.get("usagePercent");
                if (v != null) values.add(((Number) v).doubleValue());
            }

            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
            double std = Math.sqrt(variance);
            if (std == 0) continue;

            double z = Math.abs((val - mean) / std);
            if (z > Config.ANOMALY_THRESHOLD) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("field", field);
                a.put("value", val);
                a.put("mean", Math.round(mean * 100.0) / 100.0);
                a.put("zScore", Math.round(z * 100.0) / 100.0);
                a.put("severity", z > Config.ANOMALY_THRESHOLD * 1.5 ? "critical" : "warning");
                a.put("timestamp", m.get("timestamp"));
                anomalies.add(a);
                while (anomalies.size() > 100) anomalies.remove(0);
                logWarn("🔍 异常: " + field + "=" + val + " (z=" + Math.round(z * 100.0) / 100.0 + ")");
                eventBus.emit("anomaly.detected", a, id);
            }
        }
    }

    private Map<String, Object> analyze(Map<String, Object> payload) {
        String metric = (String) payload.get("metric");
        Number dur = (Number) payload.getOrDefault("duration", 3600000);
        long since = System.currentTimeMillis() - dur.longValue();

        List<Double> values = new ArrayList<>();
        for (Map<String, Object> d : data) {
            if ((long) d.get("timestamp") < since) continue;
            Map<String, Object> sub = (Map<String, Object>) d.get(metric);
            if (sub == null) continue;
            Object v = sub.get("usage");
            if (v == null) v = sub.get("usagePercent");
            if (v != null) values.add(((Number) v).doubleValue());
        }

        if (values.isEmpty()) return Map.of("error", "数据不足");

        Collections.sort(values);
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();

        return Map.of(
            "metric", metric,
            "dataPoints", values.size(),
            "stats", Map.of(
                "min", values.get(0),
                "max", values.get(values.size() - 1),
                "mean", Math.round(mean * 100.0) / 100.0,
                "median", values.get(values.size() / 2),
                "p95", values.get((int)(values.size() * 0.95))
            ),
            "trend", calculateTrend(values)
        );
    }

    private String calculateTrend(List<Double> values) {
        int n = values.size();
        if (n < 2) return "insufficient";
        double xMean = (n - 1) / 2.0;
        double yMean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - xMean) * (values.get(i) - yMean);
            den += Math.pow(i - xMean, 2);
        }
        double slope = den == 0 ? 0 : num / den;
        if (Math.abs(slope) < 0.01) return "stable";
        return slope > 0 ? "increasing" : "decreasing";
    }

    private Map<String, Object> generateReport(Map<String, Object> payload) {
        Number dur = (Number) payload.getOrDefault("duration", 3600000);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", "report-" + System.currentTimeMillis());
        report.put("generatedAt", System.currentTimeMillis());
        report.put("cpu", analyze(Map.of("metric", "cpu", "duration", dur)));
        report.put("memory", analyze(Map.of("metric", "memory", "duration", dur)));
        report.put("anomalies", anomalies.size());
        reports.add(report);
        while (reports.size() > 50) reports.remove(0);
        eventBus.emit("report.generated", report, id);
        return report;
    }

    @Override
    public Map<String, Object> getStatus() {
        var base = super.getStatus();
        var copy = new LinkedHashMap<>(base);
        copy.put("dataPoints", data.size());
        copy.put("reports", reports.size());
        copy.put("anomalies", anomalies.size());
        return copy;
    }
}
