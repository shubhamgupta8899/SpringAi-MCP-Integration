# SpringAI-MCP — AI Agent with Model Context Protocol

A Spring Boot application that connects a local LLM (via Ollama) to external tools using the **Model Context Protocol (MCP)**. Instead of hardcoding what tools your AI can use, MCP lets you plug in any compatible tool server — and the AI figures out when and how to use them automatically.

---

## What Even Is MCP?

Think of MCP as a **universal adapter** for AI tools.

Before MCP existed, if you wanted an AI to, say, read files, search the web, or query a database, you had to manually write tool definitions, handle the back-and-forth yourself, and wire everything up by hand for each model. It was messy and non-standard.

MCP changes that. It's an open protocol that defines a clean, standard way for an AI model to **discover** what tools are available and **call** them. The model says "I need to read a file", MCP handles the plumbing, and your application doesn't need to care about the specifics.

### The Two Sides

There's a **client** and a **server** in every MCP setup:

- **MCP Server** — This is the tool provider. It exposes capabilities like "read a file", "run a search", "query a database", etc. You can run these as separate processes (stdio-based) or over HTTP.
- **MCP Client** — This lives inside your app. It talks to the server, fetches the list of available tools, and hands them to your LLM so the model knows what it can do.

In this project, **Spring AI acts as the MCP Client**, and the tool server is defined via `mcp-server.json`.

### How a Tool Call Actually Flows

```
User sends a question
        ↓
ChatClient sends it to Ollama (qwen3 model)
        ↓
Model decides it needs a tool → returns a tool_call in its response
        ↓
Spring AI intercepts it → calls the MCP Server via stdio
        ↓
MCP Server runs the tool → returns the result
        ↓
Result is sent back to the model as context
        ↓
Model generates final answer
        ↓
Response returned to user
```

The important thing here is the **model itself decides** when to use a tool. You just give it the tools and ask a question — the rest is autonomous.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.0.6 |
| AI Abstraction | Spring AI 2.0.0-M8 |
| LLM | Ollama (qwen3 model) |
| Protocol | MCP (Model Context Protocol) — stdio transport |
| API | REST via Spring MVC |

---

## Project Structure

```
SpringAi-MCP/
├── src/
│   └── main/
│       ├── java/com/shubham/SpringAi_MCP/
│       │   └── controller/
│       │       └── MCPController.java       ← REST endpoint + ChatClient setup
│       └── resources/
│           ├── application.properties       ← Ollama config + MCP server path
│           └── mcp-server.json              ← Defines which MCP tool servers to connect
├── pom.xml
└── README.md
```

---

## How This Project Works

### 1. The Controller (`MCPController.java`)

This is the heart of the project. On startup, it builds a `ChatClient` with two important things:

```java
this.chatClient = chatClientBuilder
    .defaultToolCallbacks(mcpTool)       // Give the LLM access to MCP tools
    .defaultAdvisors(new SimpleLoggerAdvisor())  // Log requests/responses for debugging
    .build();
```

`ToolCallbackProvider` is injected by Spring AI automatically — it reads your `mcp-server.json`, spins up the tool servers, fetches their tool definitions, and wraps them in a format the LLM understands.

When a `GET /api/chat?question=...` request comes in, it simply forwards the question to the model. If the model needs a tool, Spring AI handles that loop automatically before returning the final answer.

### 2. MCP Server Config (`mcp-server.json`)

This file tells Spring AI which MCP servers to launch and how. Since we're using **stdio transport**, each server runs as a separate child process that communicates via stdin/stdout. A typical entry looks like:

```json
{
  "mcpServers": {
    "your-tool-name": {
      "command": "npx",
      "args": ["-y", "@some-mcp-server/package"],
      "env": {}
    }
  }
}
```

Spring AI reads this at startup, boots each listed server, and makes their tools available to the model.

### 3. The Model (`qwen3` via Ollama)

We're using `qwen3` running locally through Ollama. It's a capable model that supports tool/function calling — which is a hard requirement for MCP to work. The model needs to be able to emit structured tool-call responses, not just plain text.

The Ollama base URL is pointed to `localhost:11434`, so this runs entirely on your machine. No API keys, no external services.

### 4. Logging

`SimpleLoggerAdvisor` logs every request going to the model and every response coming back. With `logging.level.org.springframework.ai.chat.client.advisor=DEBUG` in properties, you'll see the full tool call cycle in your console — very useful when debugging why the model did or didn't call a tool.

---

## Getting Started

### Prerequisites

- Java 21
- Maven
- [Ollama](https://ollama.ai) installed and running locally
- `qwen3` model pulled: `ollama pull qwen3`
- Node.js (if your MCP servers use `npx`)

### Run It

```bash
# Clone and build
git clone <your-repo-url>
cd SpringAi-MCP
mvn spring-boot:run
```

Once running, hit the API:

```bash
curl "http://localhost:8080/api/chat?question=What tools do you have access to?"
```

Or ask something that actually triggers a tool:

```bash
curl "http://localhost:8080/api/chat?question=List the files in my current directory"
```

---

## Configuration Reference

```properties
# application.properties

spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=qwen3

# Debug logging — shows full tool call cycle
logging.level.org.springframework.ai.chat.client.advisor=DEBUG

# Path to MCP server definitions
spring.ai.mcp.client.stdio.servers-configuration=classpath:mcp-server.json
```

---

## Adding More Tools

This is where MCP really shines. Want to give your AI access to a new tool? You don't touch the Java code at all. Just add another entry in `mcp-server.json`:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/your/allowed/path"]
    },
    "brave-search": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
      "env": {
        "BRAVE_API_KEY": "your-key-here"
      }
    }
  }
}
```

Restart the app, and the model now knows about those tools automatically.

---

## Notes & Known Behaviour

- **Tool calling requires a capable model.** Not all Ollama models support it. `qwen3` works well. If you switch models and tools stop being called, that's likely the reason.
- **stdio transport means child processes.** Each MCP server in your config file gets launched as a subprocess. Keep an eye on memory if you add many servers.
- **Spring AI M8 is pre-release.** This project uses `2.0.0-M8` of Spring AI, which means APIs might shift before the stable release. The MCP client integration in particular has been actively evolving.
- **Model thinking tokens.** `qwen3` uses thinking/reasoning tokens before responding. If you see `<think>` blocks in raw output during debugging, that's normal — Spring AI strips them from the final `.content()` response.

---

## Author

Built by Shubham as part of exploring agentic AI patterns with Spring AI and local LLMs.
