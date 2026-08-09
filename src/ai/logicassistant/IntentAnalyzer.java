/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Deterministic, keyword-based analyzer that turns a user's natural-language request into a set of
 * Mindustry topics. The topics are used to select only the relevant part of the knowledge base (see
 * {@link MindustryKnowledgeBase#retrieve(Set)}), so the model never receives the whole reference on
 * every request.
 *
 * <p>This is a cheap Java-side filter, not an LLM call. Detailed reasoning about the task itself is
 * left to the internal plan stage of {@link LogicCodeGenerator}.
 */
public final class IntentAnalyzer {

  public static final String POWER = "power";
  public static final String LIQUID = "liquid";
  public static final String PUMP = "pump";
  public static final String ITEM = "item";
  public static final String CONTAINER = "container";
  public static final String CONVEYOR = "conveyor";
  public static final String SORTER = "sorter";
  public static final String TURRET = "turret";
  public static final String RADAR = "radar";
  public static final String UNIT = "unit";
  public static final String MEMORY = "memory";
  public static final String DISPLAY = "display";
  public static final String DRAW = "draw";
  public static final String COLOR = "color";
  public static final String PRODUCTION = "production";
  public static final String HEAT = "heat";
  public static final String DOOR = "door";
  public static final String LAMP = "lamp";
  public static final String SWITCH = "switch";
  public static final String CORE = "core";
  public static final String LOOP = "loop";
  public static final String MESSAGE = "message";
  public static final String LINK = "link";

  /** Keyword table: topic -> words/prefixes that hint at it (request is lower-cased). */
  private static final String[][] KEYWORDS = {
    {
      POWER, "энерг", "аккумулятор", "батаре", "power", "battery", "электри", "солнечн", "генератор"
    },
    {LIQUID, "жидкост", "вода", "вод", "бак", "резервуар", "liquid", "water", "tank", "протеч"},
    {PUMP, "насос", "pump"},
    {ITEM, "предмет", "ресурс", "item", "items", "медь", "copper", "титан", "метал", "припасы"},
    {CONTAINER, "контейнер", "хранилище", "vault", "container", "ящик", "сундук"},
    {CONVEYOR, "конвейер", "транспортер", "conveyor", "конвеер"},
    {SORTER, "сортировщик", "сортир", "sorter", "сортиров"},
    {TURRET, "турель", "турел", "turret", "пушк", "оборо", "атак", "стреляй", "огонь", "стрел"},
    {RADAR, "радар", "radar", "враг", "enemy", "цель", "target", "обнаруж"},
    {UNIT, "юнит", "unit", "дрон", "дроны", "танк", "корабл", "отряд"},
    {MEMORY, "память", "ячейк", "memory", "cell", "запомина", "сохран"},
    {
      DISPLAY,
      "дисплей",
      "экран",
      "display",
      "монитор",
      "показыва",
      "показа",
      "вывед",
      "вывод",
      "screen"
    },
    {DRAW, "рис", "draw", "график", "картинк", "шрифт", "линию", "прямоугольник"},
    {COLOR, "цвет", "color", "палитр", "подсвет"},
    {
      PRODUCTION,
      "производ",
      "завод",
      "factory",
      "печь",
      "фабрик",
      "assembler",
      "press",
      "плав",
      "производс"
    },
    {HEAT, "нагрев", "тепл", "температур", "heat", "жар", "перегрев"},
    {DOOR, "дверь", "двери", "двер", "door", "ворота"},
    {LAMP, "светильник", "иллюминатор", "lamp", "ламп", "свет", "illuminator", "сигнальн"},
    {SWITCH, "переключател", "switch", "выключател", "кнопк", "рычаг", "тумблер"},
    {CORE, "ядро", "core", "ядре"},
    {
      LOOP, "цикл", "каждые", "каждый", "loop", "постоянн", "повторя", "непрерывн", "всегда", "тика"
    },
    {MESSAGE, "сообщен", "надпис", "message", "текст", "вывод"},
    {LINK, "link", "линк", "связь", "подключ"},
  };

  private IntentAnalyzer() {}

  /**
   * Analyzes a request and returns the set of topics it relates to. Never returns {@code null}; an
   * empty set means no Mindustry topic was detected (the knowledge base then returns only the
   * always-relevant core syntax).
   */
  public static Set<String> analyze(String request) {
    Set<String> topics = new LinkedHashSet<>();
    if (request == null || request.trim().isEmpty()) return topics;
    String lower = request.toLowerCase();
    for (String[] entry : KEYWORDS) {
      String topic = entry[0];
      for (int i = 1; i < entry.length; i++) {
        if (lower.contains(entry[i])) {
          topics.add(topic);
          break;
        }
      }
    }
    return topics;
  }
}
