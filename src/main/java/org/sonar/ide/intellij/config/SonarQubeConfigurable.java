/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.config;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang.StringUtils;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeBundle;

import javax.swing.*;
import java.util.List;

public class SonarQubeConfigurable implements Configurable {
  private static final Logger LOG = Logger.getInstance(SonarQubeConfigurable.class);
  private SonarQubeSettingsForm settingsForm;

  @org.jetbrains.annotations.Nls
  @Override
  public String getDisplayName() {
    return SonarQubeBundle.message("sonarqube.sonarqube");
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public javax.swing.JComponent createComponent() {
    if (settingsForm == null) {
      settingsForm = new SonarQubeSettingsForm();
      loadServerPasswords();
    }
    return settingsForm.getFormComponent();
  }

  @Override
  public boolean isModified() {
    if (settingsForm != null) {
      return settingsForm.isModified();
    }
    return false;

  }

  @Override
  public void apply() throws com.intellij.openapi.options.ConfigurationException {
    if (settingsForm != null) {
      final List<SonarQubeServer> servers = settingsForm.getServers();
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          SonarQubeSettings.getInstance().getServers().clear();
          SonarQubeSettings.getInstance().getServers().addAll(servers);
          for (SonarQubeServer server : servers) {
            try {
              if (StringUtils.isBlank(server.getPassword())) {
                PasswordSafe.getInstance().removePassword(null, SonarQubeServer.class, server.getId());
              } else {
                PasswordSafe.getInstance().storePassword(null, SonarQubeServer.class, server.getId(), server.getPassword());
              }
            } catch (PasswordSafeException e) {
              LOG.error("Unable to store password", e);
            }
          }
        }
      });
    }

  }

  @Override
  public void reset() {
    if (settingsForm != null) {
      loadServerPasswords();
    }

  }

  private void loadServerPasswords() {
    final List<SonarQubeServer> servers = SonarQubeSettings.getInstance().getServers();
    settingsForm.setServers(servers);
    // Load server passwords asynchronously to avoid UI lock
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        for (SonarQubeServer server : servers) {
          try {
            server.setPassword(PasswordSafe.getInstance().getPassword(null, SonarQubeServer.class, server.getId()));
          } catch (PasswordSafeException e) {
            LOG.error("Unable to load password", e);
          }
        }
      }
    });
  }

  @Override
  public void disposeUIResources() {
    settingsForm = null;
  }

  public Icon getIcon() {
    return null;
  }

}
