package output;

import java.util.List;
import java.util.Map;

/** Minimal JSON serializer (no extra libraries). */
final class Json {
    private Json() {}

    static String pretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append("{\n");
            int i = 0;
            int n = map.size();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                pad(sb, indent + 1);
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\": ");
                write(sb, e.getValue(), indent + 1);
                if (++i < n) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                pad(sb, indent + 1);
                write(sb, list.get(i), indent + 1);
                if (i + 1 < list.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
