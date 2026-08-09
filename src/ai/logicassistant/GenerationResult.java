/*
 * Copyright (c) 2026 WkSYk (GitHub: nazik360). All rights reserved.
 * This file is part of the Logic Assistant mod for Mindustry.
 * See the LICENSE file in the project root for full copyright terms.
 */
package ai.logicassistant;

/** Result of a code generation attempt. Keeps generated code separate from error reporting. */
public final class GenerationResult {

  public final boolean ok;
  public final String code;
  public final String error;

  /** HTTP status of the provider response; -1 when unknown or not an HTTP error. */
  public final int httpStatus;

  private GenerationResult(boolean ok, String code, String error, int httpStatus) {
    this.ok = ok;
    this.code = code;
    this.error = error;
    this.httpStatus = httpStatus;
  }

  public static GenerationResult success(String code) {
    return new GenerationResult(true, code, null, -1);
  }

  public static GenerationResult failure(String error) {
    return failure(error, -1);
  }

  public static GenerationResult failure(String error, int httpStatus) {
    return new GenerationResult(false, null, error, httpStatus);
  }
}
