/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.clion.common;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class BuildWrapperJsonGenerator {
  private final StringBuilder builder;
  private boolean first = true;

  public BuildWrapperJsonGenerator() {
    builder = new StringBuilder()
      .append("{"
        + "\"version\":0,"
        + "\"captures\":[");
  }

  public BuildWrapperJsonGenerator add(AnalyzerConfiguration.Configuration configuration) {
    if (first) {
      first = false;
    } else {
      builder.append(",");
    }
    appendEntry(configuration);
    return this;
  }

  private void appendEntry(AnalyzerConfiguration.Configuration entry) {
    var quotedCompilerExecutable = quote(entry.compilerExecutable());
    builder.append("{")
      .append("\"compiler\":\"")
      .append(entry.compilerKind())
      .append("\",")
      .append("\"cwd\":")
      .append(quote(entry.compilerWorkingDir())).append(",")
      .append("\"executable\":")
      .append(quotedCompilerExecutable)
      .append(",");
    builder.append("\"properties\":{");
    var firstProp = true;
    for (Map.Entry<String, String> prop : entry.properties().entrySet()) {
      if (!firstProp) {
        builder.append(",");
      } else {
        firstProp = false;
      }
      builder.append("\"").append(prop.getKey()).append("\":").append(quote(prop.getValue()));
    }
    builder.append("},");
    builder.append("\"cmd\":[")
      .append(quotedCompilerExecutable)
      .append(",")
      .append(quote(entry.virtualFile().getCanonicalPath()));
    entry.compilerSwitches().forEach(s -> builder.append(",").append(quote(s)));
    builder.append("]}");
  }

  public String build() {
    return builder.append("]}").toString();
  }

  static String quote(@Nullable String string) {
    if (string == null || string.isEmpty()) {
      return "\"\"";
    }

    char c;
    int i;
    var len = string.length();
    var sb = new StringBuilder(len + 4);
    String t;

    sb.append('"');
    for (i = 0; i < len; i += 1) {
      c = string.charAt(i);
      switch (c) {
        case '\\', '"' -> {
          sb.append('\\');
          sb.append(c);
        }
        case '\b' -> sb.append("\\b");
        case '\t' -> sb.append("\\t");
        case '\n' -> sb.append("\\n");
        case '\f' -> sb.append("\\f");
        case '\r' -> sb.append("\\r");
        default -> {
          if (c < ' ') {
            t = "000" + Integer.toHexString(c);
            sb.append("\\u").append(t.substring(t.length() - 4));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
