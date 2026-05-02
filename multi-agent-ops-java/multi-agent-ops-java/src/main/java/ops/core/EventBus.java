package ops.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 事件总线 - 发布订阅模式，支持通配符
 */
public class EventBus {

    private final Map<String, List<Subscription>> listeners = new ConcurrentHashMap<>();
    private final List<Event> history = Collections.synchronizedList(new ArrayList<>());
    private final int maxHistory;

    public EventBus(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public EventBus() {
        this(500);
    }

    /** 订阅事件 */
    public void on(String topic, Consumer<Event> handler, String subscriberId) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                 .add(new Subscription(handler, subscriberId));
    }

    /** 取消订阅 */
    public void off(String topic, Consumer<Event> handler) {
        List<Subscription> subs = listeners.get(topic);
        if (subs != null) {
            subs.removeIf(s -> s.handler == handler);
        }
    }

    /** 发布事件 */
    public void emit(String topic, Object payload, String publisherId) {
        Event event = new Event(topic, payload, publisherId, System.currentTimeMillis());
        history.add(event);
        while (history.size() > maxHistory) history.remove(0);

        // 精确匹配
        List<Subscription> exact = listeners.getOrDefault(topic, List.of());
        // 全局通配符
        List<Subscription> wildcard = listeners.getOrDefault("*", List.of());
        // 部分通配符 (task.* 匹配 task.created)
        List<Subscription> partial = new ArrayList<>();
        for (var entry : listeners.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.endsWith(".*") && topic.startsWith(pattern.substring(0, pattern.length() - 1))) {
                partial.addAll(entry.getValue());
            }
        }

        List<Subscription> all = new ArrayList<>();
        all.addAll(exact);
        all.addAll(wildcard);
        all.addAll(partial);

        for (Subscription sub : all) {
            try {
                sub.handler.accept(event);
            } catch (Exception e) {
                System.err.printf("EventBus error [%s]: %s%n", sub.subscriberId, e.getMessage());
            }
        }
    }

    public void emit(String topic, Object payload) {
        emit(topic, payload, "system");
    }

    /** 获取事件历史 */
    public List<Event> getHistory(String topic, int limit) {
        List<Event> filtered = topic == null
            ? new ArrayList<>(history)
            : history.stream().filter(e -> e.topic.equals(topic) || e.topic.startsWith(topic)).toList();
        int from = Math.max(0, filtered.size() - limit);
        return filtered.subList(from, filtered.size());
    }

    public List<Event> getHistory() {
        return getHistory(null, 50);
    }

    // --- 内部类型 ---

    public record Event(String topic, Object payload, String publisherId, long timestamp) {}

    static class Subscription {
        final Consumer<Event> handler;
        final String subscriberId;
        Subscription(Consumer<Event> handler, String subscriberId) {
            this.handler = handler;
            this.subscriberId = subscriberId;
        }
    }
}
