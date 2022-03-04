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
package org.sonarlint.intellij.ui.ruledescription;

import com.intellij.openapi.util.text.StringUtil;

import java.util.Locale;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.jetbrains.annotations.Nullable;

public class RuleDescriptionHTMLEditorKit extends HTMLEditorKit {

  public RuleDescriptionHTMLEditorKit() {
    StyleSheet styleSheet = this.getStyleSheet();
    styleSheet.addRule("td {align:center;}");
    styleSheet.addRule("td.pad {padding: 0px 10px 0px 0px;}");
    styleSheet.addRule(".rule-params { border: none; border-collapse: collapse; padding: 1em }");
    styleSheet.addRule(".rule-params caption { text-align: left }");
    styleSheet.addRule(".rule-params .thead td { padding-left: 0; padding-bottom: 1em; font-style: italic }");
    styleSheet.addRule(".rule-params .tbody th { text-align: right; font-weight: normal; font-family: monospace }");
    styleSheet.addRule(".rule-params .tbody td { margin-left: 1em; padding-bottom: 1em; }");
    styleSheet.addRule(".rule-params p { margin: 0 }");
    styleSheet.addRule(".rule-params small { display: block; margin-top: 2px }");
  }

  public static void appendRuleAttributesHtmlTable(String ruleKey, String ruleSeverity, @Nullable String ruleType, StringBuilder builder) {
    // apparently some css properties are not supported
    String imgAttributes = "valign=\"top\" hspace=\"3\" height=\"16\" width=\"16\"";

    builder.append("<table><tr>");
    if (ruleType != null) {
      builder.append("<td>").append("<img ").append(imgAttributes).append(" src=\"file:///type/").append(ruleType).append("\"/></td>")
        .append("<td class=\"pad\"><b>").append(clean(ruleType)).append("</b></td>");
    }
    builder.append("<td>").append("<img ").append(imgAttributes).append(" src=\"file:///severity/").append(ruleSeverity).append("\"/></td>")
      .append("<td class=\"pad\"><b>").append(clean(ruleSeverity)).append("</b></td>")
      .append("<td><b>").append(ruleKey).append("</b></td>")
      .append("</tr></table>");
  }

  private static String clean(String txt) {
    return StringUtil.capitalize(txt.toLowerCase(Locale.ENGLISH).replace("_", " "));
  }

  private static final HTMLFactory factory = new HTMLFactory() {
    @Override
    public View create(Element elem) {
      AttributeSet attrs = elem.getAttributes();
      Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
      Object o = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
      if (o instanceof HTML.Tag) {
        HTML.Tag kind = (HTML.Tag) o;
        if (HTML.Tag.IMG.equals(kind)) {
          return new RuleDescriptionImageView(elem);
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
