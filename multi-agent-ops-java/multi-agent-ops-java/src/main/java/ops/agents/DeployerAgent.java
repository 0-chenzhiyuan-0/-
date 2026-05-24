package ops.agents;

import ops.core.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 部署Agent - 滚动/蓝绿/金丝雀部署、自动回滚
 */
public class DeployerAgent extends BaseAgent {

    private final List<Map<String, Object>> history = Collections.synchronizedList(new ArrayList<>());

    public DeployerAgent(EventBus bus, TaskScheduler sched) {
        super("deployer", "DeployerAgent", bus, sched);
    }

    @Override
    protected void onStart() {
        eventBus.on("deploy.request", e -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) e.payload();
            taskScheduler.createTask(
                "部署 " + data.get("service") + " v" + data.get("version"),
                "deploy", data, "high", "deployer"
            );
        }, id);
    }

    @Override
    protected boolean canHandle(Task task) {
        return Set.of("deploy", "rollback").contains(task.type);
    }

    @Override
    protected Object process(Task task) throws Exception {
        return switch (task.type) {
            case "deploy" -> deploy(task.payload);
            case "rollback" -> rollback(task.payload);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deploy(Map<String, Object> payload) throws Exception {
        String service = (String) payload.get("service");
        String version = (String) payload.get("version");
        String strategy = (String) payload.getOrDefault("strategy", "rolling");
        Number instances = (Number) payload.getOrDefault("instances", 1);

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", "deploy-" + System.currentTimeMillis());
        d.put("service", service);
        d.put("version", version);
        d.put("strategy", strategy);
        d.put("status", "deploying");
        d.put("startedAt", System.currentTimeMillis());
        d.put("steps", new ArrayList<Map<String, Object>>());

        log("开始部署: " + service + " v" + version + " (" + strategy + ")");

        try {
            switch (strategy) {
                case "blue-green" -> blueGreenDeploy(d);
                case "canary" -> canaryDeploy(d);
                default -> rollingDeploy(d, instances.intValue());
            }
            d.put("status", "completed");
            d.put("completedAt", System.currentTimeMillis());
            log("✅ 部署完成: " + service + " v" + version);
            eventBus.emit("deploy.completed", d, id);
        } catch (Exception e) {
            d.put("status", "failed");
            d.put("error", e.getMessage());
            d.put("completedAt", System.currentTimeMillis());
            log("❌ 部署失败: " + e.getMessage());
            eventBus.emit("deploy.failed", d, id);
            throw e;
        } finally {
            history.add(d);
            while (history.size() > 100) history.remove(0);
        }
        return d;
    }

    private void rollingDeploy(Map<String, Object> d, int instances) throws Exception {
        @SuppressWarnings("unchecked")
        var steps = (List<Map<String, Object>>) d.get("steps");
        for (int i = 1; i <= instances; i++) {
            addStep(steps, "滚动更新 " + i + "/" + instances);
            Thread.sleep(2000);
            addStep(steps, "实例 " + i + " 健康检查");
            Thread.sleep(1000);
            eventBus.emit("deploy.progress", Map.of("deployId", d.get("id"), "progress", (i * 100 / instances)), id);
        }
    }

    private void blueGreenDeploy(Map<String, Object> d) throws Exception {
        @SuppressWarnings("unchecked")
        var steps = (List<Map<String, Object>>) d.get("steps");
        addStep(steps, "准备绿色环境"); Thread.sleep(2000);
        addStep(steps, "部署到绿色环境"); Thread.sleep(2000);
        addStep(steps, "健康检查"); Thread.sleep(1000);
        addStep(steps, "切换流量"); Thread.sleep(1000);
        addStep(steps, "关闭蓝色环境"); Thread.sleep(500);
    }

    private void canaryDeploy(Map<String, Object> d) throws Exception {
        @SuppressWarnings("unchecked")
        var steps = (List<Map<String, Object>>) d.get("steps");
        for (int pct : new int[]{10, 30, 50, 100}) {
            addStep(steps, "金丝雀 " + pct + "% 流量"); Thread.sleep(2000);
            addStep(steps, pct + "% 健康检查"); Thread.sleep(1000);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rollback(Map<String, Object> payload) throws Exception {
        String service = (String) payload.get("service");
        String reason = (String) payload.getOrDefault("reason", "manual");
        log("🔄 回滚: " + service + " (" + reason + ")");
        Thread.sleep(2000);
        Map<String, Object> r = Map.of("service", service, "reason", reason, "status", "completed", "timestamp", System.currentTimeMillis());
        eventBus.emit("deploy.rollback", r, id);
        return r;
    }

    private void addStep(List<Map<String, Object>> steps, String desc) {
        steps.add(Map.of("desc", desc, "ts", System.currentTimeMillis()));
        log("  📋 " + desc);
    }

    @Override
    public Map<String, Object> getStatus() {
        var base = super.getStatus();
        var copy = new LinkedHashMap<>(base);
        copy.put("deploymentCount", history.size());
        return copy;
    }
}
