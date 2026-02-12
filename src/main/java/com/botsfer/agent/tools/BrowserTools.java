package com.botsfer.agent.tools;

import com.botsfer.agent.BrowserControlService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class BrowserTools {

    private final BrowserControlService browserControl;
    private final ToolExecutionNotifier notifier;

    public BrowserTools(BrowserControlService browserControl, ToolExecutionNotifier notifier) {
        this.browserControl = browserControl;
        this.notifier = notifier;
    }

    @Tool(description = "Open a URL in the default web browser")
    public String openUrl(
            @ToolParam(description = "The URL to open, e.g. 'google.com' or 'https://example.com'") String url) {
        notifier.notify("Opening " + url + "...");
        return browserControl.openInBrowser("default", url);
    }

    @Tool(description = "Search Google for a query and open results in the browser")
    public String searchGoogle(
            @ToolParam(description = "The search query") String query) {
        notifier.notify("Searching Google: " + query);
        return browserControl.searchGoogle(query);
    }

    @Tool(description = "Search YouTube for videos and open results in the browser")
    public String searchYouTube(
            @ToolParam(description = "The YouTube search query") String query) {
        notifier.notify("Searching YouTube: " + query);
        return browserControl.searchYouTube(query);
    }

    @Tool(description = "Close all open web browser windows (Chrome, Firefox, Edge, Brave, Opera)")
    public String closeAllBrowsers() {
        notifier.notify("Closing all browsers...");
        return browserControl.closeAllBrowsers();
    }

    @Tool(description = "List currently open browser windows with their titles")
    public String listBrowserTabs() {
        notifier.notify("Listing browser tabs...");
        return browserControl.listBrowserTabs();
    }
}
