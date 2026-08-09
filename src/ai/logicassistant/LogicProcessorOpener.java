/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.Core;
import arc.scene.event.VisibilityListener;
import mindustry.Vars;
import mindustry.logic.LogicDialog;

/**
 * Opens the in-game Logic Processor editor with the given mlog code.
 *
 * <p>In v159.7 the editor is {@code Vars.ui.logic} (a {@link mindustry.logic.LogicDialog}) which
 * can be opened programmatically via {@code show(code, executor, privileged, modified)}. The {@code
 * modified} callback only fires when the code was actually edited (or the processor was restarted),
 * so it cannot be used for navigation. Instead this class listens to the editor's {@code hidden}
 * event (once) to run {@code onClosed} whenever the editor closes, returning the player to the AI
 * Logic window.
 */
public final class LogicProcessorOpener {

  private LogicProcessorOpener() {}

  /**
   * Opens the Logic Processor editor with {@code code}.
   *
   * @param onClosed run (on the main thread) exactly once when the editor is closed
   */
  public static void open(String code, Runnable onClosed) {
    if (Vars.ui == null) return;

    LogicDialog logic = Vars.ui.logic;

    if (onClosed != null) {
      logic.addListener(
          new VisibilityListener() {
            private boolean ran;

            @Override
            public boolean hidden() {
              // Run only once, even if the editor is hidden again later.
              if (ran) return false;
              ran = true;
              Core.app.post(onClosed);
              return false;
            }
          });
    }

    logic.show(code, null, false, result -> {});
  }
}
