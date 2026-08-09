/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

import arc.func.Cons;
import arc.util.Log;
import arc.util.Timer;

/**
 * Decorator that retries a provider call on transient HTTP errors: {@code 429 Too Many Requests}
 * and {@code 5xx} server errors. Retries happen with exponential backoff (2s, then 4s, then 8s), at
 * most {@value #MAX_RETRIES} retries per call. Client errors ({@code 4xx} other than 429) are never
 * retried because retrying them would not help. Retries are strictly sequential: the next attempt
 * is scheduled only after the previous one has finished, so at most one HTTP request is in flight.
 */
final class RetryingProvider implements AiProvider {

  static final int MAX_RETRIES = 3;

  /** Backoff (seconds) before the 1st, 2nd and 3rd retry. */
  static final int[] BACKOFF_SECONDS = {2, 4, 8};

  static final String RATE_LIMIT_MESSAGE =
      "Слишком много запросов. Подождите немного и попробуйте снова.";

  /** Schedules a delayed task; the default uses arc's {@link Timer}. */
  interface Delayer {
    void schedule(Runnable task, float delaySeconds);
  }

  private final AiProvider delegate;
  private final Delayer delayer;

  RetryingProvider(AiProvider delegate) {
    this(delegate, (task, delaySeconds) -> Timer.schedule(task, delaySeconds));
  }

  RetryingProvider(AiProvider delegate, Delayer delayer) {
    this.delegate = delegate;
    this.delayer = delayer;
  }

  @Override
  public void chat(String system, String user, Cons<GenerationResult> result) {
    attempt(system, user, result, 0);
  }

  private void attempt(String system, String user, Cons<GenerationResult> result, int attempt) {
    delegate.chat(
        system,
        user,
        response -> {
          if (response.ok || !retryable(response.httpStatus)) {
            result.get(response);
            return;
          }
          if (attempt >= MAX_RETRIES) {
            result.get(
                response.httpStatus == 429
                    ? GenerationResult.failure(RATE_LIMIT_MESSAGE, 429)
                    : response);
            return;
          }
          int delaySeconds = BACKOFF_SECONDS[attempt];
          Log.warn(
              "[Logic Assistant] HTTP "
                  + response.httpStatus
                  + ", retrying in "
                  + delaySeconds
                  + "s (attempt "
                  + (attempt + 1)
                  + "/"
                  + MAX_RETRIES
                  + ")");
          delayer.schedule(() -> attempt(system, user, result, attempt + 1), (float) delaySeconds);
        });
  }

  private static boolean retryable(int httpStatus) {
    return httpStatus == 429 || (httpStatus >= 500 && httpStatus < 600);
  }
}
