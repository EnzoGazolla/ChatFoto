///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:0.35.0

public class test_json {
    public static void main(String[] args) {
        String json = "{\"prompt\": \"cachorro astronauta\"}";
        java.util.Map map = dev.langchain4j.internal.Json.fromJson(json, java.util.Map.class);
        System.out.println(map.values().iterator().next());
    }
}
