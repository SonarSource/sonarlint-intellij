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

import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProjectBindingManagerTest extends AbstractSonarLintLightTests {
  private ProjectBindingManager projectBindingManager;

  private StandaloneSonarLintEngine standaloneEngine = mock(StandaloneSonarLintEngine.class);
  private ConnectedSonarLintEngine connectedEngine = mock(ConnectedSonarLintEngine.class);
  private SonarLintEngineManager engineManager = mock(SonarLintEngineManager.class);

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void before() throws InvalidBindingException {
    SonarLintConsole console = mock(SonarLintConsole.class);
    SonarLintProjectNotifications notifications = mock(SonarLintProjectNotifications.class);
    replaceProjectService(SonarLintConsole.class, console);
    replaceProjectService(SonarLintProjectNotifications.class, notifications);

    when(engineManager.getStandaloneEngine()).thenReturn(standaloneEngine);
    when(engineManager.getConnectedEngine(any(SonarLintProjectNotifications.class), anyString(), anyString())).thenReturn(connectedEngine);
    projectBindingManager = new ProjectBindingManager(getProject(), () -> engineManager);
  }

  @Test
  public void should_create_facade_standalone() throws InvalidBindingException {
    assertThat(projectBindingManager.getFacade()).isInstanceOf(StandaloneSonarLintFacade.class);
  }

  @Test
  public void should_get_connected_engine() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey("project1");
    getProjectSettings().setConnectionName("server1");

    assertThat(projectBindingManager.getConnectedEngine()).isNotNull();
    verify(engineManager).getConnectedEngine(any(SonarLintProjectNotifications.class), eq("server1"), eq("project1"));
  }

  @Test
  public void fail_get_connected_engine_if_not_connected() throws InvalidBindingException {
    exception.expect(IllegalStateException.class);
    projectBindingManager.getConnectedEngine();
  }

  @Test
  public void should_create_facade_connected() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey("project1");
    getProjectSettings().setConnectionName("server1");

    assertThat(projectBindingManager.getFacade()).isInstanceOf(ConnectedSonarLintFacade.class);
  }

  @Test
  public void should_find_sq_server() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey("project1");
    getProjectSettings().setConnectionName("server1");

    ServerConnection server = ServerConnection.newBuilder().setName("server1").build();
    getGlobalSettings().setServerConnections(Collections.singletonList(server));
    assertThat(projectBindingManager.getServerConnection()).isEqualTo(server);
  }

  @Test
  public void fail_if_cant_find_server() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey("project1");
    getProjectSettings().setConnectionName("server1");

    ServerConnection server = ServerConnection.newBuilder().setName("server2").build();
    getGlobalSettings().setServerConnections(Collections.singletonList(server));
    exception.expect(InvalidBindingException.class);
    projectBindingManager.getServerConnection();
  }

  @Test
  public void fail_invalid_server_binding() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    exception.expect(InvalidBindingException.class);
    exception.expectMessage("Project has an invalid binding");
    assertThat(projectBindingManager.getFacade()).isNotNull();
  }

  @Test
  public void fail_invalid_module_binding() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setConnectionName("server1");
    getProjectSettings().setProjectKey(null);

    exception.expect(InvalidBindingException.class);
    exception.expectMessage("Project has an invalid binding");
    assertThat(projectBindingManager.getFacade()).isNotNull();
  }
}
