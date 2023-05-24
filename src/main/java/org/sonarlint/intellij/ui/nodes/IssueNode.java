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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import org.sonarlint.intellij.SonarLintIcons;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.swing.Icon;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.CompoundIcon;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;

public class IssueNode extends AbstractNode {
  // not available in IJ15
  private static final SimpleTextAttributes GRAYED_SMALL_ATTRIBUTES = new SimpleTextAttributes(STYLE_SMALLER, UIUtil.getInactiveTextColor());

  private final LiveIssue issue;

  public IssueNode(Trackable issue) {
    this.issue = ((LiveIssue) issue);
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    var severity = issue.getUserSeverity();
    var severityText = StringUtil.capitalize(severity.toString().toLowerCase(Locale.ENGLISH));
    var type = issue.getType();

    if (type != null) {
      var typeStr = type.toString().replace('_', ' ').toLowerCase(Locale.ENGLISH);
      renderer.setIconToolTip(severityText + " " + typeStr);
      var gap = JBUIScale.isUsrHiDPI() ? 8 : 4;
      setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, SonarLintIcons.type(type), SonarLintIcons.severity(severity)));
    } else {
      renderer.setIconToolTip(severityText);
      setIcon(renderer, SonarLintIcons.severity(severity));
    }

    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);

    if (issue.isValid()) {
      renderer.setToolTipText("Double click to open location");
      renderer.append(issue.getMessage());
    } else {
      renderer.setToolTipText("Issue is no longer valid");
      renderer.append(issue.getMessage(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    issue.context().ifPresent(context -> renderer.append(context.getSummaryDescription(), GRAYED_SMALL_ATTRIBUTES));

    var introductionDate = issue.getIntroductionDate();
    if (introductionDate != null) {
      renderer.append(" ");
      var age = DateUtils.toAge(introductionDate);
      renderer.append(age, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private void setIcon(TreeCellRenderer renderer, Icon icon) {
    if (issue.isValid()) {
      renderer.setIcon(icon);
    } else {
      renderer.setIcon(SonarLintIcons.toDisabled(icon));
    }
  }

  @Override
  public int getFindingCount() {
    return 1;
  }

  public LiveIssue issue() {
    return issue;
  }

  private static String issueCoordinates(@Nonnull LiveIssue issue) {
    var range = issue.getRange();
    if (range == null) {
      return "(0, 0) ";
    }

    if (!issue.isValid()) {
      return "(-, -) ";
    }

    var doc = range.getDocument();
    var line = doc.getLineNumber(range.getStartOffset());
    var offset = range.getStartOffset() - doc.getLineStartOffset(line);
    return String.format("(%d, %d) ", line + 1, offset);
  }

  @Override
  public String toString() {
    return issue.getMessage();
  }
}
