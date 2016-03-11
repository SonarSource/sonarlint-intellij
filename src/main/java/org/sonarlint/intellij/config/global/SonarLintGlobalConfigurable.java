/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.VerticalFlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class SonarLintGlobalConfigurable implements Configurable{
  private JPanel rootPanel;
  private SonarQubeServerMgmtPanel serversPanel;
  private SonarLintGlobalSettingsPanel globalPanel;

  private SonarLintGlobalSettings globalSettings;

  public SonarLintGlobalConfigurable() {
    globalSettings = ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
  }

  @Nls @Override public String getDisplayName() {
    return "SonarLint General Settings";
  }

  @Nullable @Override public String getHelpTopic() {
    return null;
  }

  @Nullable @Override public JComponent createComponent() {
    return getPanel();
  }

  @Override public boolean isModified() {
    return  serversPanel.isModified(globalSettings) || globalPanel.isModified();
  }

  @Override public void apply() throws ConfigurationException {
    serversPanel.save(globalSettings);
    globalPanel.save(globalSettings);
  }

  @Override public void reset() {
    serversPanel.load(globalSettings);
    globalPanel.load(globalSettings);
  }

  @Override public void disposeUIResources() {
    if (rootPanel != null) {
      rootPanel.setVisible(false);
    }
    rootPanel = null;
  }

  private JPanel getPanel() {
    if (rootPanel == null) {
      rootPanel = new JPanel(new VerticalFlowLayout());
      globalPanel = new SonarLintGlobalSettingsPanel(globalSettings);
      serversPanel = new SonarQubeServerMgmtPanel(globalSettings);
      rootPanel.add(globalPanel.getComponent());
      rootPanel.add(serversPanel.getComponent());
    }

    return rootPanel;
  }
}
