package ops.core;

import ops.config.Config;
import java.util.*;
import java.util.concurrent.*;

/**
 * 任务调度器 - 优先级队列、依赖管理、超时重试
 */
public class TaskScheduler {

    private final EventBus eventBus;
    private final List<Task> queue = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Task> running = new ConcurrentHashMap<>();
    private final List<Task> completed = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public TaskScheduler(EventBus eventBus) {
        this.eventBus = eventBus;
        // 定期检查超时
        timer.scheduleAtFixedRate(this::checkTimeouts, 5, 5, TimeUnit.SECONDS);
    }

    /** 创建任务 */
    public Task createTask(String name, String type, Map<String, Object> payload,
                           String priorityName, String assignedAgent) {
        int prio = switch (priorityName) {
            case "critical" -> Config.Priority.CRITICAL.value;
            case "high" -> Config.Priority.HIGH.value;
            case "low" -> Config.Priority.LOW.value;
            default -> Config.Priority.MEDIUM.value;
        };
        Task task = new Task(name, type, payload, prio, priorityName, assignedAgent,
                null, Config.MAX_RETRIES, Config.TASK_TIMEOUT, null);
        queue.add(task);
        sortQueue();
        task.status = "queued";
        eventBus.emit("task.queued", task.toMap(), "scheduler");
        return task;
    }

    /** 创建带依赖的任务 */
    public Task createTaskWithDeps(String name, String type, Map<String, Object> payload,
                                   String priorityName, String assignedAgent, List<String> dependencies) {
        int prio = switch (priorityName) {
            case "critical" -> Config.Priority.CRITICAL.value;
            case "high" -> Config.Priority.HIGH.value;
            case "low" -> Config.Priority.LOW.value;
            default -> Config.Priority.MEDIUM.value;
        };
        Task task = new Task(name, type, payload, prio, priorityName, assignedAgent,
                dependencies, Config.MAX_RETRIES, Config.TASK_TIMEOUT, null);
        queue.add(task);
        sortQueue();
        if (areDepsResolved(task)) {
            task.status = "queued";
        }
        eventBus.emit("task.created", task.toMap(), "scheduler");
        return task;
    }

    /** 取下一个待执行任务 */
    public Task dequeue(String agentType) {
        synchronized (queue) {
            Iterator<Task> it = queue.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.assignedAgent != null && !t.assignedAgent.equals(agentType)) continue;
                if (!areDepsResolved(t)) continue;
                it.remove();
                t.status = "running";
                t.startedAt = System.currentTimeMillis();
                running.put(t.id, t);
                eventBus.emit("task.started", t.toMap(), "scheduler");
                return t;
            }
        }
        return null;
    }

    /** 标记完成 */
    public void completeTask(String taskId, Object result) {
        Task t = running.remove(taskId);
        if (t == null) return;
        t.status = "completed";
        t.completedAt = System.currentTimeMillis();
        t.result = result;
        addToHistory(t);
        eventBus.emit("task.completed", t.toMap(), "scheduler");
        resolveDependents(taskId);
    }

    /** 标记失败 */
    public void failTask(String taskId, String error) {
        Task t = running.remove(taskId);
        if (t == null) return;
        t.retries++;
        t.error = error;
        if (t.retries < t.maxRetries) {
            t.status = "queued";
            long delay = (long) (Config.MAX_RETRIES * Math.pow(2, t.retries - 1));
            timer.schedule(() -> {
                queue.add(t);
                sortQueue();
                eventBus.emit("task.retried", Map.of("task", t.toMap(), "attempt", t.retries), "scheduler");
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            t.status = "failed";
            t.completedAt = System.currentTimeMillis();
            addToHistory(t);
            eventBus.emit("task.failed", t.toMap(), "scheduler");
        }
    }

    /** 超时检查 */
    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        List<Task> timedOut = new ArrayList<>();
        for (var entry : running.entrySet()) {
            Task t = entry.getValue();
            if (now - t.startedAt > t.timeout) {
                timedOut.add(t);
            }
        }
        for (Task t : timedOut) {
            running.remove(t.id);
            t.status = "timeout";
            t.error = "Timeout after " + t.timeout + "ms";
            t.completedAt = System.currentTimeMillis();
            addToHistory(t);
            eventBus.emit("task.timeout", t.toMap(), "scheduler");
            failTask(t.id, t.error);
        }
    }

    /** 获取状态 */
    public Map<String, Object> getStatus() {
        List<Map<String, Object>> runningTasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Task t : running.values()) {
            runningTasks.add(Map.of(
                "id", t.id, "name", t.name, "type", t.type,
                "assignedAgent", t.assignedAgent != null ? t.assignedAgent : "",
                "elapsed", now - t.startedAt
            ));
        }
        return Map.of(
            "queued", queue.size(),
            "running", running.size(),
            "completed", completed.size(),
            "runningTasks", runningTasks
        );
    }

    public List<Task> getQueue() { return List.copyOf(queue); }
    public Map<String, Task> getRunning() { return Map.copyOf(running); }
    public List<Task> getCompleted() { return List.copyOf(completed); }

    // --- 内部 ---

    private boolean areDepsResolved(Task task) {
        return task.dependencies.stream().allMatch(depId ->
            completed.stream().anyMatch(t -> t.id.equals(depId) && "completed".equals(t.status))
        );
    }

    private void resolveDependents(String completedId) {
        synchronized (queue) {
            for (Task t : queue) {
                if (t.dependencies.contains(completedId) && "pending".equals(t.status)) {
                    if (areDepsResolved(t)) {
                        t.status = "queued";
                        eventBus.emit("task.ready", t.toMap(), "scheduler");
                    }
                }
            }
        }
    }

    private void sortQueue() {
        queue.sort(Comparator.comparingInt((Task t) -> t.priority)
                             .thenComparingLong(t -> t.createdAt));
    }

    private void addToHistory(Task t) {
        completed.add(t);
        while (completed.size() > Config.MAX_HISTORY) completed.remove(0);
    }

    public void shutdown() {
        timer.shutdown();
    }
}
