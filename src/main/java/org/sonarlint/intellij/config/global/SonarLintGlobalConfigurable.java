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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;

public class SonarLintGlobalConfigurable implements Configurable, Configurable.NoScroll {
  private final GlobalConfigurationListener changeListener;
  private JPanel rootPanel;
  private SonarQubeServerMgmtPanel serversPanel;
  private SonarLintGlobalSettingsPanel globalPanel;

  private SonarLintGlobalSettings globalSettings;

  public SonarLintGlobalConfigurable() {
    Application app = ApplicationManager.getApplication();
    this.globalSettings = app.getComponent(SonarLintGlobalSettings.class);
    this.changeListener = app.getMessageBus().syncPublisher(GlobalConfigurationListener.SONARLINT_GLOBAL_CONFIG_TOPIC);
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

    changeListener.changed();
  }

  @Override public void reset() {
    if(serversPanel != null) {
      serversPanel.load(globalSettings);
    }
    if(globalPanel != null) {
      globalPanel.load(globalSettings);
    }
  }

  @Override public void disposeUIResources() {
    if (rootPanel != null) {
      rootPanel.setVisible(false);
    }
    rootPanel = null;
  }

  private JPanel getPanel() {
    if (rootPanel == null) {
      rootPanel = new JPanel(new BorderLayout());
      globalPanel = new SonarLintGlobalSettingsPanel(globalSettings);
      serversPanel = new SonarQubeServerMgmtPanel(globalSettings);
      rootPanel.add(globalPanel.getComponent(), BorderLayout.NORTH);
      rootPanel.add(serversPanel.getComponent(), BorderLayout.CENTER);
    }

    return rootPanel;
  }
}
