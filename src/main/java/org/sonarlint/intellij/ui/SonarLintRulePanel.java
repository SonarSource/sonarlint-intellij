/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import icons.SonarLintIcons;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Image;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
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
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintRulePanel {
  private static final Pattern SPACES_BEGINNING_LINE = Pattern.compile("\n(\\p{Blank}*)");
  private final Project project;
  private final JPanel panel;
  private final HTMLEditorKit kit;
  private JEditorPane editor;
  private RuleDescriptionHyperLinkListener ruleDescriptionHyperLinkListener;

  public SonarLintRulePanel(Project project) {
    this.project = project;
    this.kit = new CustomHTMLEditorKit();
    StyleSheet styleSheet = kit.getStyleSheet();
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
      try {
        SonarLintFacade facade = SonarLintUtils.getService(project, ProjectBindingManager.class).getFacade();
        RuleDetails rule = facade.getActiveRuleDetails(issue.getRuleKey());
        String description = facade.getDescription(issue.getRuleKey());
        if (rule == null || description == null) {
          nothingToDisplay(true);
          return;
        }

        StringBuilder builder = new StringBuilder(description.length() + 64);
        builder.append("<h2>")
          .append(StringEscapeUtils.escapeHtml(issue.getRuleName()))
          .append("</h2>");
        createTable(rule, builder);
        builder.append("<br />")
          .append(description);
        if (rule instanceof StandaloneRuleDetails) {
          builder.append(renderRuleParams((StandaloneRuleDetails) rule));
        }
        String htmlBody = builder.toString();
        htmlBody = fixPreformatedText(htmlBody);

        updateEditor(htmlBody, rule.getKey());
      } catch (InvalidBindingException e) {
        nothingToDisplay(true);
      }
    }
  }

  private static void createTable(RuleDetails rule, StringBuilder builder) {
    // apparently some css properties are not supported
    String imgAttributes = "valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\"";

    builder.append("<table><tr>");
    if (rule.getType() != null) {
      builder.append("<td>").append("<img ").append(imgAttributes).append(" src=\"file:///type/").append(rule.getType()).append("\"/></td>")
        .append("<td class=\"pad\"><b>").append(clean(rule.getType())).append("</b></td>");
    }
    builder.append("<td>").append("<img ").append(imgAttributes).append(" src=\"file:///severity/").append(rule.getSeverity()).append("\"/></td>")
      .append("<td class=\"pad\"><b>").append(clean(rule.getSeverity())).append("</b></td>")
      .append("<td><b>").append(rule.getKey()).append("</b></td>")
      .append("</tr></table>");
  }

  private static String clean(String txt) {
    return StringUtil.capitalize(txt.toLowerCase(Locale.ENGLISH).replace("_", " "));
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
    newEditor.setEditorKit(kit);
    newEditor.setBorder(JBUI.Borders.empty(10));
    newEditor.setEditable(false);
    newEditor.setContentType("text/html");
    ruleDescriptionHyperLinkListener = new RuleDescriptionHyperLinkListener(project);
    newEditor.addHyperlinkListener(ruleDescriptionHyperLinkListener);
    return newEditor;
  }

  private static class RuleDescriptionHyperLinkListener implements HyperlinkListener {
    private final Project project;
    private String ruleKey;

    public RuleDescriptionHyperLinkListener(Project project) {
      this.project = project;
    }

    public void setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
        if (e.getDescription().startsWith("#rule")) {
          openRuleSettings(ruleKey);
          return;
        }
        Desktop desktop = Desktop.getDesktop();
        try {
          desktop.browse(e.getURL().toURI());
        } catch (Exception ex) {
          SonarLintConsole.get(project).error("Error opening browser: " + e.getURL(), ex);
        }
      }
    }

    private void openRuleSettings(String ruleKey) {
      SonarLintGlobalConfigurable configurable = new SonarLintGlobalConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.selectRule(ruleKey));
    }
  }

  /**
   * Unfortunately it looks like the default html editor kit doesn't support CSS related to white-space. Therefore,
   * all text within the pre tags doesn't wrap.
   * So we replace all 'pre' tags by 'div' tags with font monospace.
   * In the preformated text, we replace '\n' by the 'br' tag, and all the spaces in the beginning of each line by
   * the non-breaking space 'nbsp'.
   */
  private static String fixPreformatedText(String htmlBody) {
    StringBuilder builder = new StringBuilder();
    int current = 0;

    while (true) {
      int start = htmlBody.indexOf("<pre>", current);
      if (start < 0) {
        break;
      }

      int end = htmlBody.indexOf("</pre>", start);

      if (end < 0) {
        break;
      }

      builder.append(htmlBody.substring(current, start));
      builder.append("<div style=\"font-family: monospace\">");
      String preformated = htmlBody.substring(start + 5, end);

      Matcher m = SPACES_BEGINNING_LINE.matcher(preformated);
      int previous = 0;
      while (m.find()) {
        String replacement = "<br/>" + StringUtil.repeat("&nbsp;", m.group().length());
        builder.append(preformated.substring(previous, m.start()));
        builder.append(replacement);
        previous = m.end();
      }
      builder.append(preformated.substring(previous));
      builder.append("</div>");
      current = end + 6;
    }

    builder.append(htmlBody.substring(current));
    return builder.toString();
  }

  private String renderRuleParams(StandaloneRuleDetails ruleDetails) {
    if (!ruleDetails.paramDetails().isEmpty()) {
      StyleSheet styleSheet = kit.getStyleSheet();

      styleSheet.addRule(".rule-params { border: none; border-collapse: collapse; padding: 1em }");
      styleSheet.addRule(".rule-params caption { text-align: left }");
      styleSheet.addRule(".rule-params .thead td { padding-left: 0; padding-bottom: 1em; font-style: italic }");
      styleSheet.addRule(".rule-params .tbody th { text-align: right; font-weight: normal; font-family: monospace }");
      styleSheet.addRule(".rule-params .tbody td { margin-left: 1em; padding-bottom: 1em; }");
      styleSheet.addRule(".rule-params p { margin: 0 }");
      styleSheet.addRule(".rule-params small { display: block; margin-top: 2px }");

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
    } else {
      return "";
    }
  }

  private static String renderRuleParam(StandaloneRuleParam param, StandaloneRuleDetails ruleDetails) {
    String paramDescription = param.description() != null ? "<p>" + param.description() + "</p>" : "";
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

  public static class CustomHTMLEditorKit extends HTMLEditorKit {
    private static final HTMLFactory factory = new HTMLFactory() {
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

    @Override
    public ViewFactory getViewFactory() {
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
        String severity = url.getPath().substring(10);
        icon = SonarLintIcons.severity(severity);
      } else if (path.startsWith("/type/")) {
        String type = url.getPath().substring(6);
        icon = SonarLintIcons.type(type);
      } else {
        return;
      }

      // in presentation mode we don't want huge icons
      if (JBUI.isUsrHiDPI()) {
        icon = IconUtil.scale(icon, null, 0.5f);
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
