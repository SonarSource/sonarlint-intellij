/*
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
package org.sonarlint.intellij.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class UpdateCheckerTest extends SonarTest {
  private UpdateChecker updateChecker;
  private SonarLintProjectSettings settings;
  private SonarQubeServer server;

  @Mock
  private Project project;
  @Mock
  private SonarLintProjectNotifications notifications;
  @Mock
  private ProjectBindingManager bindingManager;
  @Mock
  private ConnectedSonarLintEngine engine;

  @Before
  public void before() throws InvalidBindingException {
    MockitoAnnotations.initMocks(this);
    settings = new SonarLintProjectSettings();
    settings.setProjectKey("key");
    settings.setServerId("serverId");
    server = createServer();
    super.register(app, SonarApplication.class, mock(SonarApplication.class));
    super.register(app, GlobalLogOutput.class, new GlobalLogOutput(mock(ProjectManager.class)));
    when(bindingManager.getSonarQubeServer()).thenReturn(server);
    when(bindingManager.getConnectedEngine()).thenReturn(engine);

    updateChecker = new UpdateChecker(project, bindingManager, settings, notifications);
  }

  @Test
  public void do_nothing_if_no_engine() throws InvalidBindingException {
    when(bindingManager.getConnectedEngine()).thenThrow(new IllegalStateException());
    updateChecker.checkForUpdate();

    verifyZeroInteractions(engine);
    verifyZeroInteractions(notifications);
  }

  @Test
  public void do_nothing_if_no_updates() {
    StorageUpdateCheckResult result = mock(StorageUpdateCheckResult.class);
    when(result.needUpdate()).thenReturn(false);

    when(engine.checkIfModuleStorageNeedUpdate(any(ServerConfiguration.class), anyString(), isNull())).thenReturn(result);
    when(engine.checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), isNull())).thenReturn(result);

    updateChecker.checkForUpdate();

    verify(engine).checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), isNull());
    verify(engine).checkIfModuleStorageNeedUpdate(any(ServerConfiguration.class), anyString(), isNull());

    verifyZeroInteractions(notifications);
  }

  @Test
  public void global_changes() {
    StorageUpdateCheckResult result = mock(StorageUpdateCheckResult.class);
    when(result.needUpdate()).thenReturn(true);
    when(result.changelog()).thenReturn(Collections.singletonList("change1"));

    when(engine.checkIfModuleStorageNeedUpdate(any(ServerConfiguration.class), anyString(), isNull())).thenReturn(result);
    when(engine.checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), isNull())).thenReturn(result);

    updateChecker.checkForUpdate();

    verify(engine).checkIfGlobalStorageNeedUpdate(any(ServerConfiguration.class), isNull());
    verify(engine).checkIfModuleStorageNeedUpdate(any(ServerConfiguration.class), anyString(), isNull());
    verify(notifications).notifyServerHasUpdates("serverId", engine, server, false);

    verifyNoMoreInteractions(engine);
    verifyZeroInteractions(notifications);
  }

  private SonarQubeServer createServer() {
    return SonarQubeServer.newBuilder()
      .setHostUrl("http://localhost:9000")
      .setName("server1")
      .build();
  }
}
