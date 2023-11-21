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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardEx;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.sonarlint.intellij.config.global.ServerConnectionWithAuth;
import org.sonarlint.intellij.documentation.SonarLintDocumentation;

public class ServerConnectionWizard {
  private final WizardModel model;
  private AbstractWizardEx wizardEx;

  private ServerConnectionWizard(WizardModel model) {
    this.model = model;
  }

  public static ServerConnectionWizard forNewConnection(Set<String> existingNames) {
    var wizard = new ServerConnectionWizard(new WizardModel());
    var steps = createSteps(wizard.model, false, existingNames);
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "New Connection");
    return wizard;
  }

  public static ServerConnectionWizard forNewConnection(String serverUrl, Set<String> existingNames) {
    var wizard = new ServerConnectionWizard(new WizardModel(serverUrl));
    var steps = createSteps(wizard.model, false, existingNames);
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "New Connection");
    return wizard;
  }

  public static ServerConnectionWizard forConnectionEdition(ServerConnectionWithAuth connectionToEdit) {
    var wizard = new ServerConnectionWizard(new WizardModel(connectionToEdit));
    var steps = createSteps(wizard.model, true, Collections.emptySet());
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "Edit Connection");
    return wizard;
  }

  public static ServerConnectionWizard forNotificationsEdition(ServerConnectionWithAuth connectionToEdit) {
    var wizard = new ServerConnectionWizard(new WizardModel(connectionToEdit));
    // Assume notifications are supported, if not, why would we want to edit the setting
    wizard.model.setNotificationsSupported(true);
    var steps = List.of(new NotificationsStep(wizard.model, true));
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "Edit Connection");
    return wizard;
  }

  private static List<AbstractWizardStepEx> createSteps(WizardModel model, boolean editing, Set<String> existingNames) {
    return List.of(
      new ServerStep(model, editing, existingNames),
      new AuthStep(model),
      new OrganizationStep(model),
      new NotificationsStep(model, false),
      new ConfirmStep(model, editing)
    );
  }

  public boolean showAndGet() {
    return wizardEx.showAndGet();
  }

  public ServerConnectionWithAuth getConnectionWithAuth() {
    return model.createConnectionWithAuth();
  }

  private static class ServerConnectionWizardEx extends AbstractWizardEx {
    public ServerConnectionWizardEx(List<? extends AbstractWizardStepEx> steps, String title) {
      super(title, null, steps);
      this.setHorizontalStretch(1.25f);
      this.setVerticalStretch(1.25f);
    }

    @Override
    protected void doHelpAction() {
      BrowserUtil.browse(SonarLintDocumentation.CONNECTED_MODE_LINK);
    }

    @Override
    protected String getDimensionServiceKey() {
      return this.getClass().getName();
    }
  }
}
