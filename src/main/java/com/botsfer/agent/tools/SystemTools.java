package com.botsfer.agent.tools;

import com.botsfer.agent.SystemControlService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SystemTools {

    private final SystemControlService systemControl;
    private final ToolExecutionNotifier notifier;

    public SystemTools(SystemControlService systemControl, ToolExecutionNotifier notifier) {
        this.systemControl = systemControl;
        this.notifier = notifier;
    }

    @Tool(description = "Close all running user application windows on the PC, except system processes and Botsfer itself")
    public String closeAllWindows() {
        notifier.notify("Closing all windows...");
        return systemControl.closeAllWindows();
    }

    @Tool(description = "Close a specific running application by its common name")
    public String closeApp(
            @ToolParam(description = "Name of the application, e.g. 'chrome', 'notepad', 'spotify'") String appName) {
        notifier.notify("Closing " + appName + "...");
        return systemControl.closeApp(appName);
    }

    @Tool(description = "Launch or open an application by name")
    public String openApp(
            @ToolParam(description = "Name of the application to open, e.g. 'chrome', 'calculator', 'terminal'") String appName) {
        notifier.notify("Opening " + appName + "...");
        return systemControl.openApp(appName);
    }

    @Tool(description = "Minimize all windows and show the desktop")
    public String minimizeAll() {
        notifier.notify("Minimizing all windows...");
        return systemControl.minimizeAll();
    }

    @Tool(description = "Lock the computer screen")
    public String lockScreen() {
        notifier.notify("Locking screen...");
        return systemControl.lockScreen();
    }

    @Tool(description = "Take a screenshot of the entire screen right now and save it")
    public String takeScreenshot() {
        notifier.notify("Taking screenshot...");
        return systemControl.takeScreenshot();
    }

    @Tool(description = "List all currently running user applications and their process counts")
    public String listRunningApps() {
        notifier.notify("Listing running apps...");
        return systemControl.listRunningApps();
    }

    @Tool(description = "Execute a PowerShell command and return its output. Use for system queries like disk space, RAM, installed programs, battery status, etc.")
    public String runPowerShell(
            @ToolParam(description = "The PowerShell command to execute") String command) {
        notifier.notify("Running PowerShell: " + command);
        return systemControl.runPowerShell(command);
    }

    @Tool(description = "Execute a CMD command and return its output. Use for commands like ipconfig, ping, dir, systeminfo, netstat, etc.")
    public String runCmd(
            @ToolParam(description = "The CMD command to execute") String command) {
        notifier.notify("Running CMD: " + command);
        return systemControl.runCmd(command);
    }
}
