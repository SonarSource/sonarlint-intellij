/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class RuleDescription {
  private final String key;
  private final String html;

  public static RuleDescription from(String key, String name, String severity, @Nullable String type, String fullDescription) {
    return from(key, name, severity, type, fullDescription, Collections.emptyList());
  }

  public static RuleDescription from(String key, String name, String severity, @Nullable String type, @Nullable String fullDescription, List<Param> params) {
    return new RuleDescription(key, buildHtml(key, name, severity, type, fullDescription, params));
  }

  private RuleDescription(String key, String html) {
    this.key = key;
    this.html = html;
  }

  public String getKey() {
    return key;
  }

  public String getHtml() {
    return html;
  }

  private static String buildHtml(String key, String name, String severity, @Nullable String type, @Nullable String fullDescription, List<Param> params) {
    var builder = new StringBuilder();
    builder.append("<h2>")
      .append(StringEscapeUtils.escapeHtml(name))
      .append("</h2>");
    appendRuleAttributesHtmlTable(key, severity, type, builder);
    builder.append("<br />")
      .append(fullDescription);
    if (!params.isEmpty()) {
      builder.append(renderRuleParams(params, key));
    }
    return builder.toString();
  }

  public static void appendRuleAttributesHtmlTable(String ruleKey, String ruleSeverity, @org.jetbrains.annotations.Nullable String ruleType, StringBuilder builder) {
    // apparently some css properties are not supported
    var imgAttributes = "valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\"";

    builder.append("<table><tr>");
    if (ruleType != null) {
      builder.append("<td>").append("<img ").append(imgAttributes).append(" src=\"file:///type/").append(ruleType).append("\"/></td>")
        .append("<td class=\"pad\"><b>").append(clean(ruleType)).append("</b></td>");
    }
    builder.append("<td>").append("<img ").append(imgAttributes).append(" src=\"file:///severity/").append(ruleSeverity).append("\"/></td>")
      .append("<td class=\"pad\"><b>").append(clean(ruleSeverity)).append("</b></td>")
      .append("<td><b>").append(ruleKey).append("</b></td>")
      .append("</tr></table>");
  }

  private static String clean(String txt) {
    return StringUtil.capitalize(txt.toLowerCase(Locale.ENGLISH).replace("_", " "));
  }

  private static String renderRuleParams(List<Param> params, String ruleKey) {
    return "<table class=\"rule-params\">" +
      "<caption><h2>Parameters</h2></caption>" +
      "<tr class='thead'>" +
      "<td colspan=\"2\">" +
      "Following parameter values can be set in <a href=\"#rule\">Rule Settings</a>. " +
      "In connected mode, server side configuration overrides local settings." +
      "</td>" +
      "</tr>" +
      params.stream().map(param -> renderRuleParam(param, ruleKey)).collect(Collectors.joining("\n")) +
      "</table>";
  }

  private static String renderRuleParam(Param param, String ruleKey) {
    var paramDescription = param.description != null ? ("<p>" + param.description + "</p>") : "";
    var paramDefaultValue = param.defaultValue;
    var defaultValue = paramDefaultValue != null ? paramDefaultValue : "(none)";
    var currentValue = getGlobalSettings().getRuleParamValue(ruleKey, param.name).orElse(defaultValue);
    return "<tr class='tbody'>" +
    // The <br/> elements are added to simulate a "vertical-align: top" (not supported by Java 11 CSS renderer)
      "<th>" + param.name + "<br/><br/></th>" +
      "<td>" +
      paramDescription +
      "<p><small>Current value: <code>" + currentValue + "</code></small></p>" +
      "<p><small>Default value: <code>" + defaultValue + "</code></small></p>" +
      "</td>" +
      "</tr>";
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
  }
}
