///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:0.35.0
//DEPS dev.langchain4j:langchain4j-google-ai-gemini:0.35.0
//DEPS org.slf4j:slf4j-simple:2.0.12

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;

public class test_tools {
    @Tool("Test tool")
    public String testTool() {
        return "Tested!";
    }

    public static void main(String[] args) {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new test_tools());
        System.out.println("Tools found: " + specs.size());
    }
}
