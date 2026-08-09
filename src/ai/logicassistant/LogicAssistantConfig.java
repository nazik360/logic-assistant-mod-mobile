/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.Core;

public final class LogicAssistantConfig {

  /** Provider identifiers persisted in settings. */
  public static final String PROVIDER_LOCAL = "local";

  public static final String PROVIDER_GROQ = "groq";

  public static final String PROVIDER_ANTHROPIC = "anthropic";

  public static final String PROVIDER_GEMINI = "gemini";

  public static final String PROVIDER_GROK = "grok";

  /** Official Groq OpenAI-compatible chat completions endpoint. */
  public static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

  /** Groq model recommended for complex coding tasks. */
  public static final String GROQ_MODEL = "openai/gpt-oss-120b";

  /** Official Anthropic Messages API endpoint. */
  public static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";

  /** Anthropic API version header value. */
  public static final String ANTHROPIC_API_VERSION = "2023-06-01";

  /** Anthropic model recommended for complex coding tasks. */
  public static final String ANTHROPIC_MODEL = "claude-sonnet-4-6";

  /**
   * Google Gemini generateContent REST endpoint prefix (model is appended before {@code
   * :generateContent}).
   */
  public static final String GEMINI_API_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/";

  /** Current stable Gemini Flash model, compatible with the generateContent API. */
  public static final String GEMINI_DEFAULT_MODEL = "gemini-3.6-flash";

  /** Official xAI Responses API endpoint. */
  public static final String GROK_API_URL = "https://api.x.ai/v1/responses";

  /** xAI model recommended for coding tasks. */
  public static final String GROK_DEFAULT_MODEL = "grok-4.5";

  public static final String DEFAULT_LOCAL_URL = "http://localhost:1234/v1/chat/completions";

  private static final String PROVIDER_SETTING = "logic-assistant-provider";
  private static final String GROQ_KEY_SETTING = "logic-assistant-groq-key";
  private static final String ANTHROPIC_KEY_SETTING = "logic-assistant-anthropic-key";
  private static final String GEMINI_KEY_SETTING = "logic-assistant-gemini-key";
  private static final String GEMINI_MODEL_SETTING = "logic-assistant-gemini-model";
  private static final String GROK_KEY_SETTING = "logic-assistant-grok-key";
  private static final String GROK_MODEL_SETTING = "logic-assistant-grok-model";
  private static final String LOCAL_URL_SETTING = "logic-assistant-local-url";
  private static final String LOCAL_MODEL_SETTING = "logic-assistant-local-model";

  /** Legacy key shared by the previous single-cloud-provider version; migrated to Groq. */
  private static final String LEGACY_KEY_SETTING = "logic-assistant-api-key";

  private LogicAssistantConfig() {}

  /** The active provider; unknown values fall back to the default {@value #PROVIDER_LOCAL}. */
  public static String getProvider() {
    String provider = Core.settings.getString(PROVIDER_SETTING, PROVIDER_LOCAL);
    if (PROVIDER_GROQ.equals(provider)
        || PROVIDER_ANTHROPIC.equals(provider)
        || PROVIDER_GEMINI.equals(provider)
        || PROVIDER_GROK.equals(provider)) {
      return provider;
    }
    return PROVIDER_LOCAL;
  }

  public static void setProvider(String provider) {
    if (!PROVIDER_GROQ.equals(provider)
        && !PROVIDER_ANTHROPIC.equals(provider)
        && !PROVIDER_GEMINI.equals(provider)
        && !PROVIDER_GROK.equals(provider)) {
      provider = PROVIDER_LOCAL;
    }
    Core.settings.put(PROVIDER_SETTING, provider);
  }

  public static boolean isLocal() {
    return PROVIDER_LOCAL.equals(getProvider());
  }

  public static boolean isGroq() {
    return PROVIDER_GROQ.equals(getProvider());
  }

  public static boolean isAnthropic() {
    return PROVIDER_ANTHROPIC.equals(getProvider());
  }

  public static boolean isGemini() {
    return PROVIDER_GEMINI.equals(getProvider());
  }

  public static boolean isGrok() {
    return PROVIDER_GROK.equals(getProvider());
  }

  /** Human-readable provider name for UI and error messages. */
  public static String providerDisplayName(String provider) {
    if (PROVIDER_GROQ.equals(provider)) return "Groq";
    if (PROVIDER_ANTHROPIC.equals(provider)) return "Claude";
    if (PROVIDER_GEMINI.equals(provider)) return "Gemini";
    if (PROVIDER_GROK.equals(provider)) return "Grok";
    return "LM Studio";
  }

  public static String getGroqApiKey() {
    String key = Core.settings.getString(GROQ_KEY_SETTING, "");
    if (key.isEmpty()) {
      // One-time migration from the legacy shared key (which was used by Groq).
      key = Core.settings.getString(LEGACY_KEY_SETTING, "");
      if (!key.isEmpty()) {
        Core.settings.put(GROQ_KEY_SETTING, key);
        Core.settings.put(LEGACY_KEY_SETTING, "");
      }
    }
    return key;
  }

  public static void setGroqApiKey(String apiKey) {
    Core.settings.put(GROQ_KEY_SETTING, apiKey == null ? "" : apiKey.trim());
  }

  public static boolean hasGroqApiKey() {
    return !getGroqApiKey().isEmpty();
  }

  public static String getAnthropicApiKey() {
    return Core.settings.getString(ANTHROPIC_KEY_SETTING, "");
  }

  public static void setAnthropicApiKey(String apiKey) {
    Core.settings.put(ANTHROPIC_KEY_SETTING, apiKey == null ? "" : apiKey.trim());
  }

  public static boolean hasAnthropicApiKey() {
    return !getAnthropicApiKey().isEmpty();
  }

  public static String getGeminiApiKey() {
    return Core.settings.getString(GEMINI_KEY_SETTING, "");
  }

  public static void setGeminiApiKey(String apiKey) {
    Core.settings.put(GEMINI_KEY_SETTING, apiKey == null ? "" : apiKey.trim());
  }

  public static boolean hasGeminiApiKey() {
    return !getGeminiApiKey().isEmpty();
  }

  public static String getGeminiModel() {
    return Core.settings.getString(GEMINI_MODEL_SETTING, GEMINI_DEFAULT_MODEL);
  }

  public static void setGeminiModel(String model) {
    Core.settings.put(GEMINI_MODEL_SETTING, model == null ? "" : model.trim());
  }

  public static String getGrokApiKey() {
    return Core.settings.getString(GROK_KEY_SETTING, "");
  }

  public static void setGrokApiKey(String apiKey) {
    Core.settings.put(GROK_KEY_SETTING, apiKey == null ? "" : apiKey.trim());
  }

  public static boolean hasGrokApiKey() {
    return !getGrokApiKey().isEmpty();
  }

  public static String getGrokModel() {
    return Core.settings.getString(GROK_MODEL_SETTING, GROK_DEFAULT_MODEL);
  }

  public static void setGrokModel(String model) {
    Core.settings.put(GROK_MODEL_SETTING, model == null ? "" : model.trim());
  }

  public static String getLocalUrl() {
    return Core.settings.getString(LOCAL_URL_SETTING, DEFAULT_LOCAL_URL);
  }

  public static void setLocalUrl(String localUrl) {
    Core.settings.put(LOCAL_URL_SETTING, localUrl == null ? "" : localUrl.trim());
  }

  public static String getLocalModel() {
    return Core.settings.getString(LOCAL_MODEL_SETTING, "");
  }

  public static void setLocalModel(String localModel) {
    Core.settings.put(LOCAL_MODEL_SETTING, localModel == null ? "" : localModel.trim());
  }
}
