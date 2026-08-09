/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import java.util.Set;

/**
 * Cleans an AI-generated response into usable Mindustry Logic (mlog) source and validates it.
 *
 * <p>AI responses may contain markdown code fences, a leading {@code mlog} tag, placeholder
 * ellipsis lines or surrounding explanations. {@link #parse(String)} strips those; {@link
 * #validate(String)} then verifies the result is real Mindustry Logic using Mindustry's own parser
 * ({@link LAssembler}), which rejects unknown instructions, wrong argument counts and broken jump
 * targets.
 */
public final class LogicCodeParser {

  private LogicCodeParser() {}

  /** Strips fences, tags, placeholder lines and returns clean mlog code ("" if nothing is left). */
  public static String parse(String response) {
    if (response == null) return "";
    String code = response.trim();
    if (code.isEmpty()) return "";

    // Prefer the first fenced code block when markdown is present.
    if (code.contains("```")) {
      code = extractFencedBlock(code);
    }

    // Remove a leading "mlog" tag (e.g. "mlog read x cell1 0").
    if (code.regionMatches(true, 0, "mlog", 0, 4)) {
      int i = 4;
      while (i < code.length() && Character.isWhitespace(code.charAt(i))) i++;
      code = code.substring(i);
    }

    // Remove placeholder ellipsis lines ("..."/"…") used by AI to abbreviate code.
    code = removePlaceholders(code);

    // Drop obvious prose lines around the code while keeping every real mlog line (instructions,
    // labels, comments) intact - including words inside string values.
    code = keepOnlyCodeLines(code);

    return code.trim();
  }

  /**
   * Returns the reason from an "UNSUPPORTED: <reason>" response, or {@code null} when the response
   * is not an UNSUPPORTED message.
   */
  public static String unsupportedReason(String raw) {
    if (raw == null) return null;
    int idx = raw.indexOf("UNSUPPORTED:");
    if (idx == -1) return null;
    String reason = raw.substring(idx + "UNSUPPORTED:".length()).trim();
    if (reason.isEmpty()) return "причина не указана";
    int nl = reason.indexOf('\n');
    if (nl != -1) reason = reason.substring(0, nl).trim();
    return reason.isEmpty() ? "причина не указана" : reason;
  }

  /**
   * @return whether the produced code is empty/blank.
   */
  public static boolean isEmpty(String code) {
    return code == null || code.trim().isEmpty();
  }

  /**
   * Returns a user-facing error message when the code is not usable, paste-able Mindustry Logic, or
   * {@code null} when it is valid. Uses Mindustry's own parser to reject empty results, markdown,
   * prose, pseudo-code, unknown instructions and broken jump targets.
   */
  public static String validate(String code) {
    if (isEmpty(code)) {
      return "пустой результат";
    }
    if (containsMarkdown(code)) {
      return "содержит Markdown или текст";
    }
    if (containsOnlyComments(code)) {
      return "содержит только комментарии";
    }
    return LogicValidator.validate(code);
  }

  /** True when every non-empty line is a {@code #} comment, i.e. there is no instruction at all. */
  private static boolean containsOnlyComments(String code) {
    for (String line : code.split("\n", -1)) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      return false;
    }
    return true;
  }

  private static boolean containsMarkdown(String code) {
    return code.contains("```") || code.contains("`mlog");
  }

  private static String extractFencedBlock(String text) {
    int start = text.indexOf("```");
    if (start == -1) return text;

    int contentStart = text.indexOf('\n', start);
    if (contentStart == -1) return "";
    contentStart++;

    int end = text.indexOf("```", contentStart);
    return end == -1 ? text.substring(contentStart) : text.substring(contentStart, end);
  }

  private static String removePlaceholders(String code) {
    String[] lines = code.split("\n", -1);
    StringBuilder out = new StringBuilder(code.length());
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.equals("...") || trimmed.equals("…")) continue;
      out.append(line).append('\n');
    }
    return out.toString();
  }

  /** All real mlog instructions; used to tell code apart from prose around it. */
  private static final Set<String> KEYWORDS =
      Set.of(
          "set",
          "op",
          "jump",
          "end",
          "stop",
          "wait",
          "print",
          "printflush",
          "printchar",
          "format",
          "sensor",
          "getlink",
          "read",
          "write",
          "lookup",
          "radar",
          "control",
          "draw",
          "drawflush",
          "packcolor",
          "unpackcolor",
          "status",
          "effect",
          "explosion",
          "spawn",
          "setflag",
          "getflag",
          "setprop",
          "setrule",
          "setrate",
          "setblock",
          "getblock",
          "fetch",
          "sync",
          "select",
          "message",
          "clientdata",
          "cutscene",
          "playsound",
          "playmusic",
          "setmarker",
          "makemarker",
          "localeprint",
          "ubind",
          "ucontrol",
          "uradar",
          "ulocate",
          "weathersense",
          "weatherset",
          "spawnwave",
          "bullet",
          "noop");

  /**
   * Drops lines that are clearly prose while keeping every real mlog line (instructions, labels,
   * comments) intact. A line whose content mentions normal words inside string values is still
   * kept, because the check looks at the first word only.
   */
  private static String keepOnlyCodeLines(String code) {
    String[] lines = code.split("\n", -1);
    StringBuilder out = new StringBuilder(code.length());
    int kept = 0;
    for (String line : lines) {
      if (isCodeLine(line)) {
        out.append(line).append('\n');
        kept++;
      }
    }
    // If nothing was kept the text was misjudged as prose; return it unchanged so the validator
    // produces a proper error instead of an empty result.
    return kept == 0 ? code : out.toString();
  }

  private static boolean isCodeLine(String line) {
    String t = line.trim();
    if (t.isEmpty() || t.startsWith("#")) return true;
    if (t.startsWith("```") || t.startsWith("`")) return false;
    // A label line: "name:"
    if (t.endsWith(":") && !t.contains(" ")) return true;
    int end = 0;
    while (end < t.length() && !Character.isWhitespace(t.charAt(end))) end++;
    String first = end == 0 ? t : t.substring(0, end);
    return KEYWORDS.contains(first);
  }
}
