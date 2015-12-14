/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.util;

public final class SonarLintConstants {

  public static final String SONAR_URL = "sonar.host.url";

  public static final String ANALYSIS_MODE = "sonar.analysis.mode";
  public static final String ANALYSIS_MODE_ISSUES = "issues";
  public static final String VERBOSE_PROPERTY = "sonar.verbose";
  public static final String WORK_DIR = "sonar.working.directory";
  public static final String USE_WS_CACHE = "sonar.useWsCache";

  private SonarLintConstants() {
  }
}
