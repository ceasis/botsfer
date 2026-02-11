package com.botsfer;

import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Exposed to the WebView JavaScript as window.java for expand/collapse.
 */
public class WindowBridge {

    private final Stage stage;
    private final int collapsedWidth;
    private final int collapsedHeight;
    private final int expandedWidth;
    private final int expandedHeight;
    private volatile boolean expanded;

    public WindowBridge(Stage stage, int collapsedWidth, int collapsedHeight,
                        int expandedWidth, int expandedHeight) {
        this.stage = stage;
        this.collapsedWidth = collapsedWidth;
        this.collapsedHeight = collapsedHeight;
        this.expandedWidth = expandedWidth;
        this.expandedHeight = expandedHeight;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void expand() {
        expanded = true;
        Platform.runLater(() -> {
            stage.setWidth(expandedWidth);
            stage.setHeight(expandedHeight);
            stage.toFront();
            stage.requestFocus();
        });
    }

    public void collapse() {
        expanded = false;
        Platform.runLater(() -> {
            stage.setWidth(collapsedWidth);
            stage.setHeight(collapsedHeight);
        });
    }

    /** Current window X (for drag). */
    public double getX() {
        return stage.getX();
    }

    /** Current window Y (for drag). */
    public double getY() {
        return stage.getY();
    }

    /** Move window (call from JS when dragging the ball). */
    public void setPosition(double x, double y) {
        Platform.runLater(() -> {
            stage.setX(x);
            stage.setY(y);
        });
    }
}
