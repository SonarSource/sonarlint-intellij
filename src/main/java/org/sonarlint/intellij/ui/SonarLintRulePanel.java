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
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;

import org.apache.commons.lang.StringEscapeUtils;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.ui.ruledescription.RuleDescriptionHTMLEditorKit;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.ui.HtmlUtils.fixPreformattedText;
import static org.sonarlint.intellij.ui.ruledescription.RuleDescriptionHTMLEditorKit.appendRuleAttributesHtmlTable;

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
    setRuleKey(null,null);
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
    try {
      SonarLintFacade facade = SonarLintUtils.getService(project, ProjectBindingManager.class).getFacade(module);
      RuleDetails rule = facade.getActiveRuleDetails(ruleKey);

      String description = facade.getDescription(ruleKey);
      if (rule == null || description == null) {
        nothingToDisplay(true);
        return;
      }

      StringBuilder builder = new StringBuilder(description.length() + 64);
      builder.append("<h2>")
        .append(StringEscapeUtils.escapeHtml(rule.getName()))
        .append("</h2>");
      appendRuleAttributesHtmlTable(rule.getKey(), rule.getSeverity(), rule.getType(), builder);
      builder.append("<br />")
        .append(description);
      if (rule instanceof StandaloneRuleDetails) {
        StandaloneRuleDetails standaloneRuleDetails = (StandaloneRuleDetails) rule;
        if (!standaloneRuleDetails.paramDetails().isEmpty()) {
          builder.append(renderRuleParams(standaloneRuleDetails));
        }
      }
      String htmlBody = fixPreformattedText(builder.toString());

      updateEditor(htmlBody, rule.getKey());
    } catch (InvalidBindingException e) {
      nothingToDisplay(true);
    }

  }

  private void nothingToDisplay(boolean error) {
    editor = null;
    panel.removeAll();

    String txt;
    if (error) {
      txt = "Couldn't find an extended description for the rule";
    } else {
      txt = "Select an issue to see extended rule description";
    }

    JComponent titleComp = new JBLabel(txt, SwingConstants.CENTER);
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
    JEditorPane newEditor = new JEditorPane();
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
      SonarLintGlobalConfigurable configurable = new SonarLintGlobalConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.selectRule(ruleKey));
    }
  }

  private static String renderRuleParams(StandaloneRuleDetails ruleDetails) {
    return "<table class=\"rule-params\">" +
      "<caption><h2>Parameters</h2></caption>" +
      "<tr class='thead'>" +
      "<td colspan=\"2\">" +
      "Following parameter values can be set in <a href=\"#rule\">Rule Settings</a>. " +
      "In connected mode, server side configuration overrides local settings." +
      "</td>" +
      "</tr>" +
      ruleDetails.paramDetails().stream().map(param -> renderRuleParam(param, ruleDetails)).collect(Collectors.joining("\n")) +
      "</table>";
  }

  private static String renderRuleParam(StandaloneRuleParam param, StandaloneRuleDetails ruleDetails) {
    String paramDescription = param.description() != null ? ("<p>" + param.description() + "</p>") : "";
    String paramDefaultValue = param.defaultValue();
    String defaultValue = paramDefaultValue != null ? paramDefaultValue : "(none)";
    String currentValue = getGlobalSettings().getRuleParamValue(ruleDetails.getKey(), param.name()).orElse(defaultValue);
    return "<tr class='tbody'>" +
    // The <br/> elements are added to simulate a "vertical-align: top" (not supported by Java 11 CSS renderer)
      "<th>" + param.name() + "<br/><br/></th>" +
      "<td>" +
      paramDescription +
      "<p><small>Current value: <code>" + currentValue + "</code></small></p>" +
      "<p><small>Default value: <code>" + defaultValue + "</code></small></p>" +
      "</td>" +
      "</tr>";
  }

  public JComponent getPanel() {
    return panel;
  }

  public void show() {
    panel.setVisible(true);
  }

}
