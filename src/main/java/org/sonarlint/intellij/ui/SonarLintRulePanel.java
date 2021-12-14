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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.ui.ruledescription.RuleDescriptionHTMLEditorKit;

import static org.sonarlint.intellij.ui.HtmlUtils.fixPreformattedText;

public class SonarLintRulePanel {
  private final Project project;
  private final JPanel panel;
  private JEditorPane editor;
  private RuleDescriptionHyperLinkListener ruleDescriptionHyperLinkListener;
  private String currentRuleKey;
  private Module currentModule;

  public SonarLintRulePanel(Project project) {
    this.project = project;
    panel = new JPanel(new BorderLayout());
    setRuleKey(null, null);
    show();
  }

  public void setRuleKey(@Nullable Module module, @Nullable String ruleKey) {
    if (Objects.equals(currentModule, module) && Objects.equals(currentRuleKey, ruleKey)) {
      return;
    }
    this.currentRuleKey = ruleKey;
    this.currentModule = module;
    if (module == null || ruleKey == null) {
      nothingToDisplay(false);
      return;
    }
    displayLoadingMessage();
    try {
      SonarLintUtils.getService(project, ProjectBindingManager.class)
        .getFacade(module)
        .getActiveRuleDescription(ruleKey)
        .thenAccept(ruleDescription -> {
          if (ruleDescription == null) {
            ApplicationManager.getApplication().invokeLater(() -> nothingToDisplay(true));
            return;
          }
          var htmlBody = fixPreformattedText(ruleDescription.getHtml());
          ApplicationManager.getApplication().invokeLater(() -> updateEditor(htmlBody, ruleDescription.getKey()));
        }).get(30, TimeUnit.SECONDS);

    } catch (Exception e) {
      SonarLintConsole.get(project).error("Cannot get rule description", e);
      nothingToDisplay(true);
    }
  }

  private void displayLoadingMessage() {
    editor = null;
    panel.removeAll();

    var txt = "Loading rule description...";

    var titleComp = new JBLabel(txt, SwingConstants.CENTER);
    panel.add(titleComp, BorderLayout.CENTER);
    panel.revalidate();
  }

  private void nothingToDisplay(boolean error) {
    editor = null;
    panel.removeAll();

    var txt = error ? "Couldn't find the rule description" : "Select an issue to display the rule description";

    var titleComp = new JBLabel(txt, SwingConstants.CENTER);
    panel.add(titleComp, BorderLayout.CENTER);
    panel.revalidate();
  }

  private void updateEditor(String text, String ruleKey) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (editor == null) {
      panel.removeAll();
      editor = createEditor();
      panel.add(editor, BorderLayout.CENTER);
    }
    ruleDescriptionHyperLinkListener.setRuleKey(ruleKey);

    SwingHelper.setHtml(editor, text, UIUtil.getLabelForeground());
    editor.setCaretPosition(0);
    panel.revalidate();
  }

  private JEditorPane createEditor() {
    var newEditor = new JEditorPane();
    newEditor.setEditorKit(new RuleDescriptionHTMLEditorKit());
    newEditor.setBorder(JBUI.Borders.empty(10));
    newEditor.setEditable(false);
    newEditor.setContentType(UIUtil.HTML_MIME);
    ruleDescriptionHyperLinkListener = new RuleDescriptionHyperLinkListener(project);
    newEditor.addHyperlinkListener(ruleDescriptionHyperLinkListener);
    return newEditor;
  }

  private static class RuleDescriptionHyperLinkListener extends BrowserHyperlinkListener {
    private final Project project;
    private String ruleKey;

    public RuleDescriptionHyperLinkListener(Project project) {
      this.project = project;
    }

    public void setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
    }

    @Override
    public void hyperlinkActivated(HyperlinkEvent e) {
      if (e.getDescription().startsWith("#rule")) {
        openRuleSettings(ruleKey);
        return;
      }
      super.hyperlinkActivated(e);
    }

    private void openRuleSettings(String ruleKey) {
      var configurable = new SonarLintGlobalConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.selectRule(ruleKey));
    }
  }

  public JComponent getPanel() {
    return panel;
  }

  public void show() {
    panel.setVisible(true);
  }

}
