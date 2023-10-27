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
import java.util.Locale;
import javax.swing.Icon;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.CompoundIcon;

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

    var typeIcon = SonarLintIcons.hotspotTypeWithProbability(vulnerability);
    var gap = JBUIScale.isUsrHiDPI() ? 8 : 4;
    var serverConnection = getService(securityHotspot.project(), ProjectBindingManager.class).tryGetServerConnection();
    if (securityHotspot.getServerFindingKey() != null && serverConnection.isPresent()) {
      var productIcon = serverConnection.get().getProduct().getIcon();
      var tooltip = vulnerabilityText + " " + typeStr + " existing on " + serverConnection.get().getProductName();
      renderer.setIconToolTip(tooltip);
      setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, productIcon, typeIcon));
    } else {
      renderer.setIconToolTip(vulnerabilityText + " " + typeStr);
      setIcon(renderer, new OffsetIcon(typeIcon.getIconWidth() + gap, typeIcon));
    }

    renderer.setToolTipText("Double click to open location");
    if (securityHotspot.isResolved()) {
      renderer.append(securityHotspot.getMessage(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null));
    } else {
      renderer.append(securityHotspot.getMessage());
    }

    if (appendFileName) {
      renderer.append(" " + securityHotspot.file().getName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private void setIcon(TreeCellRenderer renderer, Icon icon) {
    if (securityHotspot.isValid()) {
      renderer.setIcon(icon);
    } else {
      renderer.setIcon(SonarLintIcons.toDisabled(icon));
    }
  }

  @Override
  public int getFindingCount() {
    return 1;
  }


  @Override
  public String toString() {
    return securityHotspot.getMessage();
  }

}
