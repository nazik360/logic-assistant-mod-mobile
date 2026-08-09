/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.struct.Seq;
import mindustry.logic.LAssembler;
import mindustry.logic.LStatement;
import mindustry.logic.LStatements;

/**
 * Validates that text is usable, paste-able Mindustry Logic (mlog).
 *
 * <p>Validation is delegated to Mindustry's own parser ({@link LAssembler#read(String, boolean)}),
 * so the result matches what the game accepts: unknown or misspelled instructions and invalid
 * {@code op} codes are rejected, undefined jump labels are reported, and the same lenient treatment
 * of argument counts the game applies is kept (never stricter than the game). A separate name scan
 * gives precise "instruction does not exist" messages for invented instructions.
 */
public final class LogicValidator {

  private LogicValidator() {}

  /**
   * Returns a user-facing error message when the code is not usable Mindustry Logic, or {@code
   * null} when it is valid.
   */
  public static String validate(String code) {
    if (code == null || code.trim().isEmpty()) return "программа пуста";

    String unknown = findUnknownInstruction(code);
    if (unknown != null) return unknown;

    try {
      Seq<LStatement> statements = LAssembler.read(code, true);
      for (LStatement statement : statements) {
        if (statement instanceof LStatements.InvalidStatement) {
          return "содержит недопустимую инструкцию Mindustry Logic "
              + "(проверьте синтаксис, операторы op и аргументы)";
        }
      }
      return null;
    } catch (Throwable t) {
      String message = t.getMessage();
      if (message != null && message.contains("Undefined jump location")) {
        return "некорректный адрес перехода: " + message;
      }
      return "не является корректным синтаксисом Mindustry Logic: "
          + (message == null ? "ошибка разбора" : message);
    }
  }

  /**
   * Scans every non-empty, non-comment, non-label line and rejects lines that start with a
   * lowercase word that is not a real Mindustry Logic instruction. This catches invented or
   * misspelled instructions (for example a hallucinated {@code banana}) with a precise message
   * before the game parser is consulted. Lines whose first word starts with an uppercase letter are
   * treated as surrounding prose and skipped here (the game parser rejects prose anyway), and a
   * leading {@code mlog} tag is ignored because {@link LogicCodeParser#parse} strips it.
   */
  public static String findUnknownInstruction(String code) {
    for (String rawLine : code.split("\n", -1)) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      int end = 0;
      while (end < line.length()
          && !Character.isWhitespace(line.charAt(end))
          && line.charAt(end) != '#') {
        end++;
      }
      String first = line.substring(0, end);
      if (first.isEmpty() || first.endsWith(":") || first.equalsIgnoreCase("mlog")) continue;
      if (!Character.isLowerCase(first.charAt(0))) continue;
      if (!MindustryKnowledgeBase.verifiedInstructionNames().contains(first)) {
        return "инструкция '" + first + "' не существует в Mindustry Logic. Строка: " + line;
      }
    }
    return null;
  }
}
