/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.func.Cons;

/** Local LM Studio provider: OpenAI-compatible chat completions at a local URL with no API key. */
final class LocalProvider implements AiProvider {

  @Override
  public void chat(String system, String user, Cons<GenerationResult> result) {
    String url = LogicAssistantConfig.getLocalUrl();
    String model = LogicAssistantConfig.getLocalModel();

    if (url == null || url.trim().isEmpty()) {
      result.get(
          GenerationResult.failure(
              "Не задан URL для LM Studio. Укажите локальный сервер в настройках."));
      return;
    }
    if (model == null || model.trim().isEmpty()) {
      result.get(
          GenerationResult.failure("Не задана модель для LM Studio. Укажите модель в настройках."));
      return;
    }

    OpenAiChat.chat(system, user, null, url, model, "LM Studio", result);
  }
}
