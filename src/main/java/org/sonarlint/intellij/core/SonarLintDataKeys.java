package org.sonarlint.intellij.core;

import com.intellij.openapi.actionSystem.DataKey;
import org.sonarlint.intellij.issue.LiveIssue;

/**
 * @author Julian Siebert (j.siebert@micromata.de)
 */
public class SonarLintDataKeys
{
  private SonarLintDataKeys()
  {
  }

  public static final DataKey<LiveIssue> SELECTED_ISSUE = DataKey.create("SonarLint.Issue");
}
