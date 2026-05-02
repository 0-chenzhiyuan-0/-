package ops.dashboard;

import com.sun.net.httpserver.*;
import ops.core.Orchestrator;
import ops.util.Json;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dashboard - HTTP REST API + 内嵌HTML
 */
public class Dashboard {

    private final Orchestrator orch;
    private final int port;

    public Dashboard(Orchestrator orch, int port) {
        this.orch = orch;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API路由
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/agents", this::handleAgents);
        server.createContext("/api/tasks", this::handleTasks);
        server.createContext("/api/alerts", this::handleAlerts);
        server.createContext("/api/deploy", this::handleDeploy);
        server.createContext("/api/execute", this::handleExecute);
        server.createContext("/", this::handleIndex);

        server.setExecutor(null);
        server.start();
        System.out.println("\n🌐 Dashboard: http://localhost:" + port);
    }

    // --- API Handlers ---

    private void handleStatus(HttpExchange ex) throws IOException {
        sendJson(ex, orch.getSystemStatus());
    }

    private void handleAgents(HttpExchange ex) throws IOException {
        sendJson(ex, orch.getAgents().values().stream().map(a -> a.getStatus()).toList());
    }

    private void handleTasks(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            sendJson(ex, orch.taskScheduler.getStatus());
        } else if ("POST".equals(ex.getRequestMethod())) {
            // 提交任务
            String body = readBody(ex);
            @SuppressWarnings("unchecked")
            var data = Json.parseMap(body);
            var task = orch.submitTask(
                (String) data.get("name"), (String) data.get("type"),
                (java.util.Map<String, Object>) data.get("payload"),
                (String) data.getOrDefault("priority", "medium"),
                (String) data.get("assignedAgent")
            );
            sendJson(ex, java.util.Map.of("success", true, "task", task.toMap()));
        }
    }

    private void handleAlerts(HttpExchange ex) throws IOException {
        var agent = orch.getAgents().get("alert");
        if (agent instanceof ops.agents.AlertAgent alertAgent) {
            sendJson(ex, alertAgent.getStatus());
        } else {
            sendJson(ex, java.util.Map.of("alerts", java.util.List.of()));
        }
    }

    private void handleDeploy(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            String body = readBody(ex);
            @SuppressWarnings("unchecked")
            var data = Json.parseMap(body);
            orch.eventBus.emit("deploy.request", data, "dashboard");
            sendJson(ex, java.util.Map.of("success", true, "message", "部署请求已提交"));
        }
    }

    private void handleExecute(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            String body = readBody(ex);
            @SuppressWarnings("unchecked")
            var data = Json.parseMap(body);
            orch.eventBus.emit("executor.command", data, "dashboard");
            sendJson(ex, java.util.Map.of("success", true, "message", "命令已提交"));
        }
    }

    // --- 内嵌HTML ---

    private void handleIndex(HttpExchange ex) throws IOException {
        String html = INDEX_HTML;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // --- 工具方法 ---

    private void sendJson(HttpExchange ex, Object data) throws IOException {
        String json = Json.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // --- 内嵌HTML ---

    private static final String INDEX_HTML = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>🤖 多Agent协同运营系统</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
:root{--bg:#0f1117;--s:#1a1d27;--s2:#232736;--b:#2d3148;--t:#e4e6f0;--t2:#8b8fa3;--a:#6366f1;--a2:#818cf8;--g:#22c55e;--r:#ef4444;--y:#eab308;--bl:#3b82f6;--p:#a855f7}
body{font-family:'SF Mono','Cascadia Code',Consolas,monospace;background:var(--bg);color:var(--t);min-height:100vh}
.hdr{background:var(--s);border-bottom:1px solid var(--b);padding:14px 24px;display:flex;justify-content:space-between;align-items:center}
.hdr h1{font-size:17px}.hdr .st{display:flex;gap:14px;font-size:12px}
.dot{width:8px;height:8px;border-radius:50%;display:inline-block;margin-right:5px;background:var(--g);box-shadow:0 0 8px var(--g)}
.ct{display:grid;grid-template-columns:1fr 1fr 1fr;gap:14px;padding:14px 24px;max-width:1600px;margin:0 auto}
.cd{background:var(--s);border:1px solid var(--b);border-radius:12px;padding:16px}
.cd h2{font-size:13px;color:var(--t2);margin-bottom:10px;display:flex;align-items:center;gap:8px}
.cd h2 .bg{background:var(--a);color:#fff;font-size:10px;padding:2px 8px;border-radius:10px}
.ag{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.ac{background:var(--s2);border-radius:8px;padding:10px;font-size:11px}
.ac .n{font-weight:600;margin-bottom:3px}.ac .m{color:var(--t2);font-size:10px}
.sb{display:inline-block;padding:2px 7px;border-radius:4px;font-size:10px;font-weight:600;margin-top:5px}
.sb.idle{background:rgba(34,197,94,.15);color:var(--g)}.sb.busy{background:rgba(234,179,8,.15);color:var(--y)}
.mr{display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:8px;margin-bottom:10px}
.mb{background:var(--s2);border-radius:8px;padding:12px;text-align:center}
.mb .v{font-size:22px;font-weight:700}.mb .l{font-size:10px;color:var(--t2);margin-top:3px}
.sf{grid-column:1/-1}
.acts{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}
.btn{background:var(--a);color:#fff;border:none;padding:7px 14px;border-radius:6px;font-size:11px;cursor:pointer;font-family:inherit}
.btn:hover{background:var(--a2)}.btn.r{background:var(--r)}.btn.g{background:var(--g)}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.5}}.live{animation:pulse 2s infinite}
</style>
</head>
<body>
<div class="hdr"><h1>🤖 多Agent协同运营系统 (Java)</h1><div class="st"><span><span class="dot live"></span> 实时</span><span id="up">--</span></div></div>
<div class="ct">
<div class="cd sf"><h2>📊 系统指标</h2><div class="mr">
<div class="mb"><div class="v" id="m0" style="color:var(--a)">0</div><div class="l">Agent</div></div>
<div class="mb"><div class="v" id="m1" style="color:var(--bl)">0</div><div class="l">任务</div></div>
<div class="mb"><div class="v" id="m2" style="color:var(--g)">0</div><div class="l">完成</div></div>
<div class="mb"><div class="v" id="m3" style="color:var(--r)">0</div><div class="l">失败</div></div>
</div></div>
<div class="cd"><h2>🤖 Agent <span class="bg" id="ac">0</span></h2><div class="ag" id="al"></div></div>
<div class="cd"><h2>📋 任务状态</h2><div id="tl" style="font-size:12px;color:var(--t2);text-align:center;padding:20px">加载中...</div></div>
<div class="cd"><h2>🔔 告警</h2><div id="all" style="font-size:12px;color:var(--t2);text-align:center;padding:20px">暂无告警</div></div>
<div class="cd sf"><h2>⚡ 操作</h2><div class="acts">
<button class="btn" onclick="sub('health_check','monitor')">🔍 健康检查</button>
<button class="btn" onclick="sub('generate_report','analyst')">📊 报告</button>
<button class="btn g" onclick="dep()">🚀 部署</button>
<button class="btn" onclick="sub('flush_cache','executor',{cacheType:'all'})">🗑️ 清缓存</button>
</div></div>
</div>
<script>
const API='';
async function load(){
try{
const[r1,r2]=await Promise.all([fetch(API+'/api/status'),fetch(API+'/api/agents')]);
const st=await r1.json();const ag=await r2.json();
const m=st.metrics||{};const t=st.tasks||{};
document.getElementById('m0').textContent=ag.length;
document.getElementById('m1').textContent=m.totalTasks||0;
document.getElementById('m2').textContent=m.completedTasks||0;
document.getElementById('m3').textContent=m.failedTasks||0;
document.getElementById('ac').textContent=ag.length;
if(st.uptime){const h=Math.floor(st.uptime/3600000),mi=Math.floor((st.uptime%3600000)/60000);document.getElementById('up').textContent=h+'h '+mi+'m'}
document.getElementById('al').innerHTML=ag.map(a=>'<div class="ac"><div class="n">'+(a.name||a.type)+'</div><div class="m">处理:'+a.processedCount+' 错误:'+a.errorCount+'</div><span class="sb '+(a.status||'idle')+'">'+(a.status||'idle')+'</span></div>').join('');
document.getElementById('tl').innerHTML='队列: '+t.queued+' | 运行中: '+t.running+' | 已完成: '+t.completed;
}catch(e){console.error(e)}
}
async function sub(type,agent,payload={}){await fetch(API+'/api/tasks',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:'手动:'+type,type,payload,priority:'high',assignedAgent:agent})});alert('✅ 已提交');load()}
async function dep(){const s=prompt('服务名:','web-app'),v=prompt('版本:','2.0.0');if(!s||!v)return;await fetch(API+'/api/deploy',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({service:s,version:v,strategy:'rolling',instances:3})});alert('🚀 已提交');load()}
load();setInterval(load,3000);
</script></body></html>""";
}
