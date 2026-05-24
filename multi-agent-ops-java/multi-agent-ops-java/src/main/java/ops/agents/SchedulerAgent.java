package ops.agents;

import ops.core.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 调度Agent - Cron定时、工作流编排
 */
public class SchedulerAgent extends BaseAgent {

    private final Map<String, ScheduledFuture<?>> cronJobs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public SchedulerAgent(EventBus bus, TaskScheduler sched) {
        super("scheduler", "SchedulerAgent", bus, sched);
    }

    @Override
    protected void onStart() {
        // 每5分钟健康检查
        addCronJob("health_check", 5, TimeUnit.MINUTES, "定期健康检查", "health_check", "monitor");
        // 每30分钟报告
        addCronJob("periodic_report", 30, TimeUnit.MINUTES, "定期报告", "generate_report", "analyst");
        // 每天清理
        addCronJob("daily_cleanup", 24, TimeUnit.HOURS, "每日清理", "flush_cache", "executor");
        log("📅 已注册 " + cronJobs.size() + " 个定时任务");
    }

    @Override
    protected void onStop() {
        scheduler.shutdown();
    }

    @Override
    protected boolean canHandle(Task task) {
        return "run_workflow".equals(task.type);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object process(Task task) throws Exception {
        if ("run_workflow".equals(task.type)) {
            return runWorkflow(task.payload);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runWorkflow(Map<String, Object> payload) throws Exception {
        String name = (String) payload.get("name");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");

        log("🔄 工作流: " + name + " (" + steps.size() + "步)");
        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("id", "wf-" + System.currentTimeMillis());
        wf.put("name", name);
        wf.put("status", "running");
        wf.put("startedAt", System.currentTimeMillis());
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String stepName = (String) step.get("name");
            log("  步骤 " + (i + 1) + "/" + steps.size() + ": " + stepName);
            try {
                Map<String, Object> taskDef = (Map<String, Object>) step.get("task");
                taskScheduler.createTask(
                    stepName, (String) taskDef.get("type"),
                    (Map<String, Object>) taskDef.get("payload"),
                    "high", (String) taskDef.get("assignedAgent")
                );
                results.add(Map.of("step", i + 1, "name", stepName, "status", "submitted"));
                Number wait = (Number) step.get("waitBeforeNext");
                if (wait != null) Thread.sleep(wait.longValue());
            } catch (Exception e) {
                results.add(Map.of("step", i + 1, "name", stepName, "status", "failed", "error", e.getMessage()));
                Boolean cont = (Boolean) step.get("continueOnError");
                if (cont == null || !cont) { wf.put("status", "failed"); break; }
            }
        }
        if ("running".equals(wf.get("status"))) wf.put("status", "completed");
        wf.put("completedAt", System.currentTimeMillis());
        wf.put("results", results);
        eventBus.emit("workflow.completed", wf, id);
        return wf;
    }

    private void addCronJob(String name, long interval, TimeUnit unit,
                            String taskName, String taskType, String agent) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            log("⏰ 触发: " + name);
            taskScheduler.createTask("[定时] " + taskName, taskType, Map.of(), "medium", agent);
            eventBus.emit("scheduler.triggered", Map.of("name", name), id);
        }, interval, interval, unit);
        cronJobs.put(name, future);
    }

    @Override
    public Map<String, Object> getStatus() {
        var base = super.getStatus();
        var copy = new LinkedHashMap<>(base);
        copy.put("cronJobs", cronJobs.size());
        return copy;
    }
}
