/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.util.ui.UIUtil;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.icons.DisplayedStatus;
import org.sonarlint.intellij.ui.icons.FindingIconBuilder;
import org.sonarlint.intellij.ui.icons.SonarLintIcons;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.DateUtils;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality;

import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;
import static org.sonarlint.intellij.common.ui.ReadActionUtils.runReadActionSafely;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class IssueNode extends FindingNode {
  private static final SimpleTextAttributes GRAYED_SMALL_ATTRIBUTES = new SimpleTextAttributes(STYLE_SMALLER,
    UIUtil.getInactiveTextColor());

  private final LiveIssue issue;

  public IssueNode(LiveIssue issue) {
    super(issue);
    this.issue = issue;
  }

  private Optional<ServerConnection> retrieveServerConnection() {
    var project = issue.project();
    if (!project.isDisposed()) {
      return getService(project, ProjectBindingManager.class).tryGetServerConnection();
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    runReadActionSafely(issue.project(), () -> doRender(renderer));
  }

  private void doRender(TreeCellRenderer renderer) {
    var highestImpact = issue.getHighestImpact();
    var highestQuality = issue.getHighestQuality();

    if (issue.isMqrMode() && issue.getCleanCodeAttribute() != null && highestQuality != null && highestImpact != null) {
      renderWithMqrMode(renderer, highestImpact, highestQuality);
    } else {
      renderWithStandardMode(renderer);
    }

    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);
    renderMessage(renderer);
    issue.context().ifPresent(context -> renderer.append(context.getSummaryDescription(), GRAYED_SMALL_ATTRIBUTES));
    renderIntroductionDate(renderer);
    renderer.append("  " + issue.getRuleKey(), GRAYED_SMALL_ATTRIBUTES);
  }

  private void renderWithMqrMode(TreeCellRenderer renderer,
    ImpactSeverity highestImpact, SoftwareQuality highestQuality) {
    var impactText = StringUtil.capitalize(highestImpact.toString().toLowerCase(Locale.ENGLISH));
    var qualityText = StringUtil.capitalize(highestQuality.toString().toLowerCase(Locale.ENGLISH));
    var icon = SonarLintIcons.impact(highestImpact);
    var tooltip = impactText + " impact on " + qualityText;
    render(renderer, tooltip, icon);
  }

  private void renderWithStandardMode(TreeCellRenderer renderer) {
    var severity = issue.getUserSeverity();
    var type = issue.getType();
    Icon icon = null;
    var typeStr = "";
    var severityText = "";
    if (severity != null && type != null) {
      icon = SonarLintIcons.getIconForTypeAndSeverity(type, severity);
      typeStr = type.toString().replace('_', ' ').toLowerCase(Locale.ENGLISH);
      severityText = StringUtil.capitalize(severity.toString().toLowerCase(Locale.ENGLISH));
    }
    var tooltip = severityText + " " + typeStr;
    render(renderer, tooltip, icon);
  }

  private void render(TreeCellRenderer renderer, String baseTooltip, @Nullable Icon icon) {
    var serverConnection = retrieveServerConnection();
    var tooltip = baseTooltip;
    Icon productIcon = null;
    if (issue.getServerKey() != null && serverConnection.isPresent()) {
      var connection = serverConnection.get();
      tooltip += " already detected by " + connection.getProductName() + " analysis";
      productIcon = connection.getProductIcon();
    }

    var compoundIcon = FindingIconBuilder.forBaseIcon(icon)
      .withDecoratingIcon(productIcon)
      .withAiCodeFix(issue.isAiCodeFixable())
      .withDisplayedStatus(DisplayedStatus.fromFinding(issue))
      .build();

    renderer.setIconToolTip(tooltip);
    renderer.setIcon(compoundIcon);
  }

  private void renderMessage(TreeCellRenderer renderer) {
    String tooltip;
    SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (!issue.isValid()) {
      tooltip = "Issue is no longer valid";
      attributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
    } else {
      tooltip = "Double click to open location";
      if (issue.isResolved()) {
        attributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
      }
    }
    renderer.setToolTipText(tooltip);
    renderer.append(issue.getMessage(), attributes);
  }

  private void renderIntroductionDate(TreeCellRenderer renderer) {
    var introductionDate = issue.getIntroductionDate();
    if (introductionDate != null) {
      renderer.append(" ");
      var age = DateUtils.toAge(introductionDate.toEpochMilli());
      renderer.append(age, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public LiveIssue issue() {
    return issue;
  }

  private String issueCoordinates(LiveIssue issue) {
    return formatRangeMarker(issue.file(), issue.getRange());
  }

  @Override
  public String toString() {
    return issue.getMessage();
  }
}
