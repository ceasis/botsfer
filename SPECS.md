# SPECS.md — Botsfer Technical Specification

## Overview

Botsfer is a floating desktop chatbot built with **Java 17**, **Spring Boot 3.5.3**, **JavaFX 21**, and **Spring AI 1.0.1**. A swirling animated ball sits on the desktop; double-click expands a chat panel. It connects to OpenAI for intelligent tool-calling conversations, falls back to regex-based commands offline, and integrates with 9 messaging platforms. It can control the PC, collect files, browse the web, capture screenshots, and maintain persistent conversation history.

**Main class:** `com.botsfer.FloatingAppLauncher`
**Default port:** `8765` (configurable via `BOTSFER_PORT` env var)
**Build:** Maven — `mvn clean package -DskipTests` / `mvn spring-boot:run`

---

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│  JavaFX Transparent Window (FloatingAppLauncher)               │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  WebView  ──►  index.html / app.js / style.css            │ │
│  │            ◄──  window.java bridge (WindowBridge)         │ │
│  └───────────────────────────────────────────────────────────┘ │
│  Scene-level mouse event filters (ball drag, click forwarding) │
└──────────────────────────┬─────────────────────────────────────┘
                           │ HTTP (localhost:8765)
┌──────────────────────────▼─────────────────────────────────────┐
│  Spring Boot Web Server                                        │
│                                                                │
│  ChatController ──► ChatService ──► Spring AI ChatClient       │
│       /api/chat          │              │                      │
│       /api/chat/async    │         ┌────▼────────────────┐     │
│       /api/chat/status   │         │  25 @Tool methods   │     │
│                          │         │  SystemTools (8)    │     │
│                          │         │  BrowserTools (5)   │     │
│                          │         │  FileTools (4)      │     │
│                          │         │  FileSystemTools (4)│     │
│                          │         │  ChatHistoryTool (3)│     │
│                          │         │  TaskStatusTool (1) │     │
│                          │         └─────────────────────┘     │
│                          │                                     │
│                          ├──► PcAgentService (regex fallback)  │
│                          │                                     │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ Platform │  │ Core Services│  │ Persistent Storage       │  │
│  │ Webhooks │  │              │  │                          │  │
│  │ Viber    │  │ SystemCtrl   │  │ TranscriptService        │  │
│  │ Telegram │  │ BrowserCtrl  │  │   ~/botsfer_data/history │  │
│  │ Discord  │  │ FileCollector│  │ MemoryService            │  │
│  │ Slack    │  │ Screenshot   │  │   ./memory/              │  │
│  │ WhatsApp │  │ NativeVoice  │  │ ScreenshotService        │  │
│  │ Messenger│  │              │  │   ~/botsfer_data/screens │  │
│  │ LINE     │  └──────────────┘  └──────────────────────────┘  │
│  │ Teams    │                                                  │
│  │ WeChat   │  Skills: DiskScan (/api/skills/diskscan/*)       │
│  │ Signal   │                                                  │
│  └──────────┘                                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
src/main/java/com/botsfer/
├── BotsferApplication.java              # Spring Boot @SpringBootApplication entry
├── FloatingAppLauncher.java             # JavaFX Application — transparent window + WebView
├── WindowBridge.java                    # JS ↔ Java bridge (expand/collapse/drag/voice)
├── NativeVoiceService.java              # Microphone capture → WAV → OpenAI transcription
├── ChatController.java                  # REST: /api/chat, /api/chat/async, /api/chat/status
├── ChatService.java                     # Core reply logic — Spring AI or regex fallback
├── TranscriptService.java              # Chat history — daily files + 100-message ring buffer
├── ScreenshotService.java              # Periodic desktop captures with auto-cleanup
│
├── agent/
│   ├── AiConfig.java                    # ChatClient + ChatMemory beans
│   ├── PcAgentService.java              # Regex-based command interpreter (offline fallback)
│   ├── SystemContextProvider.java       # System prompt builder (username, OS, time)
│   ├── SystemControlService.java        # Windows process/app control
│   ├── BrowserControlService.java       # Browser automation
│   ├── FileCollectorService.java        # Scan PC for files by category
│   └── tools/
│       ├── SystemTools.java             # 8 @Tool methods → SystemControlService
│       ├── BrowserTools.java            # 5 @Tool methods → BrowserControlService
│       ├── FileTools.java               # 4 @Tool methods → FileCollectorService (2 async)
│       ├── FileSystemTools.java         # 4 @Tool methods (open/copy/delete/count)
│       ├── ChatHistoryTool.java         # 3 @Tool methods → TranscriptService
│       ├── TaskStatusTool.java          # 1 @Tool method (background task status)
│       └── ToolExecutionNotifier.java   # Status message queue for frontend polling
│
├── memory/
│   ├── MemoryConfig.java                # app.memory.* configuration
│   └── MemoryService.java              # File-based key-value store
│
├── skills/
│   ├── package-info.java                # Skills convention documentation
│   └── diskscan/
│       ├── DiskScanConfig.java          # app.skills.diskscan.* configuration
│       ├── DiskScanService.java         # Browse/search filesystem
│       └── DiskScanController.java      # GET /api/skills/diskscan/*
│
└── [Platform integrations — 9 platforms, 3-4 classes each]
    ├── ViberConfig / ViberApiClient / ViberWebhookController / ViberWebhookRegistrar
    ├── TelegramConfig / TelegramApiClient / TelegramWebhookController / TelegramWebhookRegistrar
    ├── DiscordConfig / DiscordApiClient / DiscordWebhookController
    ├── SlackConfig / SlackApiClient / SlackEventController
    ├── WhatsAppConfig / WhatsAppApiClient / WhatsAppWebhookController
    ├── MessengerConfig / MessengerApiClient / MessengerWebhookController
    ├── LineConfig / LineApiClient / LineWebhookController
    ├── TeamsConfig / TeamsApiClient / TeamsWebhookController
    ├── WeChatConfig / WeChatApiClient / WeChatWebhookController
    └── SignalConfig / SignalApiClient / SignalWebhookController

src/main/resources/
├── application.properties               # All configuration
└── static/
    ├── index.html                       # Root UI
    ├── css/style.css                    # Dark theme, animations
    └── js/app.js                        # Frontend logic
```

---

## Core Components

### FloatingAppLauncher

JavaFX `Application` that creates a transparent always-on-top window containing a WebView.

- **Startup:** Launches Spring Boot on a background thread, waits for readiness, then creates the JavaFX stage
- **Window:** `StageStyle.TRANSPARENT`, no title bar, configurable dimensions
- **Scene event filters:** Intercept ALL mouse events before the WebView receives them
  - Ball area (0–45px top-left): Java handles drag, single-click-to-expand, double-click-to-toggle
  - Outside ball area: Forwarded to HTML via `engine.executeScript("document.elementFromPoint(x,y).click()")`
  - All events consumed to prevent Windows "Copy" taskbar ghosts from native drag
- **WebView transparency:** Reflection hack — accesses WebEngine's private `page` field, calls `setBackgroundColor(0)` for fully transparent ARGB
- **JVM args required:** `--add-opens javafx.web/javafx.scene.web=ALL-UNNAMED --add-opens javafx.web/com.sun.webkit=ALL-UNNAMED`

### WindowBridge (JS ↔ Java)

Injected into the WebView as `window.java`. Exposes to JavaScript:

| Method | Description |
|--------|-------------|
| `expand()` / `collapse()` | Resize stage |
| `isExpanded()` | Current state |
| `getX()` / `getY()` | Window position |
| `setPosition(x, y)` | Move window |
| `isNativeVoiceAvailable()` | Check microphone support |
| `startNativeVoice()` | Begin 8-second capture |
| `stopNativeVoice()` | Cancel capture |
| `isNativeVoiceListening()` | Capture in progress? |
| `consumeNativeVoiceTranscript()` | One-shot transcript result |
| `consumeNativeVoiceError()` | One-shot error message |
| `shutdownNativeVoice()` | Cleanup on exit |

### NativeVoiceService

Captures microphone audio on Windows, sends to OpenAI for transcription.

- **Format:** 16 kHz, 16-bit, mono WAV, 8-second max
- **Flow:** `TargetDataLine` → raw PCM → WAV container → `ChatService.getReplyFromAudio()` → `__AUDIO_RESULT__{"transcript":"...", "reply":"..."}`
- **Thread safety:** Locks + volatile fields + single-threaded daemon executor

### ChatService

Central reply logic used by the desktop UI and all 9 platform integrations.

**Reply tiers:**

1. **Spring AI tool-calling** (when `ChatClient` is available):
   - Builds system message via `SystemContextProvider`
   - Passes user message + 6 tool objects to `chatClient.prompt().tools(...).call()`
   - 25 @Tool methods available to the AI
   - Supports async callbacks for long-running file operations

2. **Regex fallback** (via `PcAgentService.tryExecute()`):
   - Pattern-matching for ~20 command types
   - Works completely offline without an API key

3. **No-AI message** (if `chatClient == null` and no regex match):
   - Returns "AI is not connected" guidance

**Audio pipeline:** WAV bytes → `transcribeAudio()` via OpenAI Whisper API → `getReply(transcript)`

**Async operations:** Background task results queued to `ConcurrentLinkedQueue<String>`, polled by frontend via `/api/chat/async`.

**Tool status:** `ToolExecutionNotifier` queue polled by frontend via `/api/chat/status` every 500ms during active requests.

### TranscriptService

Persistent conversation history with in-memory search.

- **Daily files:** `~/botsfer_data/botsfer_history/chat_history_yyyyMMdd.dat`
- **Format:** `[yyyy-MM-dd HH:mm:ss] SPEAKER: message text`
- **Speakers:** `USER`, `USER(voice)`, `BOT`, `BOT(error)`, `BOT(agent)`
- **Ring buffer:** Last 100 messages in memory for fast search
- **Search:** Case-insensitive substring matching (both in-memory and file-based)

### ScreenshotService

Periodic desktop screenshot capture with auto-cleanup.

- **Storage:** `~/botsfer_data/screenshots/yyyy-MM-dd_HH-mm-ss.png`
- **Schedule:** Configurable interval (default 5 seconds)
- **Cleanup:** Deletes files older than `max-age-days` (default 3 days), runs daily

---

## Spring AI Tool System

Spring AI auto-generates tool schemas from `@Tool` annotations and handles the full tool-calling protocol with the LLM. Tool classes are thin wrappers around core services, keeping business logic AI-agnostic.

### SystemTools (8 tools)

| Tool | Description | Delegates to |
|------|-------------|--------------|
| `closeAllWindows()` | Kill non-protected user processes | SystemControlService |
| `closeApp(appName)` | Kill specific app by name | SystemControlService |
| `openApp(appName)` | Launch application | SystemControlService |
| `minimizeAll()` | Show desktop (Win+D) | SystemControlService |
| `lockScreen()` | Lock workstation | SystemControlService |
| `takeScreenshot()` | Capture screen immediately | SystemControlService |
| `listRunningApps()` | List running processes | SystemControlService |
| `runPowerShell(command)` | Execute PowerShell, return output | SystemControlService |
| `runCmd(command)` | Execute CMD, return output | SystemControlService |

### BrowserTools (5 tools)

| Tool | Description | Delegates to |
|------|-------------|--------------|
| `openUrl(url)` | Open in default browser | BrowserControlService |
| `searchGoogle(query)` | Google search | BrowserControlService |
| `searchYouTube(query)` | YouTube search | BrowserControlService |
| `closeAllBrowsers()` | Kill all browser processes | BrowserControlService |
| `listBrowserTabs()` | List open browser windows | BrowserControlService |

### FileTools (4 tools, 2 async)

| Tool | Description | Async? |
|------|-------------|--------|
| `collectFiles(category)` | Scan all drives, copy matching files | Yes — returns acknowledgment, result via callback |
| `searchFiles(pattern)` | Glob pattern file search | Yes — returns acknowledgment, result via callback |
| `listCollected()` | Show collected files grouped by category | No |
| `openCollectedFolder()` | Open collected folder in explorer | No |

**File categories:** photos, videos, music, documents, archives (with full extension lists).

**Collection target:** `~/botsfer_data/collected/<category>/`

### FileSystemTools (4 tools)

| Tool | Description |
|------|-------------|
| `openPath(path)` | Open file/folder in explorer |
| `copyFile(source, dest)` | Copy a file |
| `deleteFile(path)` | Delete a file (not directories) |
| `countDirectoryContents(path)` | Count files and directories |

### ChatHistoryTool (3 tools)

| Tool | Description |
|------|-------------|
| `recallRecentConversation(query)` | Search in-memory buffer (last 100 messages) |
| `searchPastConversations(query)` | Search historical daily .dat files |
| `getFullRecentHistory()` | Return full in-memory buffer |

### TaskStatusTool (1 tool)

| Tool | Description |
|------|-------------|
| `taskStatus()` | Show status of background file tasks |

### ToolExecutionNotifier

Shared `@Component` with a `ConcurrentLinkedQueue<String>` for cross-cutting tool status messages. Every `@Tool` method calls `notifier.notify("Doing X...")` before execution. Frontend polls `/api/chat/status` to display these in real-time.

---

## Core Services

### SystemControlService

Windows system operations via `ProcessBuilder` and `Runtime.exec()`.

- **Process control:** Close all (with protected process whitelist), close by name, open app
- **Protected processes:** `java.exe`, `explorer.exe`, `csrss.exe`, `svchost.exe`, `winlogon.exe`, `lsass.exe`, `dwm.exe`, `MsMpEng.exe`, and others
- **App name mapping:** 60+ apps mapped to executable names (chrome → chrome.exe, word → WINWORD.EXE, etc.)
- **Shell execution:** PowerShell and CMD with 2000-char output limit
- **Desktop operations:** Minimize all (Win+D via PowerShell SendKeys), lock screen (rundll32 user32.dll)
- **Screenshot:** `java.awt.Robot` screen capture to PNG

### BrowserControlService

Browser automation via system commands.

- **Open URL:** `Desktop.getDesktop().browse()` or browser-specific process launch
- **Search:** Constructs Google/YouTube URLs with encoded queries
- **Tab listing:** PowerShell `Get-Process` with MainWindowTitle parsing
- **Close:** `taskkill /IM` for chrome, firefox, msedge, brave, opera

### FileCollectorService

Scans the entire PC for files matching a category, copies them to a centralized folder.

- **Categories:** photos (.jpg, .png, .gif, .heic, .raw, etc.), videos (.mp4, .mkv, .mov, etc.), music (.mp3, .flac, .ogg, etc.), documents (.pdf, .docx, .xlsx, etc.), archives (.zip, .rar, .7z, etc.)
- **Scan roots:** User home + all filesystem roots (depth limit 30)
- **Skip directories:** Windows system dirs, `node_modules`, `.git`, `.gradle`, `target`, hidden dirs
- **Deduplication:** Flattened path naming with counter on collision
- **Output:** `~/botsfer_data/collected/<category>/`

### PcAgentService (Regex Fallback)

Offline command interpreter using regex patterns. Handles ~20 command types:

- File operations: collect, list collected, search, open path
- System control: close all/specific, minimize, lock, screenshot, list running
- Browser: Google search, YouTube search, open URL, list tabs
- App management: open/launch specific apps
- Shell: PowerShell and CMD execution
- File management: copy, delete

Long-running commands execute on a background `ExecutorService` with async callback.

---

## REST API

### Chat Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/chat` | Send message, get reply. Body: `{"message": "..."}` Response: `{"reply": "..."}` |
| GET | `/api/chat/async` | Poll for background task results. Response: `{"hasResult": bool, "reply": "..."}` |
| GET | `/api/chat/status` | Poll for tool execution status. Response: `{"messages": ["...", "..."]}` |

### Platform Webhooks

| Platform | Endpoint |
|----------|----------|
| Viber | `POST /api/viber/webhook` |
| Telegram | `POST /api/telegram/webhook` |
| Discord | `POST /api/discord/interactions` |
| Slack | `POST /api/slack/events` |
| WhatsApp | `POST /api/whatsapp/webhook` |
| Messenger | `POST /api/messenger/webhook` |
| LINE | `POST /api/line/webhook` |
| Teams | `POST /api/teams/messages` |
| WeChat | `POST /api/wechat/webhook` |
| Signal | `POST /api/signal/webhook` |

### Skills

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/skills/diskscan/roots` | List filesystem roots with space info |
| GET | `/api/skills/diskscan/browse?path=...` | Browse directory contents |
| GET | `/api/skills/diskscan/info?path=...` | Get file/directory info |
| GET | `/api/skills/diskscan/search?basePath=...&pattern=...` | Glob pattern search |

---

## Platform Integrations

All 9 platforms follow the same pattern:

```
*Config.java           → @ConfigurationProperties, enabled=false by default
*ApiClient.java        → HTTP client (RestTemplate) for platform API
*WebhookController.java → Receives callbacks, calls ChatService.getReply()
*WebhookRegistrar.java  → (optional) Auto-registers webhook URL on startup
```

| Platform | Auth mechanism | API |
|----------|---------------|-----|
| Viber | X-Viber-Auth-Token header | chatapi.viber.com/pa |
| Telegram | Bot token in URL path | api.telegram.org |
| Discord | Bot token + public key signature verification | discord.com/api |
| Slack | Bot token + signing secret | slack.com/api |
| WhatsApp | Bearer access token (Meta Cloud API) | graph.facebook.com |
| Messenger | Page access token + app secret | graph.facebook.com |
| LINE | Channel access token + channel secret | api.line.me |
| Teams | App ID + password (Bot Framework) | botframework.com |
| WeChat | App ID + secret + AES key | api.weixin.qq.com |
| Signal | Local signal-cli-rest-api instance | localhost (configurable) |

All platforms use `ChatService.getReply()` for response generation. No platform-specific business logic exists in webhook controllers.

---

## Frontend

### UI Structure

- **Ball:** 34x34px gradient circle with 3-second swirl animation, top-left corner
- **Chat panel:** Dark theme, slides in on expand with opacity transition
  - Header: "Botsfer" title + close (collapse) and clear buttons
  - Messages log: Scrollable, user messages right-aligned (blue), bot messages left-aligned (dark)
  - Input row: Text input + voice button + send button
  - Voice status indicator (red pulse when listening)

### Interactions

- **Ball click:** Expand panel
- **Ball double-click:** Toggle expand/collapse
- **Ball drag:** Move window (via Java event filter)
- **Enter key:** Send message
- **Voice button:** Toggle native voice capture (8-sec recording → transcription → reply)

### Polling

| Endpoint | Interval | Purpose |
|----------|----------|---------|
| `/api/chat/status` | 500ms (during active request) | Tool execution status updates |
| `/api/chat/async` | 2000ms (always when expanded) | Background task results |
| Native voice state | 180ms (while listening) | Transcript/error/listening state |

### Theme

Dark mode with zinc/slate colors. Custom thin scrollbar. Message animations (fade + slide). Thinking dots animation while waiting for reply.

---

## Data Storage

All file-based, no database.

| Data | Location | Format |
|------|----------|--------|
| Chat history | `~/botsfer_data/botsfer_history/chat_history_yyyyMMdd.dat` | `[timestamp] SPEAKER: text` |
| Screenshots | `~/botsfer_data/screenshots/yyyy-MM-dd_HH-mm-ss.png` | PNG images |
| Collected files | `~/botsfer_data/collected/<category>/` | Original files (copied) |
| Key-value memory | `./memory/<key>` | Plain text files |

---

## Configuration Reference

### application.properties

```properties
# Server
server.port=${BOTSFER_PORT:8765}

# Window dimensions
app.window.collapsed.width=45          # Ball size
app.window.collapsed.height=45
app.window.expanded.width=380          # Chat panel size
app.window.expanded.height=520
app.window.initial.x=-1               # -1 = center screen
app.window.initial.y=-1
app.window.always-on-top=true
app.window.hover.expand.delay-ms=150
app.window.hover.collapse.delay-ms=400

# Spring AI (requires API key in application-secrets.properties)
spring.ai.openai.base-url=https://api.openai.com
spring.ai.openai.chat.options.model=gpt-4o-mini

# Audio transcription
app.openai.api-key=${spring.ai.openai.api-key:}
app.openai.transcription-model=gpt-4o-mini-transcribe

# Screenshots
app.screenshot.enabled=true
app.screenshot.interval-seconds=5
app.screenshot.max-age-days=3

# Memory
app.memory.enabled=true
app.memory.base-path=memory

# Skills
app.skills.diskscan.enabled=false
app.skills.diskscan.max-depth=20
app.skills.diskscan.max-results=500

# Platform integrations (all disabled by default)
app.viber.enabled=false
app.telegram.enabled=false
app.discord.enabled=false
app.slack.enabled=false
app.whatsapp.enabled=false
app.messenger.enabled=false
app.line.enabled=false
app.teams.enabled=false
app.wechat.enabled=false
app.signal.enabled=false
```

### application-secrets.properties (gitignored)

```properties
spring.ai.openai.api-key=sk-...
# Platform tokens as needed
```

---

## Security

| Area | Protection |
|------|-----------|
| Memory service | Key validation regex + path traversal prevention |
| Disk scan | Blocklist for system directories (System32, $Recycle.Bin, /proc, /sys) |
| File collector | Skips protected/system/hidden directories |
| Process control | Protected process whitelist (java.exe, explorer.exe, system services) |
| Shell execution | 2000-char output limit on PowerShell/CMD |
| Audio capture | 8-second time limit |
| File deletion | Files only (not directories) |
| API keys | Stored in gitignored secrets file, never in main properties |

---

## Build & Dependencies

### pom.xml

- **Parent:** `spring-boot-starter-parent:3.5.3`
- **Spring AI:** `spring-ai-starter-model-openai:1.0.1`
- **JavaFX:** `javafx-controls`, `javafx-web`, `javafx-fxml` — all `21.0.1`
- **Platform profiles:** Windows/Mac/Linux — unpack JavaFX natives at build time

### JVM Arguments

```
-Djava.library.path=${project.basedir}/target/javafx-natives
--add-opens javafx.web/javafx.scene.web=ALL-UNNAMED
--add-opens javafx.web/com.sun.webkit=ALL-UNNAMED
```

### Build Commands

```bash
mvn clean package -DskipTests    # Full build with JavaFX natives
mvn spring-boot:run              # Development run
mvn compiler:compile             # Compile only (when jfxwebkit.dll locked)
```

---

## Adding New Components

### New Messaging Platform

1. Create `*Config.java` with `@ConfigurationProperties(prefix="app.<name>")`, `enabled=false`
2. Create `*ApiClient.java` with RestTemplate HTTP client
3. Create `*WebhookController.java` at `POST /api/<name>/webhook`
4. Inject `ChatService`, call `getReply()` for responses
5. Add `app.<name>.*` properties to `application.properties`

### New AI Tool

1. Create `@Component` class in `com.botsfer.agent.tools`
2. Annotate methods with `@Tool(description="...")` and parameters with `@ToolParam`
3. Inject `ToolExecutionNotifier` and call `notifier.notify()` before execution
4. Add the tool bean to `ChatService` constructor and `.tools(...)` call

### New Skill

1. Create package `com.botsfer.skills.<name>/`
2. Add `*Config.java` with `enabled=false`, `*Service.java`, `*Controller.java`
3. Endpoints under `/api/skills/<name>/*`
4. Add `app.skills.<name>.*` properties to `application.properties`
