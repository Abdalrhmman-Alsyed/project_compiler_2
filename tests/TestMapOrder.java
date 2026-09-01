import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestMapOrder {
    public static void main(String[] args) {
        System.out.println("=== اختبار LinkedHashMap ===");
        Map<String, Integer> linkedMap = new LinkedHashMap<>();
        linkedMap.put("x_var", 10);
        linkedMap.put("y_var", 20);
        linkedMap.put("z_var", 30);
        linkedMap.put("alpha", 40);
        linkedMap.put("beta", 50);

        for (String key : linkedMap.keySet()) {
            System.out.println(key + " : " + linkedMap.get(key));
        }

        System.out.println("\n=== اختبار HashMap العادية ===");
        Map<String, Integer> hashMap = new HashMap<>();
        hashMap.put("x_var", 10);
        hashMap.put("y_var", 20);
        hashMap.put("z_var", 30);
        hashMap.put("alpha", 40);
        hashMap.put("beta", 50);

        for (String key : hashMap.keySet()) {
            System.out.println(key + " : " + hashMap.get(key));
        }
    }
}
