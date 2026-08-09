/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import mindustry.mod.Mod;

public class LogicAssistantMod extends Mod {

  @Override
  public void init() {
    LogicEditorIntegration.init();
  }
}
