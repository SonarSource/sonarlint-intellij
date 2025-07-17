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
package org.sonarlint.intellij.ui.tree;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.event.MouseEvent;
import javax.swing.JTree;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

/**
 * Can't unit test this because the parent uses a service, depending on a pico container with a method
 * that doesn't exist in the pico container used by SonarLint (different versions), causing NoSuchMethodError.
 */
public class TreeCellRenderer extends ColoredTreeCellRenderer {
  private final NodeRenderer<Object> nodeRenderer;

  public TreeCellRenderer() {
    nodeRenderer = null;
  }

  public TreeCellRenderer(NodeRenderer<Object> nodeRenderer) {
    this.nodeRenderer = nodeRenderer;
  }

  private String iconToolTip = null;

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (nodeRenderer != null) {
      nodeRenderer.render(this, value);
    } else {
      var node = (AbstractNode) value;
      node.render(this);
      // --- Modern UI polish ---
      // Issue node
      if (node instanceof org.sonarlint.intellij.ui.nodes.IssueNode issueNode) {
        LiveIssue issue = issueNode.issue();
        var severity = issue.getUserSeverity();
        var impact = issue.getHighestImpact();
        if (severity != null && issue.getType() != null) {
          setIcon(SonarLintIcons.getIconForTypeAndSeverity(issue.getType(), severity));
          // Badge for critical/blocker
          if (severity == IssueSeverity.BLOCKER || severity == IssueSeverity.CRITICAL) {
            append("  0", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getErrorForeground()));
          }
        } else if (impact != null) {
          setIcon(SonarLintIcons.impact(impact));
          // Badge for critical/blocker/high
          if (impact == org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.BLOCKER || impact == org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.HIGH) {
            append("  0", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getErrorForeground()));
          }
        } else {
          setIcon(null); // fallback
        }
        // Subtitle: rule key
        append("  " + issue.getRuleKey(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
      } else if (node instanceof org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode hsNode) {
        LiveSecurityHotspot hs = hsNode.getHotspot();
        setIcon(SonarLintIcons.hotspotTypeWithProbability(hs.getVulnerabilityProbability()));
        append("  " + hs.getRuleKey(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
      } else if (value instanceof LocalTaintVulnerability taint) {
        var severity = taint.severity();
        var impact = taint.getHighestImpact();
        if (severity != null && taint.getType() != null) {
          setIcon(SonarLintIcons.getIconForTypeAndSeverity(taint.getType(), severity));
          if (severity == IssueSeverity.BLOCKER || severity == IssueSeverity.CRITICAL) {
            append("  0", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getErrorForeground()));
          }
        } else if (impact != null) {
          setIcon(SonarLintIcons.impact(impact));
          if (impact == org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.BLOCKER || impact == org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.HIGH) {
            append("  0", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getErrorForeground()));
          }
        } else {
          setIcon(AllIcons.Ide.Dislike); // fallback
        }
        append("  " + taint.getRuleKey(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
      }
      // Add spacing
      setIpad(JBUI.insets(4));
    }
  }

  public void setIconToolTip(String toolTip) {
    this.iconToolTip = toolTip;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (iconToolTip == null) {
      return super.getToolTipText(event);
    }

    if (event.getX() < getIconWidth()) {
      return iconToolTip;
    }

    return super.getToolTipText(event);
  }

  private int getIconWidth() {
    if (getIcon() != null) {
      return getIcon().getIconWidth() + myIconTextGap;
    }
    return 0;
  }
}
