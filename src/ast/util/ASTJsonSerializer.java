package ast.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class ASTJsonSerializer {

    public static String toJson(Object root) {
        StringBuilder sb = new StringBuilder();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        toJson(root, sb, 0, visited);
        return sb.toString();
    }

    private static void toJson(Object obj, StringBuilder sb, int indent, Set<Object> visited) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        if (obj instanceof String) {
            sb.append("\"").append(escapeString((String) obj)).append("\"");
            return;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }
        
        Class<?> clazz = obj.getClass();
        if (clazz.isEnum()) {
            sb.append("\"").append(obj.toString()).append("\"");
            return;
        }

        if (visited.contains(obj)) {
            sb.append("\"[Circular Reference]\"");
            return;
        }
        visited.add(obj);

        if (obj instanceof Iterable) {
            sb.append("[\n");
            boolean first = true;
            for (Object item : (Iterable<?>) obj) {
                if (!first) sb.append(",\n");
                indent(sb, indent + 1);
                toJson(item, sb, indent + 1, visited);
                first = false;
            }
            sb.append("\n");
            indent(sb, indent);
            sb.append("]");
            visited.remove(obj);
            return;
        }
        
        if (clazz.isArray()) {
            sb.append("[\n");
            Object[] arr = (Object[]) obj;
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(",\n");
                indent(sb, indent + 1);
                toJson(arr[i], sb, indent + 1, visited);
            }
            sb.append("\n");
            indent(sb, indent);
            sb.append("]");
            visited.remove(obj);
            return;
        }

        if (obj instanceof Map) {
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",\n");
                indent(sb, indent + 1);
                sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\": ");
                toJson(entry.getValue(), sb, indent + 1, visited);
                first = false;
            }
            sb.append("\n");
            indent(sb, indent);
            sb.append("}");
            visited.remove(obj);
            return;
        }

        // Custom object mapping
        if (clazz.getName().startsWith("java.")) {
            sb.append("\"").append(escapeString(obj.toString())).append("\"");
            visited.remove(obj);
            return;
        }

        sb.append("{\n");
        indent(sb, indent + 1);
        sb.append("\"_type\": \"").append(clazz.getSimpleName()).append("\"");

        List<Field> fields = getAllFields(clazz);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            // Skip parent pointers and redundant children list to avoid exponential size
            if (name.equals("parent") || name.equals("children") || name.equals("enclosingScope") || name.equals("scope")) continue;

            field.setAccessible(true);
            Object value;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                continue;
            }

            sb.append(",\n");
            indent(sb, indent + 1);
            sb.append("\"").append(name).append("\": ");
            toJson(value, sb, indent + 1, visited);
        }

        sb.append("\n");
        indent(sb, indent);
        sb.append("}");
        
        visited.remove(obj);
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }

    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
