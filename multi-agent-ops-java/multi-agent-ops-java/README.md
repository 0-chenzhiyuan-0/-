# 🤖 多Agent协同运营自动化系统 (Java版)

纯Java 17实现，零外部依赖。

## 系统架构

```
┌─────────────────────── Orchestrator ───────────────────────┐
│  EventBus (发布订阅)  ←→  TaskScheduler (优先级队列)       │
│                    ↓                                        │
│  ┌──────────┐ ┌──────────┐ ┌───────┐ ┌─────────┐          │
│  │ Monitor  │ │ Deployer │ │ Alert │ │ Analyst │          │
│  └──────────┘ └──────────┘ └───────┘ └─────────┘          │
│  ┌──────────┐ ┌──────────┐                                 │
│  │ Executor │ │ Scheduler│  ←→  Dashboard (HTTP)           │
│  └──────────┘ └──────────┘                                 │
└────────────────────────────────────────────────────────────┘
```

## 快速开始

### 方式一：Maven（推荐）
```bash
mvn clean package
java -jar target/multi-agent-ops-1.0.0.jar
```

### 方式二：纯javac（无需Maven）
```bash
chmod +x build.sh
./build.sh
java -jar target/multi-agent-ops.jar
```

### 方式三：Windows
```cmd
mkdir target\classes
javac -d target\classes -source 17 -target 17 src\main\java\ops\*.java src\main\java\ops\**\*.java
cd target\classes
jar cfe ..\multi-agent-ops.jar ops.App *
cd ..
java -jar multi-agent-ops.jar
```

启动后访问: **http://localhost:3200**

## Agent说明

| Agent | 职责 |
|-------|------|
| MonitorAgent | CPU/内存采集、阈值告警 |
| DeployerAgent | 滚动/蓝绿/金丝雀部署 |
| AlertAgent | 告警去重、分级、升级 |
| AnalystAgent | Z-Score异常检测、趋势预测 |
| ExecutorAgent | 服务重启、扩缩容、自动修复 |
| SchedulerAgent | 定时任务、工作流编排 |

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/status | 系统状态 |
| GET | /api/agents | Agent列表 |
| GET | /api/tasks | 任务状态 |
| POST | /api/tasks | 提交任务 |
| GET | /api/alerts | 告警列表 |
| POST | /api/deploy | 触发部署 |
| POST | /api/execute | 执行命令 |

## 目录结构

```
src/main/java/ops/
├── App.java              # 主入口
├── config/Config.java    # 配置
├── core/
│   ├── EventBus.java     # 事件总线
│   ├── Task.java         # 任务对象
│   ├── TaskScheduler.java # 任务调度
│   ├── BaseAgent.java    # Agent基类
│   └── Orchestrator.java # 编排器
├── agents/
│   ├── MonitorAgent.java
│   ├── DeployerAgent.java
│   ├── AlertAgent.java
│   ├── AnalystAgent.java
│   ├── ExecutorAgent.java
│   └── SchedulerAgent.java
├── dashboard/Dashboard.java
└── util/Json.java        # JSON工具
```

## 前提

- Java 17+
