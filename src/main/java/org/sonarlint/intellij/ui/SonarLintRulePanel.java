/*
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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import icons.SonarLintIcons;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Image;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;
import org.apache.commons.lang.StringEscapeUtils;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintRulePanel {
  private final Project project;
  private final ProjectBindingManager projectBindingManager;
  private final JPanel panel;
  private final HTMLEditorKit kit;
  private JEditorPane editor;

  public SonarLintRulePanel(Project project, ProjectBindingManager projectBindingManager) {
    this.project = project;
    this.projectBindingManager = projectBindingManager;
    this.kit = new CustomHTMLEditorKit();
    StyleSheet styleSheet = kit.getStyleSheet();
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    String fontName = scheme.getFontPreferences().getFontFamily();
    styleSheet.addRule("body {font-family:" + fontName + ";}");
    styleSheet.addRule("td {align:center;}");
    styleSheet.addRule("td.pad {padding: 0px 10px 0px 0px;}");

    panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    setRuleKey(null);
    show();
  }

  public void setRuleKey(@Nullable LiveIssue issue) {
    if (issue == null) {
      nothingToDisplay(false);
    } else {

      String description = projectBindingManager.getFacadeForAnalysis().getDescription(issue.getRuleKey());
      if (description == null) {
        nothingToDisplay(true);
        return;
      }

      StringBuilder builder = new StringBuilder(description.length() + 64);
      builder.append("<h2>")
        .append(StringEscapeUtils.escapeHtml(issue.getRuleName()))
        .append("</h2>");
      createTable(issue, builder);
      builder.append("<br />")
        .append(description);
      updateEditor(builder.toString());
    }
  }

  private void createTable(LiveIssue issue, StringBuilder builder) {
    // apparently some css properties are not supported
    String imgAttributes = "valign=\"top\" hspace=\"3\"";

    builder.append("<table><tr>");
    if (issue.getType() != null) {
      builder.append("<td>").append("<img " + imgAttributes + " src=\"file:///type/").append(issue.getType()).append("\"/></td>")
        .append("<td class=\"pad\"><b>").append(clean(issue.getType())).append("</b></td>");
    }
    builder.append("<td>").append("<img " + imgAttributes + " src=\"file:///severity/").append(issue.getSeverity()).append("\"/></td>")
      .append("<td class=\"pad\"><b>").append(clean(issue.getSeverity())).append("</b></td>")
      .append("<td><b>").append(issue.getRuleKey()).append("</b></td>")
      .append("</tr></table>");
  }

  private static String clean(String txt) {
    return StringUtil.capitalize(txt.toLowerCase().replace("_", " "));
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

    JComponent titleComp = new JLabel(txt, SwingConstants.CENTER);
    panel.add(titleComp, BorderLayout.CENTER);
    panel.revalidate();
  }

  private void updateEditor(String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (editor == null) {
      panel.removeAll();
      editor = createEditor();
      panel.add(editor, BorderLayout.CENTER);
    }

    editor.setText(text);
    editor.setCaretPosition(0);
    panel.revalidate();
  }

  private JEditorPane createEditor() {
    JEditorPane newEditor = new JEditorPane();
    newEditor.setEditorKit(kit);
    newEditor.setBorder(new EmptyBorder(10, 10, 10, 10));
    newEditor.setEditable(false);
    newEditor.setContentType("text/html");
    newEditor.addHyperlinkListener(e -> {
      if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
        Desktop desktop = Desktop.getDesktop();
        try {
          desktop.browse(e.getURL().toURI());
        } catch (Exception ex) {
          SonarLintConsole.get(project).error("Error opening browser: " + e.getURL(), ex);
        }
      }
    });

    return newEditor;
  }

  public JComponent getPanel() {
    return panel;
  }

  public void show() {
    panel.setVisible(true);
  }

  public static class CustomHTMLEditorKit extends HTMLEditorKit {
    private static HTMLFactory factory = null;

    @Override
    public ViewFactory getViewFactory() {
      if (factory == null) {
        factory = new HTMLFactory() {
          @Override
          public View create(Element elem) {
            AttributeSet attrs = elem.getAttributes();
            Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
            Object o = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
            if (o instanceof HTML.Tag) {
              HTML.Tag kind = (HTML.Tag) o;
              if (HTML.Tag.IMG.equals(kind)) {
                return new CustomImageView(elem);
              }
            }
            return super.create(elem);
          }
        };
      }
      return factory;
    }
  }

  public static class CustomImageView extends ImageView {
    public CustomImageView(Element elem) {
      super(elem);
      cacheImage(getImageURL());
    }

    private void cacheImage(@Nullable URL url) {
      if (url == null) {
        return;
      }
      String path = url.getPath();
      if (path == null) {
        return;
      }

      Icon icon;
      if (path.startsWith("/severity/")) {
        String severity = url.getPath().substring(9);
        icon = SonarLintIcons.severity(severity);
      } else if (path.startsWith("/type/")) {
        String type = url.getPath().substring(6);
        icon = SonarLintIcons.type(type);
      } else {
        return;
      }

      Dictionary<URL, Image> cache = (Dictionary<URL, Image>) getDocument().getProperty("imageCache");
      if (cache == null) {
        cache = new Hashtable<>();
      }
      cache.put(url, SonarLintUtils.iconToImage(icon));
      getDocument().putProperty("imageCache", cache);
    }
  }
}
