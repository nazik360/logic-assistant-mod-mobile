/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.Core;
import arc.scene.ui.Dialog;
import mindustry.Vars;

/**
 * Adds the "AI" button to the Logic Processor editor (mindustry.logic.LogicDialog).
 *
 * <p>In v159.7, {@code Vars.ui.logic} (a {@link mindustry.logic.LogicDialog}) rebuilds its bottom
 * button bar inside {@code setup()}, which clears {@code buttons} and re-adds the default buttons
 * every time the dialog is shown or the screen orientation changes. To survive that rebuild, the
 * assistant button is (re)added through the dialog's {@code shown} and {@code resized} listeners,
 * guarding against duplicates by name.
 */
public final class LogicEditorIntegration {

  public static final String AI_BUTTON_NAME = "logic-assistant-ai-button";

  public static final String AI_BUTTON_TEXT = "AI Logic";

  private LogicEditorIntegration() {}

  /** Called from {@link LogicAssistantMod#init()}. */
  public static void init() {
    // UI is not created on headless servers.
    if (Vars.ui == null) {
      return;
    }

    Dialog logic = Vars.ui.logic;

    logic.shown(LogicEditorIntegration::addAssistantButton);

    logic.resized(
        () -> {
          if (logic.isShown()) {
            Core.app.post(LogicEditorIntegration::addAssistantButton);
          }
        });
  }

  private static void addAssistantButton() {
    if (Vars.ui == null) {
      return;
    }

    Dialog logic = Vars.ui.logic;
    if (logic.buttons.find(AI_BUTTON_NAME) != null) {
      return;
    }

    logic.buttons.button(AI_BUTTON_TEXT, LogicAssistantUI::show).name(AI_BUTTON_NAME);
  }
}
