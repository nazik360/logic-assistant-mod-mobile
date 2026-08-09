/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local, bundled knowledge base about Mindustry (version 159.7) and Mindustry Logic.
 *
 * <p>Everything in it is verified against the used Mindustry version:
 *
 * <ul>
 *   <li>{@code ai/logic_reference.txt} - the full mlog instruction reference; every numbered
 *       example is validated at test time with Mindustry's own parser ({@code LAssembler}).
 *   <li>{@code ai/knowledge/blocks.txt} - which blocks can be linked and what {@code sensor} /
 *       {@code control} can do with them.
 *   <li>{@code ai/knowledge/items.txt}, {@code liquids.txt}, {@code units.txt} - content names
 *       verified at test time against the {@code mindustry.content.*} classes of the v159.7 jar.
 * </ul>
 *
 * <p>{@link #retrieve(Set)} never returns the whole base: it selects only the sections relevant to
 * the request topics plus a small always-on core (syntax, variables, op, jump, common patterns), so
 * the model gets a compact, focused prompt.
 */
public final class MindustryKnowledgeBase {

  /** Upper bound for one {@link #retrieve(Set)} result, so prompts stay focused. */
  public static final int MAX_RETRIEVED_CHARS = 6000;

  private static final String LOGIC_PATH = "/ai/logic_reference.txt";
  private static final String BLOCKS_PATH = "/ai/knowledge/blocks.txt";
  private static final String ITEMS_PATH = "/ai/knowledge/items.txt";
  private static final String LIQUIDS_PATH = "/ai/knowledge/liquids.txt";
  private static final String UNITS_PATH = "/ai/knowledge/units.txt";

  /** Logic reference sections always included, no matter the request topics. */
  private static final int[] CORE_SECTIONS = {1, 2, 3, 4, 5, 19};

  private static final String LOGIC_TEXT = load(LOGIC_PATH);
  private static final String BLOCKS_TEXT = load(BLOCKS_PATH);
  private static final String ITEMS_TEXT = load(ITEMS_PATH);
  private static final String LIQUIDS_TEXT = load(LIQUIDS_PATH);
  private static final String UNITS_TEXT = load(UNITS_PATH);

  private static final Pattern SECTION_HEADER = Pattern.compile("^(\\d+)\\. (.+)$");
  private static final Pattern DASH_LINE = Pattern.compile("^-{5,}\\s*$");
  private static final Pattern BLOCK_SECTION_HEADER = Pattern.compile("^## (.+)$");
  private static final Pattern EXAMPLE_HEADER =
      Pattern.compile("^# (\\d+)\\) .*\\[topics: ([^]]+)]");

  private static final List<Section> SECTIONS = indexSections(LOGIC_TEXT, SECTION_HEADER);
  private static final List<Section> BLOCK_SECTIONS =
      indexSections(BLOCKS_TEXT, BLOCK_SECTION_HEADER);
  private static final List<Example> EXAMPLES = indexExamples(LOGIC_TEXT);

  private MindustryKnowledgeBase() {}

  /** The full bundled mlog reference (all sections). */
  public static String fullLogicReference() {
    return LOGIC_TEXT;
  }

  /** Block knowledge: what can be linked/controlled/sensed per block type. */
  public static String blocksKnowledge() {
    return BLOCKS_TEXT;
  }

  public static String itemsKnowledge() {
    return ITEMS_TEXT;
  }

  public static String liquidsKnowledge() {
    return LIQUIDS_TEXT;
  }

  public static String unitsKnowledge() {
    return UNITS_TEXT;
  }

  /**
   * Returns the knowledge relevant to the given topics: always-on core syntax plus the matched
   * logic sections, verified examples, block sections and content lists. The result is capped at
   * {@link #MAX_RETRIEVED_CHARS} characters.
   */
  public static String retrieve(Set<String> topics) {
    Set<String> t = topics == null ? Set.of() : topics;

    StringBuilder sb = new StringBuilder(MAX_RETRIEVED_CHARS + 256);
    sb.append("=== MINDUSTRY LOGIC - core syntax (always relevant) ===\n");
    for (int s : CORE_SECTIONS) {
      sb.append(sectionText(s));
    }

    Set<Integer> extra = new LinkedHashSet<>();
    for (String topic : t) {
      for (int s : sectionsFor(topic)) {
        if (!contains(CORE_SECTIONS, s)) extra.add(s);
      }
    }
    if (!extra.isEmpty()) {
      sb.append("=== MINDUSTRY LOGIC - relevant sections ===\n");
      for (int s : extra) {
        sb.append(sectionText(s));
      }
    }

    List<Example> examples = new ArrayList<>();
    for (Example example : EXAMPLES) {
      if (example.matches(t)) examples.add(example);
    }
    if (!examples.isEmpty()) {
      sb.append("=== VERIFIED EXAMPLES (relevant to this request) ===\n");
      for (Example example : examples) {
        sb.append(example.text).append("\n\n");
      }
    }

    Set<String> blockTopics = new LinkedHashSet<>();
    for (String topic : t) {
      blockTopics.addAll(blockSectionsFor(topic));
    }
    // Linking rules and shared sensor properties are almost always useful.
    blockTopics.add("link");
    blockTopics.add("content");
    if (!blockTopics.isEmpty()) {
      sb.append("=== BLOCK KNOWLEDGE (what logic can do with these blocks) ===\n");
      for (String blockTopic : blockTopics) {
        String text = blockSectionText(blockTopic);
        if (text != null) sb.append(text);
      }
    }

    if (t.contains(IntentAnalyzer.ITEM) || t.contains(IntentAnalyzer.CONTAINER)) {
      sb.append("=== ITEMS (verified for v159.7) ===\n").append(ITEMS_TEXT).append('\n');
    }
    if (t.contains(IntentAnalyzer.LIQUID) || t.contains(IntentAnalyzer.PUMP)) {
      sb.append("=== LIQUIDS (verified for v159.7) ===\n").append(LIQUIDS_TEXT).append('\n');
    }
    if (t.contains(IntentAnalyzer.UNIT)) {
      sb.append("=== UNITS (verified for v159.7) ===\n").append(UNITS_TEXT).append('\n');
    }

    return cap(sb.toString());
  }

  /** The number of topic-tagged verified examples in the reference. */
  public static int exampleCount() {
    return EXAMPLES.size();
  }

  /** The number of indexed logic reference sections. */
  public static int sectionCount() {
    return SECTIONS.size();
  }

  /**
   * The mlog instruction names this mod knows and allows. This is the exact set of statements the
   * v159.7 parser accepts (the {@code @RegisterStatement} names in {@code LStatements.java},
   * excluding {@code "#"} for comments and the unusable {@code noop} trap). Every name is checked
   * at test time with Mindustry's own parser using a representative valid line.
   */
  public static Set<String> verifiedInstructionNames() {
    return Set.of(
        "read",
        "write",
        "draw",
        "print",
        "printchar",
        "format",
        "drawflush",
        "printflush",
        "getlink",
        "control",
        "radar",
        "sensor",
        "set",
        "op",
        "select",
        "wait",
        "stop",
        "lookup",
        "packcolor",
        "unpackcolor",
        "end",
        "jump",
        "ubind",
        "ucontrol",
        "uradar",
        "ulocate",
        "query",
        "getblock",
        "setblock",
        "spawn",
        "bullet",
        "status",
        "weathersense",
        "weatherset",
        "spawnwave",
        "setrule",
        "message",
        "cutscene",
        "effect",
        "explosion",
        "setrate",
        "fetch",
        "sync",
        "clientdata",
        "getflag",
        "setflag",
        "setprop",
        "playsound",
        "playmusic",
        "setmarker",
        "makemarker",
        "localeprint");
  }

  private static int[] sectionsFor(String topic) {
    return switch (topic) {
      case IntentAnalyzer.POWER,
              IntentAnalyzer.LIQUID,
              IntentAnalyzer.PUMP,
              IntentAnalyzer.ITEM,
              IntentAnalyzer.CONTAINER,
              IntentAnalyzer.PRODUCTION,
              IntentAnalyzer.HEAT,
              IntentAnalyzer.SWITCH,
              IntentAnalyzer.CORE ->
          new int[] {6};
      case IntentAnalyzer.CONVEYOR,
              IntentAnalyzer.SORTER,
              IntentAnalyzer.DOOR,
              IntentAnalyzer.LAMP ->
          new int[] {7};
      case IntentAnalyzer.TURRET -> new int[] {6, 7};
      case IntentAnalyzer.RADAR -> new int[] {12};
      case IntentAnalyzer.UNIT -> new int[] {16};
      case IntentAnalyzer.MEMORY -> new int[] {9};
      case IntentAnalyzer.DISPLAY, IntentAnalyzer.MESSAGE -> new int[] {10};
      case IntentAnalyzer.DRAW -> new int[] {11};
      case IntentAnalyzer.COLOR -> new int[] {15};
      case IntentAnalyzer.LOOP -> new int[] {5};
      case IntentAnalyzer.LINK -> new int[] {8};
      default -> new int[0];
    };
  }

  private static List<String> blockSectionsFor(String topic) {
    List<String> result = new ArrayList<>();
    switch (topic) {
      case IntentAnalyzer.POWER -> result.add("power");
      case IntentAnalyzer.LIQUID, IntentAnalyzer.PUMP -> result.add("liquid");
      case IntentAnalyzer.ITEM,
              IntentAnalyzer.CONTAINER,
              IntentAnalyzer.CONVEYOR,
              IntentAnalyzer.SORTER ->
          result.add("item");
      case IntentAnalyzer.TURRET, IntentAnalyzer.RADAR -> result.add("turret");
      case IntentAnalyzer.UNIT -> result.add("unit");
      case IntentAnalyzer.PRODUCTION, IntentAnalyzer.HEAT -> result.add("production");
      case IntentAnalyzer.CORE -> result.add("core");
      case IntentAnalyzer.MEMORY,
              IntentAnalyzer.DISPLAY,
              IntentAnalyzer.DRAW,
              IntentAnalyzer.COLOR,
              IntentAnalyzer.DOOR,
              IntentAnalyzer.LAMP,
              IntentAnalyzer.SWITCH,
              IntentAnalyzer.MESSAGE ->
          result.add("logic");
      default -> {}
    }
    return result;
  }

  private static String sectionText(int number) {
    for (Section section : SECTIONS) {
      if (section.number == number) return section.text;
    }
    return "";
  }

  private static String blockSectionText(String topic) {
    for (Section section : BLOCK_SECTIONS) {
      if (section.topic.equals(topic)) return section.text;
    }
    return null;
  }

  private static List<Section> indexSections(String text, Pattern header) {
    List<Section> list = new ArrayList<>();
    int number = 0;
    String topic = null;
    StringBuilder body = new StringBuilder();
    String[] lines = text.split("\n", -1);
    for (String line : lines) {
      Matcher matcher = header.matcher(line.trim());
      if (matcher.matches()) {
        if (topic != null) list.add(new Section(number, topic, body.toString()));
        number = matcher.groupCount() >= 2 ? parseInt(matcher.group(1)) : 0;
        topic = matcher.groupCount() >= 2 ? matcher.group(2) : matcher.group(1);
        body = new StringBuilder();
      } else if (topic != null && !DASH_LINE.matcher(line.trim()).matches()) {
        body.append(line).append('\n');
      }
    }
    if (topic != null) list.add(new Section(number, topic, body.toString()));
    return list;
  }

  private static List<Example> indexExamples(String text) {
    List<Example> list = new ArrayList<>();
    StringBuilder current = null;
    int number = 0;
    Set<String> tags = Set.of();
    String[] lines = text.split("\n", -1);
    for (String line : lines) {
      Matcher matcher = EXAMPLE_HEADER.matcher(line.trim());
      if (matcher.matches()) {
        if (current != null) list.add(new Example(number, tags, current.toString().trim()));
        number = parseInt(matcher.group(1));
        tags = Set.of(matcher.group(2).trim().split("\\s+"));
        current = new StringBuilder();
        current.append(line.trim()).append('\n');
      } else if (current != null) {
        current.append(line).append('\n');
      }
    }
    if (current != null) list.add(new Example(number, tags, current.toString().trim()));
    return list;
  }

  private static boolean contains(int[] array, int value) {
    for (int item : array) {
      if (item == value) return true;
    }
    return false;
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  /** Cuts at a newline boundary so the prompt never ends mid-line. */
  private static String cap(String text) {
    if (text.length() <= MAX_RETRIEVED_CHARS) return text.trim();
    int end = text.lastIndexOf('\n', MAX_RETRIEVED_CHARS);
    return (end > 0 ? text.substring(0, end) : text.substring(0, MAX_RETRIEVED_CHARS)).trim();
  }

  private static String load(String path) {
    try (InputStream in = MindustryKnowledgeBase.class.getResourceAsStream(path)) {
      if (in == null) return "";
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static final class Section {
    final int number;
    final String topic;
    final String text;

    Section(int number, String topic, String text) {
      this.number = number;
      this.topic = topic;
      this.text = text;
    }
  }

  private static final class Example {
    final int number;
    final Set<String> tags;
    final String text;

    Example(int number, Set<String> tags, String text) {
      this.number = number;
      this.tags = tags;
      this.text = text;
    }

    boolean matches(Set<String> topics) {
      for (String tag : tags) {
        if (topics.contains(tag)) return true;
      }
      return false;
    }
  }
}
