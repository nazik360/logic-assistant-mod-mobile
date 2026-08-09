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
 * Shared OpenAI-compatible chat completion flow used by the Groq and LM Studio providers: builds
 * the request JSON, performs the HTTPS call through arc's {@link Http} client and parses the
 * standard {@code choices[0].message.content} response.
 */
final class OpenAiChat {

  private OpenAiChat() {}

  /** Builds an OpenAI-compatible request body for the given system/user text and model. */
  static String buildChat(String system, String user, String model) {
    return "{\"model\":\""
        + LogicCodeGenerator.escapeJson(model)
        + "\",\"max_tokens\":"
        + LogicCodeGenerator.MAX_TOKENS
        + ",\"messages\":[{\"role\":\"system\",\"content\":\""
        + LogicCodeGenerator.escapeJson(system)
        + "\"},{\"role\":\"user\",\"content\":\""
        + LogicCodeGenerator.escapeJson(user)
        + "\"}]}";
  }

  /** Parses an OpenAI-compatible response ({@code choices[0].message.content}). */
  static String extractText(String json) {
    if (json == null || json.trim().isEmpty()) return "";
    try {
      Jval root = Jval.read(json);
      Jval choice = root.get("choices").asArray().first();
      if (choice == null) return "";
      if (choice.get("message") == null) return "";
      Jval message = choice.get("message");
      Jval content = message.get("content");
      if (content == null) return "";
      return content.asString();
    } catch (Throwable t) {
      Log.err("[Logic Assistant] Failed to parse AI response.", t);
      return "";
    }
  }

  /**
   * Sends one OpenAI-compatible chat completion call and reports the raw text or a readable error.
   */
  static void chat(
      String system,
      String user,
      String apiKey,
      String url,
      String model,
      String providerName,
      Cons<GenerationResult> cb) {
    String body = buildChat(system, user, model);
    Http.HttpRequest request = Http.post(url, body);
    if (apiKey != null && !apiKey.isEmpty()) {
      request.header("authorization", "Bearer " + apiKey);
    }
    request
        .header("content-type", "application/json")
        .timeout(LogicCodeGenerator.TIMEOUT)
        .error(
            err -> {
              String message = LogicCodeGenerator.readableError(err);
              Log.warn("[Logic Assistant] " + providerName + " generation failed: @", message);
              cb.get(GenerationResult.failure(message, LogicCodeGenerator.httpStatus(err)));
            })
        .submit(
            response ->
                cb.get(GenerationResult.success(extractText(response.getResultAsString()))));
  }
}
