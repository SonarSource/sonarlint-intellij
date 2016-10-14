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
