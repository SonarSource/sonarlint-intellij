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
package org.sonarlint.intellij.issue;

import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

public class DefaultIssue implements Issue {
  private String severity;
  private Integer startLine;
  private Integer startLineOffset;
  private Integer endLine;
  private Integer endLineOffset;
  private String message;
  private String ruleKey;
  private String ruleName;

  @Override public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  @Override public Integer getStartLine() {
    return startLine;
  }

  public void setStartLine(Integer startLine) {
    this.startLine = startLine;
  }

  @Override public Integer getStartLineOffset() {
    return startLineOffset;
  }

  public void setStartLineOffset(Integer startLineOffset) {
    this.startLineOffset = startLineOffset;
  }

  @Override public Integer getEndLine() {
    return endLine;
  }

  public void setEndLine(Integer endLine) {
    this.endLine = endLine;
  }

  @Override public Integer getEndLineOffset() {
    return endLineOffset;
  }

  public void setEndLineOffset(Integer endLineOffset) {
    this.endLineOffset = endLineOffset;
  }

  @Override public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override public String getRuleKey() {
    return ruleKey;
  }

  public void setRuleKey(String ruleKey) {
    this.ruleKey = ruleKey;
  }

  @Override public String getRuleName() {
    return ruleName;
  }

  @Override public ClientInputFile getInputFile() {
    return null;
  }

  public void setRuleName(String ruleName) {
    this.ruleName = ruleName;
  }

}
