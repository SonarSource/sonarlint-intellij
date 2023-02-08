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
import com.intellij.ui.scale.JBUIScale;
import icons.SonarLintIcons;
import java.util.Locale;
import javax.swing.Icon;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.util.CompoundIcon;

public class LiveSecurityHotspotNode extends AbstractNode {
  private final LiveSecurityHotspot securityHotspot;

  public LiveSecurityHotspotNode(LiveSecurityHotspot securityHotspot) {
    this.securityHotspot = securityHotspot;
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

    if (securityHotspot.getServerFindingKey() != null) {
      var gap = JBUIScale.isUsrHiDPI() ? 8 : 4;
      renderer.setIconToolTip(vulnerabilityText + " " + typeStr + " matched on SonarQube");
      setIcon(renderer, new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, SonarLintIcons.ICON_SONARQUBE_16,
        SonarLintIcons.type(type + "_" + vulnerabilityText)));
    } else {
      renderer.setIconToolTip(vulnerabilityText + " " + typeStr);
      setIcon(renderer, SonarLintIcons.type12(type));
    }

    renderer.setToolTipText("Double click to open location");
    renderer.append(securityHotspot.getMessage());
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

}
