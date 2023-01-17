/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.text.html.HTMLEditorKit;

public class SonarLintHotspotDescriptionPanel {
  private static final int BORDER = 10;

  private final JPanel panel;
  private final HTMLEditorKit kit;
  private JEditorPane editor;

  public SonarLintHotspotDescriptionPanel() {
    this.kit = new HTMLEditorKit();
    var styleSheet = kit.getStyleSheet();
    styleSheet.addRule("td {align:center;}");
    styleSheet.addRule("td.pad {padding: 0px 10px 0px 0px;}");
    styleSheet.addRule("pre {padding: 10px;}");

    panel = new JPanel(new BorderLayout());

    var titleComp = new JLabel("Select a hotspot to see more details", SwingConstants.CENTER);
    panel.add(titleComp, BorderLayout.CENTER);
  }

  public void setDescription(String description) {
    var htmlBody = "<table><tr><td>" + description + "</td></tr></table>";
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
    var newEditor = new JEditorPane();
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
