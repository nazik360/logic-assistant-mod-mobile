/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.func.Cons;

/** Groq provider: OpenAI-compatible chat completions through the official Groq endpoint. */
final class GroqProvider implements AiProvider {

  @Override
  public void chat(String system, String user, Cons<GenerationResult> result) {
    OpenAiChat.chat(
        system,
        user,
        LogicAssistantConfig.getGroqApiKey(),
        LogicAssistantConfig.GROQ_API_URL,
        LogicAssistantConfig.GROQ_MODEL,
        "Groq",
        result);
  }
}
