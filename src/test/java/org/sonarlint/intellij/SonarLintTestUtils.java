/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.awt.*;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintTestUtils {

  static {
    System.out.println("headless mode: " + GraphicsEnvironment.isHeadless());
  }

  private SonarLintTestUtils() {
    // only static
  }

  public static AnActionEvent createAnActionEvent(@Nullable Project project) {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
    return event;
  }

  public static RaisedIssueDto createIssue(int id) {
    var issue = mock(RaisedIssueDto.class);
    when(issue.getRuleKey()).thenReturn(Integer.toString(id));
    when(issue.getPrimaryMessage()).thenReturn("issue " + id);
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)));
    return issue;
  }

  public static void clearServerConnectionCredentials() {
    if (ApplicationManager.getApplication() == null) {
      return;
    }

    try {
      for (var connectionName : getGlobalSettings().getServerNames()) {
        clearCredentialsForConnection(connectionName);
      }
    } catch (Exception e) {
      // Ignore exceptions during cleanup - PasswordSafe might not be available in all test environments
    }
  }

  private static void clearCredentialsForConnection(String connectionName) {
    try {
      var passwordSafe = PasswordSafe.getInstance();

      // Clear token
      var tokenKey = "server:" + connectionName + ":token";
      var tokenAttributes = new CredentialAttributes(
        CredentialAttributesKt.generateServiceName("SonarLint", tokenKey)
      );
      passwordSafe.setPassword(tokenAttributes, null);

      // Clear password
      var passwordKey = "server:" + connectionName + ":password";
      var passwordAttributes = new CredentialAttributes(
        CredentialAttributesKt.generateServiceName("SonarLint", passwordKey)
      );
      passwordSafe.set(passwordAttributes, null);
    } catch (Exception e) {
      // Ignore exceptions - credential might not exist or PasswordSafe might not be available
    }
  }

}
