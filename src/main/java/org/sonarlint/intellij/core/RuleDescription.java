/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.core;

import com.intellij.openapi.util.text.StringUtil;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringEscapeUtils;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class RuleDescription {
  private final String key;
  
  private final String name;
  
  private final IssueSeverity severity;
  
  private final RuleType type;
  private final String html;
  private final List<Param> params;

  public static RuleDescription from(String ruleKey, String name, IssueSeverity severity, RuleType type, String fullDescription) {
    return from(ruleKey, name, severity, type, fullDescription, List.of());
  }

  public static RuleDescription from(String ruleKey, String name, IssueSeverity severity, RuleType type, @Nullable String fullDescription, List<Param> params) {
    return new RuleDescription(ruleKey, name, severity, type, fullDescription, params);
  }

  private RuleDescription(String key, String name, IssueSeverity severity, RuleType type, String html, List<Param> params) {
    this.key = key;
    this.name = name;
    this.severity = severity;
    this.type = type;
    this.html = html;
    this.params = params;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public List<Param> getParams() {
    return params;
  }

  public String getHtml() {
    return html;
  }

  private static String buildHtml(String ruleKey, @Nullable String fullDescription) {
    var builder = new StringBuilder();
    builder.append(fullDescription);
    return builder.toString();
  }

  public static class Param {
    private final String name;
    private final String description;
    private final String defaultValue;

    public Param(String name, String description, @Nullable String defaultValue) {
      this.name = name;
      this.description = description;
      this.defaultValue = defaultValue;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getDefaultValue() {
      return defaultValue;
    }
  }
}
