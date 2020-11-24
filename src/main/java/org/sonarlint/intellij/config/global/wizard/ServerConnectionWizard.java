/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.sonarlint.intellij.config.global.ServerConnection;

public class ServerConnectionWizard {
  private final WizardModel model;
  private AbstractWizardEx wizardEx;

  private ServerConnectionWizard(WizardModel model) {
    this.model = model;
  }

  public static ServerConnectionWizard forNewConnection(Set<String> existingNames) {
    ServerConnectionWizard wizard = new ServerConnectionWizard(new WizardModel());
    List<AbstractWizardStepEx> steps = createSteps(wizard.model, false, existingNames);
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "New Connection");
    return wizard;
  }

  public static ServerConnectionWizard forConnectionEdition(ServerConnection connectionToEdit) {
    ServerConnectionWizard wizard = new ServerConnectionWizard(new WizardModel(connectionToEdit));
    List<AbstractWizardStepEx> steps = createSteps(wizard.model, true, Collections.emptySet());
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "Edit Connection");
    return wizard;
  }

  public static ServerConnectionWizard forNotificationsEdition(ServerConnection connectionToEdit) {
    ServerConnectionWizard wizard = new ServerConnectionWizard(new WizardModel(connectionToEdit));
    // Assume notifications are supported, if not, why would we want to edit the setting
    wizard.model.setNotificationsSupported(true);
    List<AbstractWizardStepEx> steps = Collections.singletonList(new NotificationsStep(wizard.model, true));
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "Edit Connection");
    return wizard;
  }

  private static List<AbstractWizardStepEx> createSteps(WizardModel model, boolean editing, Set<String> existingNames) {
    List<AbstractWizardStepEx> steps = new LinkedList<>();
    steps.add(new ServerStep(model, editing, existingNames));
    steps.add(new AuthStep(model));
    steps.add(new OrganizationStep(model));
    steps.add(new NotificationsStep(model, false));
    steps.add(new ConfirmStep(model, editing));
    return steps;
  }

  public boolean showAndGet() {
    return wizardEx.showAndGet();
  }

  public ServerConnection getConnection() {
    return model.createConnection();
  }

  private static class ServerConnectionWizardEx extends AbstractWizardEx {
    public ServerConnectionWizardEx(List<AbstractWizardStepEx> steps, String title) {
      super(title, null, steps);
      this.setHorizontalStretch(1.25f);
      this.setVerticalStretch(1.25f);
    }

    @Override
    protected void helpAction() {
      BrowserUtil.browse("https://github.com/SonarSource/sonarlint-intellij/wiki/Connected-Mode");
    }

    @Override
    protected String getDimensionServiceKey() {
      return this.getClass().getName();
    }
  }
}
