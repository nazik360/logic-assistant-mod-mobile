/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.func.Cons;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates the cached upstream Mindustry sources used to cross-check the local knowledge base.
 *
 * <p>Security rules:
 *
 * <ul>
 *   <li>Only https URLs on {@link #ALLOWED_HOST} under the allowed repository path are fetched;
 *       anything else is rejected by {@link #isAllowedUrl(String)}.
 *   <li>Fetched content is treated as plain text only: it is cached to disk and is never executed,
 *       compiled or interpreted.
 *   <li>The model keeps using the bundled, verified local base; upstream files are only cached and
 *       diffed for information.
 *   <li>If the network is unavailable, the local base is used unchanged.
 * </ul>
 */
public final class MindustryKnowledgeUpdater {

  /** Only this host may ever be fetched. */
  public static final String ALLOWED_HOST = "raw.githubusercontent.com";

  /** Allowed path prefix inside the repository. */
  public static final String ALLOWED_PATH_PREFIX = "/Anuken/Mindustry/";

  /** Default branch/tag used for updates (v159.7 lives on the master line). */
  public static final String DEFAULT_REF = "master";

  /** Upstream files that may be fetched and cached. */
  public static final String[] SOURCE_FILES = {
    "core/src/mindustry/logic/LAssembler.java",
    "core/src/mindustry/logic/LStatements.java",
    "core/src/mindustry/logic/LAccess.java",
    "core/src/mindustry/logic/LogicOp.java"
  };

  private static final String CACHE_DIR = "knowledge_cache";
  private static final int TIMEOUT_MS = 15000;

  /** Matches v159 statement registrations: {@code @RegisterStatement("name")}. */
  private static final Pattern INSTRUCTION_DECL =
      Pattern.compile("@RegisterStatement\\(\"([a-zA-Z0-9_]+)\"\\)");

  /** Injectable text fetcher so tests can run without a network. */
  public interface TextFetcher {
    String fetch(String url) throws Exception;
  }

  private MindustryKnowledgeUpdater() {}

  /** True when the URL may be fetched: https, allowed host, allowed repository path. */
  public static boolean isAllowedUrl(String url) {
    if (url == null) return false;
    String lower = url.toLowerCase();
    if (!lower.startsWith("https://")) return false;
    int hostStart = "https://".length();
    int pathStart = lower.indexOf('/', hostStart);
    if (pathStart == -1) return false;
    String host = lower.substring(hostStart, pathStart);
    if (!host.equals(ALLOWED_HOST)) return false;
    // The path prefix is compared case-insensitively because the URL was lowercased above.
    return lower.substring(pathStart).startsWith(ALLOWED_PATH_PREFIX.toLowerCase());
  }

  /** Builds the fetch URL for one upstream source file at the given ref. */
  public static String sourceUrl(String ref, String path) {
    return "https://" + ALLOWED_HOST + ALLOWED_PATH_PREFIX + ref + "/" + path;
  }

  /**
   * Runs {@link #updateBlocking(File)} on a background thread and reports the result through the
   * callback (on a background thread; wrap UI updates in {@code Core.app.post}).
   */
  public static void update(File cacheDir, Cons<String> callback) {
    new Thread(() -> callback.get(updateBlocking(cacheDir))).start();
  }

  /** Fetches all sources using the default network fetcher and returns a report. */
  public static String updateBlocking(File cacheDir) {
    return updateBlocking(cacheDir, MindustryKnowledgeUpdater::fetchText);
  }

  /** Fetches all sources through the given fetcher and returns a human-readable report. */
  public static String updateBlocking(File cacheDir, TextFetcher fetcher) {
    List<String> report = new ArrayList<>();
    boolean anyOk = false;
    for (String path : SOURCE_FILES) {
      String url = sourceUrl(DEFAULT_REF, path);
      if (!isAllowedUrl(url)) {
        report.add("Пропущено (не разрешённый источник): " + url);
        continue;
      }
      try {
        String text = fetcher.fetch(url);
        if (text == null || text.trim().isEmpty()) {
          throw new IOException("пустой ответ");
        }
        cache(cacheDir, path, text);
        report.add("OK: " + path + " (" + text.length() + " символов)");
        anyOk = true;
      } catch (Throwable t) {
        report.add("Не удалось скачать " + path + ": " + safeText(t));
      }
    }
    report.add("---");
    if (anyOk) {
      report.add(diffReport(cacheDir));
      report.add("Кэш обновлён. Модель продолжает использовать проверенную локальную базу.");
    } else {
      report.add(
          "Интернет недоступен или источники не отвечают. Используется локальная база знаний.");
    }
    return String.join("\n", report);
  }

  /**
   * Compares the instructions registered in the cached {@code LStatements.java} with the verified
   * local instruction list. Informational only; nothing is merged automatically.
   */
  public static String diffReport(File cacheDir) {
    String source = loadCached(cacheDir, SOURCE_FILES[1]);
    if (source == null || source.isEmpty()) {
      return "Кэш пуст: сначала скачайте исходники.";
    }
    Set<String> upstream = extractInstructionNames(source);
    Set<String> local = MindustryKnowledgeBase.verifiedInstructionNames();
    Set<String> notInLocal = new TreeSet<>();
    for (String name : upstream) {
      if (!local.contains(name)) notInLocal.add(name);
    }
    Set<String> notInUpstream = new TreeSet<>(local);
    notInUpstream.removeAll(upstream);
    return "В кэшированном исходнике зарегистрировано инструкций: "
        + upstream.size()
        + ". В локальной базе: "
        + local.size()
        + ". Не в локальной базе: "
        + (notInLocal.isEmpty() ? "нет" : notInLocal)
        + ". Не найдено в исходнике: "
        + (notInUpstream.isEmpty() ? "нет" : notInUpstream);
  }

  /** Extracts mlog instruction names from the upstream LStatements source (best-effort). */
  public static Set<String> extractInstructionNames(String lstatementsSource) {
    Set<String> names = new TreeSet<>();
    if (lstatementsSource == null) return names;
    Matcher matcher = INSTRUCTION_DECL.matcher(lstatementsSource);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /** Reads a cached upstream file, or {@code null} when it is missing. */
  public static String loadCached(File cacheDir, String path) {
    try {
      File file = cacheFile(cacheDir, path);
      return file.exists()
          ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
          : null;
    } catch (Throwable t) {
      return null;
    }
  }

  /** Saves a fetched upstream file into the cache directory. */
  public static void cache(File cacheDir, String path, String text) throws IOException {
    File file = cacheFile(cacheDir, path);
    file.getParentFile().mkdirs();
    Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
  }

  private static File cacheFile(File cacheDir, String path) {
    return new File(new File(cacheDir, CACHE_DIR), path.replace('/', '_'));
  }

  /** Synchronous https fetch of an allowed URL using the JDK's HTTP client. */
  static String fetchText(String url) throws IOException {
    if (!isAllowedUrl(url)) {
      throw new IOException("URL не входит в список разрешённых: " + url);
    }
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(TIMEOUT_MS);
    connection.setReadTimeout(TIMEOUT_MS);
    connection.setRequestProperty("User-Agent", "LogicAssistantMod/1.0");
    try {
      int code = connection.getResponseCode();
      if (code != 200) {
        throw new IOException("HTTP " + code);
      }
      try (InputStream in = connection.getInputStream()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    } finally {
      connection.disconnect();
    }
  }

  private static String safeText(Throwable t) {
    String message = t.getMessage();
    return message == null || message.trim().isEmpty()
        ? t.getClass().getSimpleName()
        : message.trim();
  }
}
