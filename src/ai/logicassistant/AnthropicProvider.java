/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.func.Cons;
import arc.util.Http;
import arc.util.Log;
import arc.util.serialization.Jval;

/** Claude provider using the official Anthropic Messages API. */
final class AnthropicProvider implements AiProvider {

  /** Builds an Anthropic Messages API request body. */
  static String buildChat(String system, String user) {
    return "{\"model\":\""
        + LogicCodeGenerator.escapeJson(LogicAssistantConfig.ANTHROPIC_MODEL)
        + "\",\"system\":\""
        + LogicCodeGenerator.escapeJson(system)
        + "\",\"max_tokens\":"
        + LogicCodeGenerator.MAX_TOKENS
        + ",\"messages\":[{\"role\":\"user\",\"content\":\""
        + LogicCodeGenerator.escapeJson(user)
        + "\"}]}";
  }

  /** Parses an Anthropic Messages API response ({@code content[0].text}). */
  static String extractText(String json) {
    if (json == null || json.trim().isEmpty()) return "";
    try {
      Jval root = Jval.read(json);
      Jval content = root.get("content");
      if (content == null || !content.isArray() || content.asArray().size == 0) return "";
      Jval first = content.asArray().first();
      if (first == null) return "";
      if (!"text".equals(first.get("type").asString())) return "";
      return first.get("text").asString();
    } catch (Throwable t) {
      Log.err("[Logic Assistant] Failed to parse AI response.", t);
      return "";
    }
  }

  @Override
  public void chat(String system, String user, Cons<GenerationResult> cb) {
    String body = buildChat(system, user);
    Http.post(LogicAssistantConfig.ANTHROPIC_API_URL, body)
        .header("x-api-key", LogicAssistantConfig.getAnthropicApiKey())
        .header("anthropic-version", LogicAssistantConfig.ANTHROPIC_API_VERSION)
        .header("content-type", "application/json")
        .timeout(LogicCodeGenerator.TIMEOUT)
        .error(
            err -> {
              String message = LogicCodeGenerator.readableError(err);
              Log.warn("[Logic Assistant] Claude generation failed: @", message);
              cb.get(GenerationResult.failure(message, LogicCodeGenerator.httpStatus(err)));
            })
        .submit(
            response ->
                cb.get(GenerationResult.success(extractText(response.getResultAsString()))));
  }
}
