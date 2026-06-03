package de.legoshi.parkourcalc.core.ui;

import imgui.ImGuiIO;

/** An overlay that can keep a display-only subset of itself visible while the main UI is closed. */
public interface DetachedOverlay {
    void renderDetached(ImGuiIO io);
}
