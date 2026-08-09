/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.func.Cons;

/**
 * Common interface implemented by every AI code-generation backend: LM Studio, Groq, Claude and
 * Gemini. Each provider turns a system instruction plus a user prompt into one chat-completion
 * call; the result is reported through the callback (on a background thread; wrap UI updates in
 * {@code Core.app.post}).
 */
public interface AiProvider {

  /**
   * Sends one chat completion request. The callback receives either the raw assistant text (as a
   * success result) or a readable error message.
   */
  void chat(String system, String user, Cons<GenerationResult> result);
}
