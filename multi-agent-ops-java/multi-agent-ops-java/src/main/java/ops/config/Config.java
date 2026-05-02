package ops.config;

/**
 * 系统配置
 */
public final class Config {

    public static final int DASHBOARD_PORT = 3200;
    public static final int WS_PORT = 3201;
    public static final long HEARTBEAT_INTERVAL = 5000;
    public static final int MAX_RETRIES = 3;
    public static final long TASK_TIMEOUT = 60000;
    public static final int MAX_HISTORY = 1000;
    public static final int MAX_EVENT_HISTORY = 500;

    // Agent配置
    public static final long MONITOR_INTERVAL = 10000;
    public static final double CPU_THRESHOLD = 80.0;
    public static final double MEMORY_THRESHOLD = 85.0;
    public static final long ALERT_DEDUP_WINDOW = 300000;
    public static final long ANALYSIS_WINDOW = 3600000;
    public static final double ANOMALY_THRESHOLD = 2.0;
    public static final long REPORT_INTERVAL = 1800000;
    public static final int MAX_CONCURRENCY = 5;
    public static final long CRITICAL_ESCALATE_AFTER = 300000;
    public static final long WARNING_ESCALATE_AFTER = 600000;

    public enum Priority {
        CRITICAL(0), HIGH(1), MEDIUM(2), LOW(3);

        public final int value;
        Priority(int value) { this.value = value; }
    }

    private Config() {}
}
