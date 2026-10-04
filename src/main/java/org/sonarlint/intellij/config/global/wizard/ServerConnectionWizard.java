/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.credentials.CredentialsService;
import org.sonarlint.intellij.config.global.credentials.CredentialOperationRunner;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK;

public class ServerConnectionWizard {
  private final ConnectionWizardModel model;
  private AbstractWizardEx wizardEx;

  private ServerConnectionWizard(ConnectionWizardModel model) {
    this.model = model;
  }

  public static ServerConnectionWizard forNewConnection(Set<String> existingNames) {
    var wizard = new ServerConnectionWizard(new ConnectionWizardModel());
    var steps = createSteps(wizard.model, false, existingNames);
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "New Connection");
    return wizard;
  }

  public static ServerConnectionWizard forNewConnection(ServerConnection prefilledConnection, Set<String> existingNames) {
    var wizard = new ServerConnectionWizard(new ConnectionWizardModel(prefilledConnection));
    var steps = createSteps(wizard.model, false, existingNames);
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "New Connection");
    return wizard;
  }

  public static ServerConnectionWizard forConnectionEdition(ServerConnection connectionToEdit) {
    var credentials = SonarLintUtils.getService(CredentialsService.class).getCredentials(connectionToEdit);
    return forConnectionEdition(connectionToEdit, credentials);
  }

  public static ServerConnectionWizard forConnectionEdition(ServerConnection connectionToEdit, Either<TokenDto, UsernamePasswordDto> credentials) {
    var wizard = new ServerConnectionWizard(new ConnectionWizardModel(connectionToEdit, credentials));
    var steps = createSteps(wizard.model, true, Collections.emptySet());
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "Edit Connection");
    return wizard;
  }

  public static ServerConnectionWizard forNotificationsEdition(ServerConnection connectionToEdit) {
    var credentials = SonarLintUtils.getService(CredentialsService.class).getCredentials(connectionToEdit);
    return forNotificationsEdition(connectionToEdit, credentials);
  }

  public static ServerConnectionWizard forNotificationsEdition(ServerConnection connectionToEdit, Either<TokenDto, UsernamePasswordDto> credentials) {
    var wizard = new ServerConnectionWizard(new ConnectionWizardModel(connectionToEdit, credentials));
    var steps = List.of(new NotificationsStep(wizard.model, true));
    wizard.wizardEx = new ServerConnectionWizardEx(steps, "Edit Connection");
    return wizard;
  }

  private static List<AbstractWizardStepEx> createSteps(ConnectionWizardModel model, boolean editing, Set<String> existingNames) {
    return List.of(
      new ServerStep(model, editing, existingNames),
      new AuthStep(model),
      new OrganizationStep(model),
      new NotificationsStep(model, false),
      new ConfirmStep(editing)
    );
  }

  public boolean showAndGet() {
    return wizardEx.showAndGet();
  }

  public void cancel() {
    wizardEx.close(DialogWrapper.CANCEL_EXIT_CODE);
  }

  public ServerConnection getConnection() {
    return model.createConnection();
  }

  static class ServerConnectionWizardEx extends AbstractWizardEx {
    private boolean savingCredentials;
    public ServerConnectionWizardEx(List<? extends AbstractWizardStepEx> steps, String title) {
      super(title, null, steps);
      this.setHorizontalStretch(1.25f);
      this.setVerticalStretch(1.25f);
    }

    @Override
    protected void doNextAction() {
      if (savingCredentials) {
        return;
      }
      if (getCurrentStepObject() instanceof AuthStep authStep) {
        if (!authStep.isComplete()) {
          return;
        }
        var credentials = authStep.snapshotCredentials();
        savingCredentials = true;
        authStep.setSaving(true);
        updateButtons();
        CredentialOperationRunner.save(getDisposable(), authStep.getComponent(), authStep.getConnectionName(), credentials,
          () -> getCurrentStepObject() == authStep,
          () -> ServerConnectionWizardEx.super.doNextAction(),
          error -> Messages.showErrorDialog(authStep.getComponent(), error.getMessage(), "Unable to Save Credentials"),
          () -> {
            savingCredentials = false;
            authStep.setSaving(false);
            updateButtons();
          });
      } else {
        super.doNextAction();
      }
    }

    @Override
    protected void doPreviousAction() {
      if (!savingCredentials) {
        super.doPreviousAction();
      }
    }

    @Override
    protected void updateButtons() {
      super.updateButtons();
      if (savingCredentials) {
        getNextButton().setEnabled(false);
        getPreviousButton().setEnabled(false);
      }
    }

    @Override
    protected void doHelpAction() {
      BrowserUtil.browse(CONNECTED_MODE_LINK);
    }

    @Override
    protected String getDimensionServiceKey() {
      return this.getClass().getName();
    }
  }
}
