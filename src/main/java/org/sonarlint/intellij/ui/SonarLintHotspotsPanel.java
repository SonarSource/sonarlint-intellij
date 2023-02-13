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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.tools.SimpleActionGroup;
import java.util.Collection;
import java.util.List;
import javax.swing.Box;
import org.sonarlint.intellij.actions.SecurityHotspotsAction;
import org.sonarlint.intellij.actions.SonarLintDetectedSHAction;
import org.sonarlint.intellij.actions.SonarQubeDetectedSHAction;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsStatus;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class SonarLintHotspotsPanel extends SimpleToolWindowPanel implements Disposable {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_HOTSPOTS_SPLIT_PROPORTION";

  private static final String TOOLBAR_GROUP_ID = "SecurityHotspot";
  private static final float DEFAULT_SPLIT_PROPORTION = 0.5f;

  private final SonarLintHotspotsListPanel hotspotsListPanel;

  public SonarLintHotspotsPanel(Project project) {
    super(false, true);
    hotspotsListPanel = new SonarLintHotspotsListPanel(project);
    super.setContent(createSplitter(project, this, this, hotspotsListPanel.getPanel(),
      hotspotsListPanel.getSonarLintRulePanel(), SPLIT_PROPORTION_PROPERTY, DEFAULT_SPLIT_PROPORTION));
    setupToolbar(List.of(new SonarLintDetectedSHAction(), new SonarQubeDetectedSHAction(),
      new SecurityHotspotsAction()));
  }

  public void setLiveHotspots(Collection<LiveSecurityHotspot> hotspots) {
    hotspotsListPanel.loadHotspots(hotspots);
  }

  @Override
  public void dispose() {
    // Nothing to do
  }

  public void populate(SecurityHotspotsStatus status) {
    hotspotsListPanel.populate(status);
  }

  private void setupToolbar(List<AnAction> actions) {
    var group = new SimpleActionGroup();
    actions.forEach(group::add);
    var toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_GROUP_ID, group, false);
    toolbar.setTargetComponent(hotspotsListPanel.getPanel());
    var horizontalBox = Box.createHorizontalBox();
    horizontalBox.add(toolbar.getComponent());
    setToolbar(horizontalBox);
    toolbar.getComponent().setVisible(true);
  }

  public void setSelectedSecurityHotspot(LiveSecurityHotspot securityHotspot) {
    hotspotsListPanel.setSelectedSecurityHotspot(securityHotspot);
  }
}
