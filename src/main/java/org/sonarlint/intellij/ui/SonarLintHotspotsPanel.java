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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.sonarlint.intellij.issue.hotspot.LocalHotspot;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class SonarLintHotspotsPanel extends SimpleToolWindowPanel implements Disposable {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_HOTSPOTS_SPLIT_PROPORTION";
  private static final float DEFAULT_SPLIT_PROPORTION = 0.5f;

  private final JBTabbedPane hotspotDetailsTab;
  private final SonarLintHotspotsListPanel hotspotsListPanel;
  private final SonarLintHotspotDescriptionPanel riskDescriptionPanel;
  private final SonarLintHotspotDescriptionPanel vulnerabilityDescriptionPanel;
  private final SonarLintHotspotDescriptionPanel fixRecommendationsPanel;
  private final SonarLintHotspotDetailsPanel detailsPanel;

  public SonarLintHotspotsPanel(Project project) {
    super(false, true);

    hotspotsListPanel = new SonarLintHotspotsListPanel(project);
    detailsPanel = new SonarLintHotspotDetailsPanel();
    riskDescriptionPanel = new SonarLintHotspotDescriptionPanel();
    vulnerabilityDescriptionPanel = new SonarLintHotspotDescriptionPanel();
    fixRecommendationsPanel = new SonarLintHotspotDescriptionPanel();

    hotspotDetailsTab = new JBTabbedPane();
    hotspotDetailsTab.addTab("What's the risk?", null, scrollable(riskDescriptionPanel.getPanel()), "Risk description");
    hotspotDetailsTab.addTab("Are you at risk?", null, scrollable(vulnerabilityDescriptionPanel.getPanel()), "Vulnerability description");
    hotspotDetailsTab.addTab("How can you fix it?", null, scrollable(fixRecommendationsPanel.getPanel()), "Recommendations");
    hotspotDetailsTab.addTab("Details", null, scrollable(detailsPanel.getPanel()), "Details about the hotspot");
    hotspotDetailsTab.setVisible(false);

    super.setContent(createSplitter(project, this, this, hotspotsListPanel.getPanel(), hotspotDetailsTab, SPLIT_PROPORTION_PROPERTY, DEFAULT_SPLIT_PROPORTION));
  }

  private static JScrollPane scrollable(JComponent component) {
    var scrollableRulePanel = ScrollPaneFactory.createScrollPane(component, true);
    scrollableRulePanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollableRulePanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollableRulePanel.getVerticalScrollBar().setUnitIncrement(10);
    return scrollableRulePanel;
  }

  public void setHotspot(LocalHotspot hotspot) {
    hotspotDetailsTab.setVisible(true);
    hotspotsListPanel.setHotspot(hotspot);
    var hotspotRule = hotspot.getRule();
    riskDescriptionPanel.setDescription(hotspotRule.riskDescription);
    vulnerabilityDescriptionPanel.setDescription(hotspotRule.vulnerabilityDescription);
    fixRecommendationsPanel.setDescription(hotspotRule.fixRecommendations);
    detailsPanel.setDetails(hotspot);
  }

  @Override
  public void dispose() {
    // Nothing to do
  }
}
