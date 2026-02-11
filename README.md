# Botsfer

A **Java 17** **Spring Boot** desktop app that runs as a **floating UI** on Windows or Mac. It shows a swirling ball that expands into a **chatbot interface with voice** when you hover over it, and collapses back to the ball when the mouse leaves.

All configuration is in `application.properties`.

## Features

- **Floating window**: Always-on-top, draggable, no title bar.
- **Collapsed state**: A single swirling animated ball (gradient orb).
- **Expanded state** (on mouse over): Full chat UI with message history, text input, and send button.
- **Voice input**: Microphone button uses the Web Speech API (browser/WebView) for speech-to-text.
- **REST chat API**: `POST /api/chat` with `{"message":"..."}`; reply comes from the backend (placeholder logic by default; you can plug in your own).
- **Viber Bot** (optional): Receive messages from Viber and reply with the same chat logic. Configure in `application.properties` and expose the webhook via HTTPS.

## Requirements

- **Java 17** (JDK 17 or later)
- **Maven 3.6+**
- Windows or macOS (Linux may work but is untested)

## Setup

### 1. Clone or open the project

```bash
cd botsfer
```

### 2. Build

```bash
mvn clean package -DskipTests
```

### 3. Run

**Option A – Maven (recommended)**

```bash
mvn spring-boot:run
```

On the first run, Maven unpacks the JavaFX Web native library (e.g. `jfxwebkit.dll` on Windows) into `target/javafx-natives` and passes it as `java.library.path` so the floating window’s WebView works. This is done automatically via the OS-specific profile (Windows/Mac/Linux).

If you see an error like *"JavaFX runtime components are missing"*, run with JavaFX modules and opens:

**Windows (PowerShell):**

```powershell
$env:MAVEN_OPTS="--add-modules javafx.controls,javafx.web,javafx.fxml --add-opens java.base/java.lang=ALL-UNNAMED"
mvn spring-boot:run
```

**macOS / Linux:**

```bash
export MAVEN_OPTS="--add-modules javafx.controls,javafx.web,javafx.fxml --add-opens java.base/java.lang=ALL-UNNAMED"
mvn spring-boot:run
```

**Option B – Run the JAR**

```bash
java --add-modules javafx.controls,javafx.web,javafx.fxml --add-opens java.base/java.lang=ALL-UNNAMED -jar target/botsfer-1.0.0-SNAPSHOT.jar
```

**Option C – From your IDE (Eclipse / IntelliJ)**

1. Set the **main class** to: `com.botsfer.FloatingAppLauncher`
2. Add VM options (if needed):
   - `--add-modules javafx.controls,javafx.web,javafx.fxml`
   - `--add-opens java.base/java.lang=ALL-UNNAMED`
3. Run `FloatingAppLauncher`.

### 4. Use the app

- A small **swirling ball** appears (usually bottom-right of the screen).
- **Hover** over the ball to expand the chat UI.
- **Type** a message and press Enter or click Send.
- Click the **microphone** to use voice input (if supported by the WebView).
- **Drag** the window by clicking and dragging anywhere on it.
- **Mouse out** of the window to collapse it back to the ball.

## Configuration (`application.properties`)

| Property | Description | Default |
|----------|-------------|---------|
| `server.port` | HTTP port for the embedded server and UI | `8765` (or `BOTSFER_PORT` env) |
| `app.window.collapsed.width` | Width when collapsed (ball only) | `64` |
| `app.window.collapsed.height` | Height when collapsed | `64` |
| `app.window.expanded.width` | Width when expanded (chat) | `380` |
| `app.window.expanded.height` | Height when expanded | `520` |
| `app.window.initial.x` | Initial X position (-1 = auto, right side) | `-1` |
| `app.window.initial.y` | Initial Y position (-1 = auto) | `-1` |
| `app.window.always-on-top` | Keep window on top | `true` |
| `app.window.hover.expand.delay-ms` | Delay before expanding on hover | `150` |
| `app.window.hover.collapse.delay-ms` | Delay before collapsing on mouse out | `400` |
| `app.chat.placeholder.enabled` | Use built-in placeholder replies | `true` |
| `app.viber.enabled` | Enable Viber bot (receive/send via webhook) | `false` |
| `app.viber.auth-token` | Viber bot auth token (app key from Viber) | (empty) |
| `app.viber.bot-name` | Name shown as sender in Viber | `Botsfer` |
| `app.viber.webhook-url` | Public HTTPS URL for webhook (e.g. ngrok). If set, registered on startup | (empty) |

Override with environment variables (e.g. `BOTSFER_PORT=9090`) or by editing `src/main/resources/application.properties`.

### Viber integration

1. Create a Viber bot (commercial terms may apply; see [Viber for developers](https://developers.viber.com/docs/api/rest-bot-api/)).
2. In Viber: **More → Settings → Bots → Edit Info → Your app key** — copy the token.
3. Set in `application.properties`:
   - `app.viber.enabled=true`
   - `app.viber.auth-token=YOUR_TOKEN`
4. Expose your app with **HTTPS** so Viber can call the webhook (they do not accept HTTP or self-signed certs):
   - **Local dev**: use [ngrok](https://ngrok.com/) or similar: `ngrok http 8765`, then set `app.viber.webhook-url=https://YOUR_NGROK_URL/api/viber/webhook`. Restart the app to register the webhook, or call Viber’s `set_webhook` API once with that URL.
   - **Production**: deploy behind HTTPS and set `app.viber.webhook-url=https://yourdomain.com/api/viber/webhook`.
5. Webhook endpoint: `POST /api/viber/webhook` — Viber sends callbacks here. The app replies to text messages using the same logic as the in-app chat (`ChatService`).

## Project layout

```
botsfer/
├── pom.xml
├── README.md
├── src/main/java/com/botsfer/
│   ├── BotsferApplication.java      # Spring Boot entry
│   ├── FloatingAppLauncher.java    # JavaFX entry, starts Spring & floating window
│   ├── WindowBridge.java           # JS ↔ Java (expand/collapse)
│   ├── ChatController.java         # REST /api/chat
│   ├── ChatService.java            # Shared reply logic (app + Viber)
│   ├── ViberConfig.java            # Viber properties
│   ├── ViberApiClient.java         # Viber REST API (set_webhook, send_message)
│   ├── ViberWebhookController.java # POST /api/viber/webhook
│   └── ViberWebhookRegistrar.java  # Optional set_webhook on startup
└── src/main/resources/
    ├── application.properties
    └── static/
        ├── index.html
        ├── css/style.css
        └── js/app.js
```

## Customizing the chatbot

- **Backend logic**: Edit `ChatService.java` — `getReply()` is used by both the in-app chat and the Viber bot. Replace or extend the placeholder logic (or call your own services).
- **UI**: Edit `src/main/resources/static/` (HTML, CSS, JS). The floating window is a JavaFX `WebView` loading the app at `http://localhost:<server.port>/`.
- **Voice**: Voice input uses the Web Speech API in the WebView; no backend change needed for basic speech-to-text.

## Troubleshooting

- **"no jfxwebkit in java.library.path"**: The build unpacks the WebView native library into `target/javafx-natives` when you run on Windows/Mac/Linux (OS-specific Maven profile). Use `mvn spring-boot:run` so the plugin can set `-Djava.library.path` to that folder. If you run the JAR directly, use:  
  `java -Djava.library.path=target/javafx-natives -jar target/botsfer-1.0.0-SNAPSHOT.jar` (and ensure you’ve run a build on the same OS first so `target/javafx-natives` exists).
- **Window doesn’t appear**: Ensure the port in `application.properties` is free and that no firewall is blocking `localhost`.
- **"JavaFX runtime components are missing"**: Use the `MAVEN_OPTS` or `java` command with `--add-modules` and `--add-opens` as above.
- **Voice not working**: Depends on WebView/system support for the Web Speech API (e.g. macOS/Windows with a recent JavaFX/WebKit build). Try in a browser at `http://localhost:<port>/` to compare.

## License

Use and modify as you like for your project.
