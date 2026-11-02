package com.campusconnect.campus_ai.config;

import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-register all Tool beans with the ToolRegistry at startup.
 * This makes the tool system extensible — just add a new @Component Tool
 * and it will be automatically registered via Spring's component scanning
 * (List&lt;Tool&gt; autowiring picks up all Tool beans).
 */
@Configuration
public class ToolAutoConfiguration {

    private final ToolRegistry toolRegistry;
    private final List<Tool> tools;

    public ToolAutoConfiguration(ToolRegistry toolRegistry,
                                 List<Tool> tools) {
        this.toolRegistry = toolRegistry;
        this.tools = tools;
    }

    @PostConstruct
    public void registerTools() {
        for (Tool tool : tools) {
            toolRegistry.register(tool);
        }
        System.out.println("Registered tools: " + toolRegistry.getAllTools().stream()
                .map(Tool::name)
                .toList());
    }
}