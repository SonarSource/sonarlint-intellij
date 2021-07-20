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
import com.intellij.openapi.project.Project;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Desktop;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.ui.HtmlUtils.fixPreformattedText;

public class SonarLintHotspotDescriptionPanel {
  private static final int BORDER = 10;

  private final Project project;
  private final JPanel panel;
  private final HTMLEditorKit kit;
  private JEditorPane editor;

  public SonarLintHotspotDescriptionPanel(Project project) {
    this.project = project;
    this.kit = new HTMLEditorKit();
    StyleSheet styleSheet = kit.getStyleSheet();
    styleSheet.addRule("td {align:center;}");
    styleSheet.addRule("td.pad {padding: 0px 10px 0px 0px;}");

    panel = new JPanel(new BorderLayout());

    JComponent titleComp = new JLabel("Select a hotspot to see more details", SwingConstants.CENTER);
    panel.add(titleComp, BorderLayout.CENTER);
  }

  public void setDescription(String description) {
    String htmlBody = fixPreformattedText("<table><tr><td>" + description + "</td></tr></table>");
    updateEditor(htmlBody);
  }

  private void updateEditor(String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (editor == null) {
      panel.removeAll();
      editor = createEditor();
      panel.add(editor, BorderLayout.CENTER);
    }

    SwingHelper.setHtml(editor, text, UIUtil.getLabelForeground());
    editor.setCaretPosition(0);
    panel.revalidate();
  }

  private JEditorPane createEditor() {
    JEditorPane newEditor = new JEditorPane();
    newEditor.setEditorKit(kit);
    newEditor.setBorder(JBUI.Borders.empty(BORDER));
    newEditor.setEditable(false);
    newEditor.setContentType(UIUtil.HTML_MIME);
    newEditor.addHyperlinkListener(new BrowserHyperlinkListener());
    return newEditor;
  }

  public JComponent getPanel() {
    return panel;
  }

}
