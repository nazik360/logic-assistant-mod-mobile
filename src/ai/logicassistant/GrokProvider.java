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

/**
 * xAI Grok provider using the Responses API ({@code https://api.x.ai/v1/responses}).
 *
 * <p>Request: a flat {@code input} array of system/user messages. Response: the final text is read
 * from {@code output[].content[].text} parts of {@code message} items (reasoning items have no
 * {@code content} and are skipped); falls back to the top-level {@code output_text} convenience
 * field.
 */
final class GrokProvider implements AiProvider {

  /** Builds a Responses API request body: model plus a flat system/user message list. */
  static String buildChat(String system, String user, String model) {
    return "{\"model\":\""
        + LogicCodeGenerator.escapeJson(model)
        + "\",\"input\":[{\"role\":\"system\",\"content\":\""
        + LogicCodeGenerator.escapeJson(system)
        + "\"},{\"role\":\"user\",\"content\":\""
        + LogicCodeGenerator.escapeJson(user)
        + "\"}]}";
  }

  /**
   * Parses a Responses API response: concatenates {@code text} of every {@code output[].content[]}
   * part, ignoring reasoning items and other non-text parts, falling back to the top-level {@code
   * output_text} field. Returns "" for an empty or malformed response.
   */
  static String extractText(String json) {
    if (json == null || json.trim().isEmpty()) return "";
    try {
      Jval root = Jval.read(json);
      Jval output = root.get("output");
      if (output != null && output.isArray()) {
        StringBuilder text = new StringBuilder();
        for (Jval item : output.asArray()) {
          Jval content = item.get("content");
          if (content == null || !content.isArray()) continue;
          for (Jval part : content.asArray()) {
            Jval partText = part.get("text");
            if (partText != null) text.append(partText.asString());
          }
        }
        return text.toString();
      }
      Jval outputText = root.get("output_text");
      if (outputText != null) return outputText.asString();
      return "";
    } catch (Throwable t) {
      Log.err("[Logic Assistant] Failed to parse Grok response.", t);
      return "";
    }
  }

  @Override
  public void chat(String system, String user, Cons<GenerationResult> result) {
    String body = buildChat(system, user, LogicAssistantConfig.getGrokModel());
    Http.post(LogicAssistantConfig.GROK_API_URL, body)
        .header("authorization", "Bearer " + LogicAssistantConfig.getGrokApiKey())
        .header("content-type", "application/json")
        .timeout(LogicCodeGenerator.TIMEOUT)
        .error(
            err -> {
              String message = LogicCodeGenerator.readableError(err);
              Log.warn("[Logic Assistant] Grok generation failed: @", message);
              result.get(GenerationResult.failure(message, LogicCodeGenerator.httpStatus(err)));
            })
        .submit(
            response ->
                result.get(GenerationResult.success(extractText(response.getResultAsString()))));
  }
}
