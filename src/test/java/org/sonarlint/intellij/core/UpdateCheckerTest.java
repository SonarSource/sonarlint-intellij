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
package org.sonarlint.intellij.core;

import com.intellij.openapi.progress.DumbProgressIndicator;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class UpdateCheckerTest extends AbstractSonarLintLightTests {
  private UpdateChecker updateChecker;
  private ServerConnection server;
  private SonarLintProjectNotifications notifications = mock(SonarLintProjectNotifications.class);
  private ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);

  @Before
  public void before() throws InvalidBindingException {
    replaceProjectService(ProjectBindingManager.class, bindingManager);
    replaceProjectService(SonarLintProjectNotifications.class, notifications);

    getProjectSettings().setProjectKey("key");
    getProjectSettings().setConnectionName("serverId");
    server = createServer();
    when(bindingManager.getServerConnection()).thenReturn(server);
    when(bindingManager.getConnectedEngine()).thenReturn(engine);

    updateChecker = new UpdateChecker(getProject());
  }

  @Test
  public void do_nothing_if_no_engine() throws InvalidBindingException {
    when(bindingManager.getConnectedEngine()).thenThrow(new IllegalStateException());
    updateChecker.checkForUpdate(DumbProgressIndicator.INSTANCE);

    verifyZeroInteractions(engine);
    verifyZeroInteractions(notifications);
  }

  @Test
  public void do_nothing_if_no_updates() {
    StorageUpdateCheckResult result = mock(StorageUpdateCheckResult.class);
    when(result.needUpdate()).thenReturn(false);

    when(engine.checkIfProjectStorageNeedUpdate(any(ServerConfiguration.class), anyString(), any())).thenReturn(result);
    when(engine.checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), any())).thenReturn(result);

    updateChecker.checkForUpdate(DumbProgressIndicator.INSTANCE);

    verify(engine).checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), any());
    verify(engine).checkIfProjectStorageNeedUpdate(any(ServerConfiguration.class), anyString(), any());

    verifyZeroInteractions(notifications);
  }

  @Test
  public void global_changes() {
    StorageUpdateCheckResult result = mock(StorageUpdateCheckResult.class);
    when(result.needUpdate()).thenReturn(true);
    when(result.changelog()).thenReturn(Collections.singletonList("change1"));

    when(engine.checkIfProjectStorageNeedUpdate(any(ServerConfiguration.class), anyString(), any())).thenReturn(result);
    when(engine.checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), any())).thenReturn(result);

    updateChecker.checkForUpdate(DumbProgressIndicator.INSTANCE);

    verify(engine).checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), any());
    verify(engine).checkIfProjectStorageNeedUpdate(any(ServerConfiguration.class), anyString(), any());
    verify(notifications).notifyServerHasUpdates("serverId", engine, server, false);

    verifyNoMoreInteractions(engine);
    verifyZeroInteractions(notifications);
  }

  private ServerConnection createServer() {
    return ServerConnection.newBuilder()
      .setHostUrl("http://localhost:9000")
      .setName("server1")
      .build();
  }
}
