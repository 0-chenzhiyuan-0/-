package ops.util;

import java.util.*;

/**
 * 轻量JSON序列化（无需外部依赖）
 */
public class Json {

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) return "\"" + escape(s) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(e.getKey().toString())).append("\":").append(toJson(e.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (obj instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : c) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        if (obj instanceof Object[] arr) {
            return toJson(Arrays.asList(arr));
        }
        if (obj instanceof Map.Entry<?, ?> e) {
            return "{\"" + escape(e.getKey().toString()) + "\":" + toJson(e.getValue()) + "}";
        }
        // 兜底: 转为字符串
        return "\"" + escape(obj.toString()) + "\"";
    }

    /** 解析为Map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String json) {
        Object result = fromJson(json);
        if (result instanceof Map) return (Map<String, Object>) result;
        return new LinkedHashMap<>();
    }

    /** 解析为List */
    @SuppressWarnings("unchecked")
    public static List<Object> parseList(String json) {
        Object result = fromJson(json);
        if (result instanceof List) return (List<Object>) result;
        return new ArrayList<>();
    }

    /** 简单的JSON解析 (仅支持Map/List/String/Number/Boolean) */
    @SuppressWarnings("unchecked")
    public static Object fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        json = json.trim();
        if (json.equals("null")) return null;
        if (json.equals("true")) return true;
        if (json.equals("false")) return false;
        if (json.startsWith("\"")) return unescape(json.substring(1, json.length() - 1));
        if (json.startsWith("{")) return parseObject(json);
        if (json.startsWith("[")) return parseArray(json);
        // Number
        try { return Long.parseLong(json); } catch (Exception e1) {
            try { return Double.parseDouble(json); } catch (Exception e2) { return json; }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) return map;
        List<String> tokens = splitTokens(body);
        for (String token : tokens) {
            int colon = findColon(token);
            if (colon < 0) continue;
            String key = token.substring(0, colon).trim();
            if (key.startsWith("\"")) key = key.substring(1, key.length() - 1);
            String val = token.substring(colon + 1).trim();
            map.put(key, fromJson(val));
        }
        return map;
    }

    public static List<Object> parseArray(String json) {
        List<Object> list = new ArrayList<>();
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) return list;
        List<String> tokens = splitTokens(body);
        for (String token : tokens) {
            list.add(fromJson(token.trim()));
        }
        return list;
    }

    // --- 内部工具 ---

    private static List<String> splitTokens(String s) {
        List<String> tokens = new ArrayList<>();
        int depth = 0; boolean inStr = false; boolean escaped = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) { current.append(c); escaped = false; continue; }
            if (c == '\\') { current.append(c); escaped = true; continue; }
            if (c == '"') { inStr = !inStr; current.append(c); continue; }
            if (inStr) { current.append(c); continue; }
            if (c == '{' || c == '[') { depth++; current.append(c); continue; }
            if (c == '}' || c == ']') { depth--; current.append(c); continue; }
            if (c == ',' && depth == 0) {
                tokens.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString().trim());
        return tokens;
    }

    private static int findColon(String s) {
        boolean inStr = false; boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (c == ':' && !inStr) return i;
        }
        return -1;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
