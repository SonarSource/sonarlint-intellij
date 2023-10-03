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
import com.intellij.ui.OffsetIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.swing.Icon;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.CompoundIcon;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class IssueNode extends FindingNode {
  // not available in IJ15
  private static final SimpleTextAttributes GRAYED_SMALL_ATTRIBUTES = new SimpleTextAttributes(STYLE_SMALLER, UIUtil.getInactiveTextColor());

  private final LiveIssue issue;

  public IssueNode(Trackable issue) {
    super((LiveIssue) issue);
    this.issue = ((LiveIssue) issue);
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    var gap = JBUIScale.isUsrHiDPI() ? 8 : 4;
    var serverConnection = getService(issue.psiFile().getProject(), ProjectBindingManager.class).tryGetServerConnection();

    if (issue.getCleanCodeAttribute() != null && !issue.getImpacts().isEmpty()) {
      var highestQualityImpact = Collections.max(issue.getImpacts().entrySet(), Map.Entry.comparingByValue(Comparator.comparing(Enum::ordinal)));
      var impactText = StringUtil.capitalize(highestQualityImpact.getValue().toString().toLowerCase(Locale.ENGLISH));
      var qualityText = StringUtil.capitalize(highestQualityImpact.getKey().toString().toLowerCase(Locale.ENGLISH));
      var impactIcon = SonarLintIcons.impact(highestQualityImpact.getValue());

      if (issue.getServerFindingKey() != null && serverConnection.isPresent()) {
        var connection = serverConnection.get();
        renderer.setIconToolTip(impactText + " impact on " + qualityText + " already detected by " + connection.getProductName() + " analysis");
        setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, connection.getProductIcon(), impactIcon));
      } else {
        renderer.setIconToolTip(impactText + " impact on " + qualityText);
        var serverIconEmptySpace = SonarLintIcons.ICON_SONARQUBE_16.getIconWidth() + gap;
        setIcon(renderer, new OffsetIcon(serverIconEmptySpace, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, impactIcon)));
      }
    } else {
      var severity = issue.getUserSeverity();
      var severityText = "";
      Icon severityIcon = null;
      if (severity != null) {
        severityText = StringUtil.capitalize(severity.toString().toLowerCase(Locale.ENGLISH));
        severityIcon = SonarLintIcons.severity(severity);
      }
      var type = issue.getType();
      var typeIcon = SonarLintIcons.type(type);
      var typeStr = type.toString().replace('_', ' ').toLowerCase(Locale.ENGLISH);

      if (issue.getServerFindingKey() != null && serverConnection.isPresent()) {
        var connection = serverConnection.get();
        renderer.setIconToolTip(severityText + " " + typeStr + " already detected by " + connection.getProductName() + " analysis");
        setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, connection.getProductIcon(), typeIcon, severityIcon));
      } else {
        renderer.setIconToolTip(severityText + " " + typeStr);
        var serverIconEmptySpace = SonarLintIcons.ICON_SONARQUBE_16.getIconWidth() + gap;
        setIcon(renderer, new OffsetIcon(serverIconEmptySpace, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, typeIcon, severityIcon)));
      }
    }

    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);

    renderMessage(renderer);

    issue.context().ifPresent(context -> renderer.append(context.getSummaryDescription(), GRAYED_SMALL_ATTRIBUTES));

    var introductionDate = issue.getIntroductionDate();
    if (introductionDate != null) {
      renderer.append(" ");
      var age = DateUtils.toAge(introductionDate);
      renderer.append(age, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private void renderMessage(TreeCellRenderer renderer) {
    if (issue.isValid()) {
      renderer.setToolTipText("Double click to open location");
      if (issue.isResolved()) {
        renderer.append(issue.getMessage(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null));
      } else {
        renderer.append(issue.getMessage());
      }
    } else {
      renderer.setToolTipText("Issue is no longer valid");
      if (issue.isResolved()) {
        renderer.append(issue.getMessage(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, UIUtil.getInactiveTextColor()));
      } else {
        renderer.append(issue.getMessage(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
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

  private String issueCoordinates(@Nonnull LiveIssue issue) {
    var range = issue.getRange();
    return formatRangeMarker(range);
  }

  @Override
  public String toString() {
    return issue.getMessage();
  }
}
