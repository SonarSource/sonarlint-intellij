/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBHtmlEditorKit;
import com.intellij.util.ui.UIUtil;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;

public class RuleDescriptionHTMLEditorKit extends JBHtmlEditorKit {

  public RuleDescriptionHTMLEditorKit() {
    var styleSheet = this.getStyleSheet();
    styleSheet.addRule("td {align:center;}");
    styleSheet.addRule("td.pad {padding: 0px 10px 0px 0px;}");
    styleSheet.addRule("pre {padding: 10px;}");
    styleSheet.addRule(".rule-params { border: none; border-collapse: collapse; padding: 1em }");
    styleSheet.addRule(".rule-params caption { text-align: left }");
    styleSheet.addRule(".rule-params .thead td { padding-left: 0; padding-bottom: 1em; font-style: italic }");
    styleSheet.addRule(".rule-params .tbody th { text-align: right; font-weight: normal; font-family: monospace }");
    styleSheet.addRule(".rule-params .tbody td { margin-left: 1em; padding-bottom: 1em; }");
    styleSheet.addRule(".rule-params p { margin: 0 }");
    styleSheet.addRule(".rule-params small { display: block; margin-top: 2px }");
  }

}
