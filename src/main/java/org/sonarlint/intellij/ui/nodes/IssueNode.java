/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.swing.Icon;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.CompoundIcon;
import org.sonarlint.intellij.util.DateUtils;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality;

import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;
import static org.sonarlint.intellij.common.ui.ReadActionUtils.runReadActionSafely;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class IssueNode extends FindingNode {
  private static final SimpleTextAttributes GRAYED_SMALL_ATTRIBUTES = new SimpleTextAttributes(STYLE_SMALLER,
    UIUtil.getInactiveTextColor());
  private static final int GAP = JBUIScale.isUsrHiDPI() ? 8 : 4;
  private static final int SERVER_ICON_EMPTY_SPACE = SonarLintIcons.ICON_SONARQUBE_SERVER_16.getIconWidth() + GAP;

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
    var serverConnection = retrieveServerConnection();
    var highestImpact = issue.getHighestImpact();
    var highestQuality = issue.getHighestQuality();

    if (issue.getCleanCodeAttribute() != null && highestQuality != null && highestImpact != null) {
      renderWithMqrMode(renderer, serverConnection, highestImpact, highestQuality);
    } else {
      renderWithStandardMode(renderer, serverConnection);
    }

    renderer.append(issueCoordinates(issue), SimpleTextAttributes.GRAY_ATTRIBUTES);
    renderMessage(renderer);
    issue.context().ifPresent(context -> renderer.append(context.getSummaryDescription(), GRAYED_SMALL_ATTRIBUTES));
    renderIntroductionDate(renderer);
  }

  private void renderWithMqrMode(TreeCellRenderer renderer, Optional<ServerConnection> serverConnection,
    ImpactSeverity highestImpact, SoftwareQuality highestQuality) {
    var impactText = StringUtil.capitalize(highestImpact.toString().toLowerCase(Locale.ENGLISH));
    var qualityText = StringUtil.capitalize(highestQuality.toString().toLowerCase(Locale.ENGLISH));
    var impactIcon = SonarLintIcons.impact(highestImpact);

    if (issue.getServerKey() != null && serverConnection.isPresent()) {
      var connection = serverConnection.get();
      renderer.setIconToolTip(impactText + " impact on " + qualityText + " already detected by " + connection.getProductName() + " " +
        "analysis");
      if (issue.isAiCodeFixable()) {
        setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, connection.getProductIcon(), impactIcon, SonarLintIcons.SPARKLE_GUTTER_ICON));
      } else {
        setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, connection.getProductIcon(), impactIcon));
      }
    } else {
      renderer.setIconToolTip(impactText + " impact on " + qualityText);
      if (issue.isAiCodeFixable()) {
        setIcon(renderer, new OffsetIcon(SERVER_ICON_EMPTY_SPACE, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, impactIcon, SonarLintIcons.SPARKLE_GUTTER_ICON)));
      } else {
        setIcon(renderer, new OffsetIcon(SERVER_ICON_EMPTY_SPACE, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, impactIcon)));
      }
    }
  }

  private void renderWithStandardMode(TreeCellRenderer renderer, Optional<ServerConnection> serverConnection) {
    var severity = issue.getUserSeverity();
    var type = issue.getType();
    Icon typeIcon = null;
    var typeStr = "";
    var severityText = "";
    if (severity != null && type != null) {
      typeIcon = SonarLintIcons.getIconForTypeAndSeverity(type, severity);
      typeStr = type.toString().replace('_', ' ').toLowerCase(Locale.ENGLISH);
      severityText = StringUtil.capitalize(severity.toString().toLowerCase(Locale.ENGLISH));
    }

    if (issue.getServerKey() != null && serverConnection.isPresent()) {
      var connection = serverConnection.get();
      renderer.setIconToolTip(severityText + " " + typeStr + " already detected by " + connection.getProductName() + " analysis");
      if (issue.isAiCodeFixable()) {
        setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, connection.getProductIcon(), typeIcon, SonarLintIcons.SPARKLE_GUTTER_ICON));
      } else {
        setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, connection.getProductIcon(), typeIcon));
      }
    } else {
      renderer.setIconToolTip(severityText + " " + typeStr);
      if (issue.isAiCodeFixable()) {
        setIcon(renderer, new OffsetIcon(SERVER_ICON_EMPTY_SPACE, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, typeIcon, SonarLintIcons.SPARKLE_GUTTER_ICON)));
      } else {
        setIcon(renderer, new OffsetIcon(SERVER_ICON_EMPTY_SPACE, new CompoundIcon(CompoundIcon.Axis.X_AXIS, GAP, typeIcon)));
      }
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

  private void renderIntroductionDate(TreeCellRenderer renderer) {
    var introductionDate = issue.getIntroductionDate();
    if (introductionDate != null) {
      renderer.append(" ");
      var age = DateUtils.toAge(introductionDate.toEpochMilli());
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

  private String issueCoordinates(@Nonnull LiveIssue issue) {
    return formatRangeMarker(issue.file(), issue.getRange());
  }

  @Override
  public String toString() {
    return issue.getMessage();
  }
}
