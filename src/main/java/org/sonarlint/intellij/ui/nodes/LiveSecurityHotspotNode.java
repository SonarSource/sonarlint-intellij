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
import com.intellij.ui.SimpleTextAttributes;
import java.util.Locale;
import javax.swing.Icon;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.icons.DisplayedStatus;
import org.sonarlint.intellij.ui.icons.FindingIconBuilder;
import org.sonarlint.intellij.ui.icons.SonarLintIcons;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class LiveSecurityHotspotNode extends FindingNode {
  private final LiveSecurityHotspot securityHotspot;
  private final boolean appendFileName;

  public LiveSecurityHotspotNode(LiveSecurityHotspot securityHotspot, boolean appendFileName) {
    super(securityHotspot);
    this.securityHotspot = securityHotspot;
    this.appendFileName = appendFileName;
    setUserObject(securityHotspot);
  }

  public LiveSecurityHotspot getHotspot() {
    return securityHotspot;
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    var vulnerability = securityHotspot.getVulnerabilityProbability();
    var vulnerabilityText = StringUtil.capitalize(vulnerability.toString().toLowerCase(Locale.ENGLISH));
    var type = securityHotspot.getType();
    var typeStr = type.toString().replace('_', ' ').toLowerCase(Locale.ENGLISH);

    Icon productIcon = null;
    var tooltip = vulnerabilityText + " " + typeStr;
    var serverConnection = getService(securityHotspot.project(), ProjectBindingManager.class).tryGetServerConnection();
    if (securityHotspot.getServerKey() != null && serverConnection.isPresent()) {
      productIcon = serverConnection.get().getProductIcon();
      tooltip += " existing on " + serverConnection.get().getProductName();
    }
    var typeIcon = SonarLintIcons.hotspotTypeWithProbability(vulnerability);
    var displayedStatus = DisplayedStatus.fromFinding(securityHotspot);
    var compoundIcon = FindingIconBuilder.forBaseIcon(typeIcon)
      .withDecoratingIcon(productIcon)
      .withDisplayedStatus(displayedStatus)
      .build();

    renderer.setIcon(compoundIcon);
    renderer.setIconToolTip(tooltip);

    renderer.setToolTipText("Double click to open location");
    if (displayedStatus == DisplayedStatus.OPEN) {
      renderer.append(securityHotspot.getMessage());
    } else {
      renderer.append(securityHotspot.getMessage(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    if (appendFileName) {
      renderer.append(" " + securityHotspot.file().getName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    renderer.append("  " + securityHotspot.getRuleKey(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
  }

  @Override
  public String toString() {
    return securityHotspot.getMessage();
  }

}
