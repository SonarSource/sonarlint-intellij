package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Optional;
import javax.swing.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.IssuesViewTabOpener;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class JumpToRuleDescriptionQuickFix implements IntentionAction, PriorityAction, Iconable {

  private final String ruleKey;

  public JumpToRuleDescriptionQuickFix(String ruleKey) {
    this.ruleKey = ruleKey;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return "Jump to SonarLint rulee '" + ruleKey + "'";
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getFamilyName() {
    return "SonarLint jump to rule";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !isProjectConnected(project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    IssueManager issueManager = SonarLintUtils.getService(project, IssueManager.class);
    Collection<LiveIssue> liveIssues = issueManager.getForFile(file.getVirtualFile());
    Optional<LiveIssue> liveIssue = liveIssues.stream().filter(issue -> issue.getRuleKey().equals(ruleKey)).findFirst();
    if(!liveIssue.isPresent()) {
      return;
    }
    UIUtil.invokeLaterIfNeeded(() -> SonarLintUtils.getService(project, IssuesViewTabOpener.class)
      .selectIssue(liveIssue.get()));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Forward;
  }

  private static boolean isProjectConnected(Project project) {
    SonarLintProjectSettings projectSettings = SonarLintUtils.getService(project, SonarLintProjectSettings.class);
    return projectSettings.isBindingEnabled();
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.NORMAL;
  }
}
