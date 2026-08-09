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
 * Google Gemini provider using the official generateContent REST API. The system instruction is
 * passed through the dedicated {@code systemInstruction} field, and the API key travels in the
 * {@code x-goog-api-key} header.
 */
final class GeminiProvider implements AiProvider {

  /** Builds the generateContent URL for the given model. */
  static String generateUrl(String model) {
    return LogicAssistantConfig.GEMINI_API_URL + model + ":generateContent";
  }

  /**
   * Builds a Gemini generateContent request body with a system instruction and one user message.
   */
  static String buildChat(String system, String user) {
    return "{\"systemInstruction\":{\"parts\":[{\"text\":\""
        + LogicCodeGenerator.escapeJson(system)
        + "\"}]},\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\""
        + LogicCodeGenerator.escapeJson(user)
        + "\"}]}]}";
  }

  /**
   * Parses a Gemini generateContent response: concatenates {@code
   * candidates[0].content.parts[].text}. Returns "" for an empty, missing or malformed response.
   */
  static String extractText(String json) {
    if (json == null || json.trim().isEmpty()) return "";
    try {
      Jval root = Jval.read(json);
      Jval candidates = root.get("candidates");
      if (candidates == null || !candidates.isArray() || candidates.asArray().size == 0) return "";
      Jval candidate = candidates.asArray().first();
      if (candidate == null) return "";
      Jval content = candidate.get("content");
      if (content == null) return "";
      Jval parts = content.get("parts");
      if (parts == null || !parts.isArray() || parts.asArray().size == 0) return "";
      StringBuilder text = new StringBuilder();
      for (Jval part : parts.asArray()) {
        Jval partText = part.get("text");
        if (partText != null) text.append(partText.asString());
      }
      return text.toString();
    } catch (Throwable t) {
      Log.err("[Logic Assistant] Failed to parse Gemini response.", t);
      return "";
    }
  }

  @Override
  public void chat(String system, String user, Cons<GenerationResult> cb) {
    String model = LogicAssistantConfig.getGeminiModel();
    if (model == null || model.trim().isEmpty()) {
      cb.get(GenerationResult.failure("Не задана модель Gemini. Укажите модель в настройках."));
      return;
    }
    String body = buildChat(system, user);
    Http.post(generateUrl(model.trim()), body)
        .header("x-goog-api-key", LogicAssistantConfig.getGeminiApiKey())
        .header("content-type", "application/json")
        .timeout(LogicCodeGenerator.TIMEOUT)
        .error(
            err -> {
              String message = LogicCodeGenerator.readableError(err);
              Log.warn("[Logic Assistant] Gemini generation failed: @", message);
              cb.get(GenerationResult.failure(message, LogicCodeGenerator.httpStatus(err)));
            })
        .submit(
            response ->
                cb.get(GenerationResult.success(extractText(response.getResultAsString()))));
  }
}
