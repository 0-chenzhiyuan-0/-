package ops;

import ops.agents.*;
import ops.config.Config;
import ops.core.Orchestrator;
import ops.dashboard.Dashboard;
import java.util.Map;

/**
 * 多Agent协同运营自动化系统 - Java版
 *
 * 启动: java -jar multi-agent-ops.jar
 * 访问: http://localhost:3200
 */
public class App {

    public static void main(String[] args) throws Exception {
        System.out.println("""
        ╔══════════════════════════════════════════════════════╗
        ║        🤖 多Agent协同运营自动化系统 v1.0.0          ║
        ║                  (Java Edition)                     ║
        ║                                                      ║
        ║   Agents: Monitor · Deployer · Alert · Analyst       ║
        ║           Executor · Scheduler                       ║
        ║                                                      ║
        ║   Dashboard: http://localhost:%d                    ║
        ╚══════════════════════════════════════════════════════╝
        """.formatted(Config.DASHBOARD_PORT));

        // 1. 创建编排器
        Orchestrator orch = new Orchestrator();

        // 2. 注册Agent
        orch.registerAgent(new MonitorAgent(orch.eventBus, orch.taskScheduler));
        orch.registerAgent(new DeployerAgent(orch.eventBus, orch.taskScheduler));
        orch.registerAgent(new AlertAgent(orch.eventBus, orch.taskScheduler));
        orch.registerAgent(new AnalystAgent(orch.eventBus, orch.taskScheduler));
        orch.registerAgent(new ExecutorAgent(orch.eventBus, orch.taskScheduler));
        orch.registerAgent(new SchedulerAgent(orch.eventBus, orch.taskScheduler));

        // 3. 启动
        orch.start();

        // 4. Dashboard
        try {
            new Dashboard(orch, Config.DASHBOARD_PORT).start();
        } catch (Exception e) {
            System.err.println("⚠️ Dashboard启动失败: " + e.getMessage());
        }

        // 5. 优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            orch.stop();
        }));

        // 6. 示例工作流
        Thread.sleep(5000);
        System.out.println("\n📋 提交示例工作流...\n");

        orch.submitTask("健康检查", "health_check", Map.of("target", "production"), "high", "monitor");
        orch.submitTask("生成报告", "generate_report", Map.of(), "low", "analyst");
        orch.submitTask("模拟部署", "deploy", Map.of(
            "service", "web-app", "version", "2.1.0",
            "strategy", "rolling", "instances", 3
        ), "critical", "deployer");

        // 7. 状态打印
        while (true) {
            Thread.sleep(30000);
            var s = orch.getSystemStatus();
            var t = (Map<?, ?>) s.get("tasks");
            var m = (Map<?, ?>) s.get("metrics");
            System.out.printf("📊 Agent:%s | 队列:%s | 运行:%s | 完成:%s | 事件:%s%n",
                ((Map<?, ?>) s.get("agents")).size(),
                t.get("queued"), t.get("running"), t.get("completed"),
                m.get("eventsProcessed"));
        }
    }
}
