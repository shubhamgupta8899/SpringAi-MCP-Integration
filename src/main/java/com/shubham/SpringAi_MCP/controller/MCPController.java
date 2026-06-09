package com.shubham.SpringAi_MCP.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MCPController {

    private final ChatClient chatClient;

    private final ToolCallbackProvider mcpTool;

    //mcp work on client and server based also
    public MCPController(ChatClient.Builder chatClientBuilder,  ToolCallbackProvider mcpTool){
        this.mcpTool = mcpTool;
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(mcpTool)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String question){

        return chatClient.prompt()
                .user(question)
                .call()
                .content();

    }
}
