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
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

/**
 * Requests Mindustry Logic code from an AI backend using Arc's built-in {@link Http} client, so no
 * extra dependencies are required.
 *
 * <p>Five providers are supported (LM Studio / Groq / Claude / Gemini / Grok), all behind the
 * common {@link AiProvider} interface. Generation is a pipeline:
 *
 * <ol>
 *   <li>analyze the request ({@link IntentAnalyzer});
 *   <li>retrieve only the relevant part of the verified knowledge base ({@link
 *       MindustryKnowledgeBase});
 *   <li>ask the model for a hidden plan (never shown to the user);
 *   <li>generate code, validate it with Mindustry's own parser ({@link LogicValidator});
 *   <li>on a validation error, send the validator's message back and let the model fix the code (up
 *       to two attempts; an empty answer is retried exactly once).
 * </ol>
 *
 * The callbacks are invoked on background threads, so UI updates must be wrapped in {@code
 * Core.app.post(...)}.
 */
public final class LogicCodeGenerator {

  static final int MAX_TOKENS = 4096;
  static final int TIMEOUT = 60000;

  /**
   * System prompt for the final code stage: strict rules only. The relevant part of the knowledge
   * base (syntax, instructions, verified examples) is appended to the user message per request.
   */
  static final String SYSTEM_PROMPT =
      """
      You are an expert Mindustry Logic (mlog) programmer. Your task is to SOLVE the user's \
      request with valid Mindustry Logic code, not to write plausible-looking code.

      The user message contains the request, an internal plan, and the relevant Mindustry \
      knowledge selected for this request. Use ONLY the instructions, syntax, properties and \
      examples from that knowledge. Never invent instructions.

      The knowledge covers Mindustry Logic (mlog) syntax, Logic Processors, all Logic \
      instructions, variables, op operations, control, sensor, radar, unit control (ubind/\
      ucontrol), world blocks, draw/drawflush, print/format, links (getlink), jumps, conditions, \
      loops, block access, displays, memory (read/write) and processor limits.

      Strict rules:
      - Output ONLY valid Mindustry Logic (mlog) code and nothing else.
      - Use only real Mindustry Logic instructions (set, op, jump, sensor, control, radar, \
      getlink, read, write, print, printflush, draw, drawflush, etc.). Never invent instructions.
      - No Markdown code fences or tags. No pseudo-code, JavaScript, or Python.
      - Respect block types: read liquids via @liquid/@totalLiquids/@liquidCapacity, power via \
      @totalPower/@powerCapacity, items via @totalItems/@itemCapacity, enabled state via @enabled.
      - Jump condition operators: equal, notEqual, lessThan, lessThanEq, greaterThan, \
      greaterThanEq; jump <target> always is unconditional.
      - Continuously running tasks need a loop (jump back to the start).
      - Use linked block names from the user's request or clear placeholders (cell1, tank1, pump1, \
      turret1, message1) and keep them consistent.
      - If a task is impossible in Mindustry Logic, reply exactly: UNSUPPORTED: <short reason>.
      - Return only the finished mlog code, ready to paste into a Logic Processor.
      """;

  /** Stage-1 system prompt: analyse the request and build an internal plan (never shown). */
  static final String PLAN_SYSTEM =
      """
      You are a Mindustry Logic (mlog) planner. Given the user's request and the relevant Mindustry \
      knowledge, produce a short structured plan (internal only, never shown to the user):
      1. what the user wants;
      2. which Mindustry objects/blocks are involved (linked blocks, or blocks to link);
      3. which data to read with sensor (block + property);
      4. which actions to send with control (block + action);
      5. conditions and whether a loop is needed;
      6. variables needed;
      7. whether links/getlink, memory (read/write), display (printflush or draw/drawflush), \
      radar or unit control (ubind/ucontrol) are needed.
      Do NOT write mlog yet. Be brief and concrete. If the task genuinely cannot be done in \
      Mindustry Logic, reply exactly: UNSUPPORTED: <short reason>.
      """;

  private LogicCodeGenerator() {}

  /**
   * Requests code for the given request. The callback receives either valid mlog code or a readable
   * error message.
   */
  public static void generate(String request, Cons<GenerationResult> callback) {
    if (request == null || request.trim().isEmpty()) {
      callback.get(GenerationResult.failure("Запрос пуст. Опишите, какой процессор нужен."));
      return;
    }

    String provider = LogicAssistantConfig.getProvider();
    String error = validateApiKey(provider, apiKeyFor(provider));
    if (error != null) {
      callback.get(GenerationResult.failure(error));
      return;
    }

    Set<String> topics = IntentAnalyzer.analyze(request);
    String knowledge = MindustryKnowledgeBase.retrieve(topics);

    generatePipeline(request, knowledge, callback, providerFor(provider));
  }

  /** Returns the provider implementation for the given provider id. */
  private static AiProvider providerFor(String provider) {
    if (LogicAssistantConfig.PROVIDER_GEMINI.equals(provider)) {
      return new RetryingProvider(new GeminiProvider());
    }
    if (LogicAssistantConfig.PROVIDER_ANTHROPIC.equals(provider)) {
      return new RetryingProvider(new AnthropicProvider());
    }
    if (LogicAssistantConfig.PROVIDER_GROQ.equals(provider)) {
      return new RetryingProvider(new GroqProvider());
    }
    if (LogicAssistantConfig.PROVIDER_GROK.equals(provider)) {
      return new RetryingProvider(new GrokProvider());
    }
    return new RetryingProvider(new LocalProvider());
  }

  /**
   * Returns an error message when the selected provider cannot work with the given API key, or
   * {@code null} when generation may proceed. The Local provider never requires a key; only Groq,
   * Anthropic, Gemini and Grok do.
   */
  static String validateApiKey(String provider, String apiKey) {
    boolean requiresKey =
        LogicAssistantConfig.PROVIDER_GROQ.equals(provider)
            || LogicAssistantConfig.PROVIDER_ANTHROPIC.equals(provider)
            || LogicAssistantConfig.PROVIDER_GEMINI.equals(provider)
            || LogicAssistantConfig.PROVIDER_GROK.equals(provider);
    if (requiresKey && (apiKey == null || apiKey.trim().isEmpty())) {
      return "API ключ "
          + LogicAssistantConfig.providerDisplayName(provider)
          + " не задан. Укажите его в поле ниже.";
    }
    return null;
  }

  /** Returns the API key of the given provider; always empty for the Local provider. */
  private static String apiKeyFor(String provider) {
    if (LogicAssistantConfig.PROVIDER_ANTHROPIC.equals(provider)) {
      return LogicAssistantConfig.getAnthropicApiKey();
    }
    if (LogicAssistantConfig.PROVIDER_GROQ.equals(provider)) {
      return LogicAssistantConfig.getGroqApiKey();
    }
    if (LogicAssistantConfig.PROVIDER_GEMINI.equals(provider)) {
      return LogicAssistantConfig.getGeminiApiKey();
    }
    if (LogicAssistantConfig.PROVIDER_GROK.equals(provider)) {
      return LogicAssistantConfig.getGrokApiKey();
    }
    return "";
  }

  /**
   * Two-stage pipeline with repair: hidden plan stage, then code generation. On a validation error
   * the model receives the validator's message and fixes the code, at most {@code maxAttempts}
   * attempts after the first (decided by response type: 2 for invalid code, 1 for an empty answer).
   */
  private static void generatePipeline(
      String request, String knowledge, Cons<GenerationResult> callback, AiProvider chat) {
    chat.chat(
        PLAN_SYSTEM,
        planUserMessage(request, knowledge),
        planResult -> {
          String plan = planResult.ok ? planResult.code : "";
          generateCode(request, knowledge, plan, chat, callback, 0, -1, null);
        });
  }

  private static void generateCode(
      String request,
      String knowledge,
      String plan,
      AiProvider chat,
      Cons<GenerationResult> callback,
      int attemptIndex,
      int maxAttempts,
      String previousError) {
    String user = userCodePrompt(request, plan, knowledge);
    if (previousError != null) {
      user =
          user
              + "\n\nThe previous attempt was rejected: "
              + previousError
              + ".\nFix the code and return ONLY the corrected mlog code now.";
    }

    chat.chat(
        SYSTEM_PROMPT,
        user,
        result -> {
          if (!result.ok) {
            callback.get(GenerationResult.failure(result.error));
            return;
          }
          Attempt attempt = evaluate(result.code);
          if (attempt.ok) {
            finishOk(attempt.code, request, callback);
            return;
          }
          if (attempt.unsupported) {
            callback.get(
                GenerationResult.failure(
                    "Задача не может быть выполнена средствами Mindustry Logic: "
                        + attempt.reason));
            return;
          }
          int max =
              maxAttempts >= 0 ? maxAttempts : (LogicCodeParser.isEmpty(attempt.code) ? 1 : 2);
          if (attemptIndex < max) {
            generateCode(
                request, knowledge, plan, chat, callback, attemptIndex + 1, max, attempt.reason);
          } else {
            callback.get(GenerationResult.failure(finalFailureText(attempt)));
          }
        });
  }

  /** Result of evaluating one raw model response. */
  static final class Attempt {
    final boolean ok;
    final boolean unsupported;
    final String code;
    final String reason;

    Attempt(boolean ok, boolean unsupported, String code, String reason) {
      this.ok = ok;
      this.unsupported = unsupported;
      this.code = code;
      this.reason = reason;
    }
  }

  /**
   * Evaluates a raw response: unsupported marker first, then an unknown-instruction scan on the raw
   * text (so invented instructions are rejected even though {@link LogicCodeParser#parse} would
   * otherwise strip them as prose), then parse + validate.
   */
  static Attempt evaluate(String raw) {
    if (raw == null) raw = "";
    String unsupported = LogicCodeParser.unsupportedReason(raw);
    if (unsupported != null) {
      return new Attempt(false, true, "", unsupported);
    }
    String unknown = LogicValidator.findUnknownInstruction(raw);
    if (unknown != null) {
      return new Attempt(false, false, LogicCodeParser.parse(raw), unknown);
    }
    String code = LogicCodeParser.parse(raw);
    String error = LogicCodeParser.validate(code);
    return new Attempt(error == null, false, code, error == null ? "" : error);
  }

  private static String finalFailureText(Attempt attempt) {
    if (LogicCodeParser.isEmpty(attempt.code)) {
      return "AI не вернул код. Попробуйте переформулировать запрос.";
    }
    return "ИИ вернул некорректный результат после нескольких попыток: "
        + attempt.reason
        + ". Попробуйте ещё раз или измените запрос.";
  }

  /** The stage-1 user message: the request plus only the relevant knowledge. */
  static String planUserMessage(String request, String knowledge) {
    return knowledge == null || knowledge.trim().isEmpty()
        ? request
        : request + "\n\nRelevant Mindustry knowledge (analyse the request with it):\n" + knowledge;
  }

  /** The stage-2 user message: request + hidden plan + relevant knowledge. */
  static String userCodePrompt(String request, String plan, String knowledge) {
    StringBuilder sb = new StringBuilder(request);
    if (plan != null && !plan.trim().isEmpty()) {
      sb.append("\n\nPlan (use it to write the code, do not repeat it):\n").append(plan.trim());
    }
    if (knowledge != null && !knowledge.trim().isEmpty()) {
      sb.append(
              "\n\nRelevant Mindustry knowledge (use ONLY these instructions, syntax and examples):\n")
          .append(knowledge);
    }
    return sb.toString();
  }

  private static void finishOk(String code, String request, Cons<GenerationResult> callback) {
    Log.info(
        "[Logic Assistant] Generated @ chars of logic code for request '@'.",
        code.length(),
        request);
    callback.get(GenerationResult.success(code));
  }

  /**
   * Builds an OpenAI-compatible request body for the given request and model (Groq / LM Studio).
   */
  static String buildOpenAiRequest(String request, String model) {
    return OpenAiChat.buildChat(SYSTEM_PROMPT, request, model);
  }

  static String buildOpenAiChat(String system, String user, String model) {
    return OpenAiChat.buildChat(system, user, model);
  }

  /** Builds an Anthropic Messages API request body. */
  static String buildAnthropicRequest(String request) {
    return AnthropicProvider.buildChat(SYSTEM_PROMPT, request);
  }

  static String buildAnthropicChat(String system, String user) {
    return AnthropicProvider.buildChat(system, user);
  }

  /** Builds a Gemini generateContent request body. */
  static String buildGeminiRequest(String request) {
    return GeminiProvider.buildChat(SYSTEM_PROMPT, request);
  }

  /** Builds an xAI Responses API request body. */
  static String buildGrokRequest(String request) {
    return GrokProvider.buildChat(SYSTEM_PROMPT, request, LogicAssistantConfig.GROK_DEFAULT_MODEL);
  }

  /** Escapes JSON special characters for embedding a string into a JSON request body. */
  static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /** Parses an OpenAI-compatible response ({@code choices[0].message.content}). */
  static String extractOpenAiText(String json) {
    return OpenAiChat.extractText(json);
  }

  /** Parses an Anthropic Messages API response ({@code content[0].text}). */
  static String extractAnthropicText(String json) {
    return AnthropicProvider.extractText(json);
  }

  /** Parses a Gemini generateContent response ({@code candidates[0].content.parts[].text}). */
  static String extractGeminiText(String json) {
    return GeminiProvider.extractText(json);
  }

  /** Parses an xAI Responses API response ({@code output[].content[].text}). */
  static String extractGrokText(String json) {
    return GrokProvider.extractText(json);
  }

  /**
   * Returns the HTTP status code of the given error, or -1 when the error is not an HTTP status
   * error. Prefers arc's structured {@code HttpStatus} field and falls back to parsing the message.
   */
  static int httpStatus(Throwable error) {
    if (error instanceof Http.HttpStatusException) {
      Http.HttpStatusException e = (Http.HttpStatusException) error;
      if (e.status != null) return e.status.code;
      return httpStatusCode(e);
    }
    return -1;
  }

  /** Matches the HTTP status code embedded in arc's HttpStatusException message. */
  private static int httpStatusCode(Http.HttpStatusException e) {
    String message = e.getMessage();
    if (message == null) return -1;
    Matcher matcher = STATUS_IN_MESSAGE.matcher(message);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
  }

  /** Matches an HTTP error message of the form "error: <code> ..." inside arc's exception. */
  private static final Pattern STATUS_IN_MESSAGE = Pattern.compile("error[^\\d]*(\\d{3})");

  /** Builds a readable user-facing message from an HTTP exception. */
  static String readableError(Throwable error) {
    if (error instanceof Http.HttpStatusException) {
      Http.HttpStatusException e = (Http.HttpStatusException) error;
      int code = httpStatusCode(e);
      if (code == 401) {
        return "Сервер отклонил API-ключ провайдера "
            + LogicAssistantConfig.providerDisplayName(LogicAssistantConfig.getProvider())
            + " (HTTP 401). Проверьте ключ.";
      }
      if (code == 403) {
        String reason = extractServerMessage(serverResponseMessage(e));
        return "Доступ запрещён (HTTP 403)"
            + (reason == null ? "." : ": " + reason + ".")
            + " Для "
            + LogicAssistantConfig.providerDisplayName(LogicAssistantConfig.getProvider())
            + " проверьте API-ключ и права доступа проекта.";
      }
      if (code == 400) {
        return "Сервер вернул ошибку запроса (HTTP 400). Попробуйте изменить запрос.";
      }
      if (code == 402) {
        return "Недостаточно средств на счёте API (HTTP 402).";
      }
      if (code == 404) {
        return "Сервер не нашёл API endpoint (HTTP 404). Проверьте URL.";
      }
      if (code == 429) {
        return "Слишком много запросов (HTTP 429). Подождите и попробуйте снова.";
      }
      if (code >= 500 && code < 600) {
        return "Сервер ИИ временно недоступен (HTTP " + code + "). Попробуйте позже.";
      }
      if (code < 0) {
        return "Сервер вернул ошибку. Проверьте настройки провайдера (HTTP "
            + safeText(error.getMessage())
            + ").";
      }
      return "Сервер вернул ошибку с кодом HTTP " + code + ".";
    }
    if (error instanceof SocketTimeoutException) {
      return "Превышено время ожидания ответа ИИ (60 секунд).";
    }
    if (error instanceof UnknownHostException) {
      return "Не удалось разрешить адрес сервера ИИ (DNS). Проверьте URL и интернет.";
    }
    if (error instanceof ConnectException || error instanceof NoRouteToHostException) {
      return "Нет соединения с сервером ИИ. Проверьте интернет и настройки провайдера.";
    }
    if (error instanceof SSLException) {
      return "Ошибка защищённого соединения (SSL/TLS) с сервером ИИ.";
    }
    if (error instanceof Http.HttpStatusException) {
      Http.HttpStatusException e = (Http.HttpStatusException) error;
      String body = serverResponseMessage(e);
      if (body != null && !body.isEmpty()) {
        return "Сервер провайдера вернул ошибку: " + body;
      }
    }
    String message = safeText(error == null ? null : error.getMessage());
    if (message.isEmpty()) {
      message = error == null ? "Неизвестная ошибка" : safeText(error.getClass().getSimpleName());
    }
    if (message.contains("timed out") || message.contains("Timeout")) {
      return "Превышено время ожидания ответа ИИ.";
    }
    if (message.contains("connect")
        || message.contains("refused")
        || message.contains("Connection")) {
      return "Нет соединения с сервером ИИ. Проверьте интернет и настройки провайдера.";
    }
    return "Ошибка при запросе к ИИ: " + message;
  }

  /** Extracts the response body text from an HTTP status exception, if available. */
  private static String serverResponseMessage(Http.HttpStatusException e) {
    try {
      return e.response == null ? null : e.response.getResultAsString();
    } catch (Throwable t) {
      return null;
    }
  }

  /** Best-effort extraction of a human-readable message from an error JSON body. */
  private static String extractServerMessage(String body) {
    if (body == null || body.isEmpty()) return null;
    try {
      Jval root = Jval.read(body);
      Jval error = root.get("error");
      if (error != null && error.isObject()) {
        Jval message = error.get("message");
        if (message != null) return message.asString();
      }
      if (root.has("message")) return root.get("message").asString();
      return null;
    } catch (Throwable t) {
      return null;
    }
  }

  /** Trims text and masks the configured API keys so they never appear in a message. */
  private static String safeText(String text) {
    if (text == null) return "";
    String result = text;
    String groqKey = LogicAssistantConfig.getGroqApiKey();
    if (groqKey != null && !groqKey.isEmpty()) result = result.replace(groqKey, "***");
    String anthropicKey = LogicAssistantConfig.getAnthropicApiKey();
    if (anthropicKey != null && !anthropicKey.isEmpty())
      result = result.replace(anthropicKey, "***");
    String geminiKey = LogicAssistantConfig.getGeminiApiKey();
    if (geminiKey != null && !geminiKey.isEmpty()) result = result.replace(geminiKey, "***");
    String grokKey = LogicAssistantConfig.getGrokApiKey();
    if (grokKey != null && !grokKey.isEmpty()) result = result.replace(grokKey, "***");
    return result.trim();
  }
}
