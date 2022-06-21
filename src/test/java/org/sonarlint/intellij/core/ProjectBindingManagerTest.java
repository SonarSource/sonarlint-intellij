/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import com.intellij.openapi.progress.ProgressManager;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.serverconnection.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.serverconnection.ProjectStorageStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProjectBindingManagerTest extends AbstractSonarLintLightTests {
  private ProjectBindingManager projectBindingManager;

  private StandaloneSonarLintEngine standaloneEngine = mock(StandaloneSonarLintEngine.class);
  private ConnectedSonarLintEngine connectedEngine = mock(ConnectedSonarLintEngine.class);

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void before() throws InvalidBindingException {
    var console = mock(SonarLintConsole.class);
    var notifications = mock(SonarLintProjectNotifications.class);
    replaceProjectService(SonarLintConsole.class, console);
    replaceProjectService(SonarLintProjectNotifications.class, notifications);

    projectBindingManager = new ProjectBindingManager(getProject(), mock(ProgressManager.class));
  }

  @Test
  public void should_create_facade_standalone() throws InvalidBindingException {
    assertThat(projectBindingManager.getFacade(getModule())).isInstanceOf(StandaloneSonarLintFacade.class);
  }

  @Test
  public void should_get_connected_engine() throws InvalidBindingException {
    connectProjectTo(ServerConnection.newBuilder().setName("server1").build(), "project1");
    getEngineManager().registerEngine(connectedEngine, "server1");

    var engine = projectBindingManager.getConnectedEngine();

    assertThat(engine).isEqualTo(connectedEngine);
  }

  @Test
  public void fail_get_connected_engine_if_not_connected() throws InvalidBindingException {
    exception.expect(IllegalStateException.class);
    projectBindingManager.getConnectedEngine();
  }

  @Test
  public void should_create_facade_connected() throws InvalidBindingException {
    connectProjectTo(ServerConnection.newBuilder().setName("server1").build(), "project1");
    getEngineManager().registerEngine(connectedEngine, "server1");

    var facade = projectBindingManager.getFacade(getModule());

    assertThat(facade).isInstanceOf(ConnectedSonarLintFacade.class);
  }

  @Test
  public void should_find_sq_server() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey("project1");
    getProjectSettings().setConnectionName("server1");

    var server = ServerConnection.newBuilder().setName("server1").build();
    getGlobalSettings().setServerConnections(List.of(server));
    assertThat(projectBindingManager.getServerConnection()).isEqualTo(server);
  }

  @Test
  public void fail_if_cant_find_server() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey("project1");
    getProjectSettings().setConnectionName("server1");

    var server = ServerConnection.newBuilder().setName("server2").build();
    getGlobalSettings().setServerConnections(List.of(server));
    exception.expect(InvalidBindingException.class);
    projectBindingManager.getServerConnection();
  }

  @Test
  public void fail_invalid_server_binding() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    exception.expect(InvalidBindingException.class);
    exception.expectMessage("Project has an invalid binding");
    projectBindingManager.getFacade(getModule());
  }

  @Test
  public void fail_invalid_module_binding() throws InvalidBindingException {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setConnectionName("server1");
    getProjectSettings().setProjectKey(null);

    exception.expect(InvalidBindingException.class);
    exception.expectMessage("Project has an invalid binding");
    projectBindingManager.getFacade(getModule());
  }

  @Test
  public void should_return_connected_engine_if_started() {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setConnectionName("server1");
    getProjectSettings().setProjectKey("key");
    getEngineManager().registerEngine(connectedEngine, "server1");

    var engine = projectBindingManager.getEngineIfStarted();

    assertThat(engine).isEqualTo(connectedEngine);
  }

  @Test
  public void should_return_standalone_engine_if_started() {
    getProjectSettings().setBindingEnabled(false);
    getEngineManager().registerEngine(standaloneEngine);

    var engine = projectBindingManager.getEngineIfStarted();

    assertThat(engine).isEqualTo(standaloneEngine);
  }

  @Test
  public void should_not_return_connected_engine_if_not_started() {
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setConnectionName("server1");
    getProjectSettings().setProjectKey(null);

    var engine = projectBindingManager.getEngineIfStarted();

    assertThat(engine).isNull();
  }

  @Test
  public void should_not_return_standalone_engine_if_not_started() {
    getProjectSettings().setBindingEnabled(false);

    var engine = projectBindingManager.getEngineIfStarted();

    assertThat(engine).isNull();
  }

  @Test
  public void should_store_project_binding_in_settings() {
    var connection = ServerConnection.newBuilder().setName("name").build();
    getGlobalSettings().setServerConnections(List.of(connection));

    projectBindingManager.bindTo(connection, "projectKey", Collections.emptyMap());

    assertThat(getProjectSettings().isBoundTo(connection)).isTrue();
    assertThat(getProjectSettings().getProjectKey()).isEqualTo("projectKey");
  }

  @Test
  public void should_store_project_and_module_bindings_in_settings() {
    var connection = ServerConnection.newBuilder().setName("name").build();
    getGlobalSettings().setServerConnections(List.of(connection));
    projectBindingManager.bindTo(connection, "projectKey", Map.of(getModule(), "moduleProjectKey"));

    assertThat(getProjectSettings().isBoundTo(connection)).isTrue();
    assertThat(getProjectSettings().getProjectKey()).isEqualTo("projectKey");
    assertThat(getModuleSettings().isProjectBindingOverridden()).isTrue();
    assertThat(getModuleSettings().getProjectKey()).isEqualTo("moduleProjectKey");
  }

  @Test
  public void should_clear_project_and_module_binding_settings_when_unbinding() {
    getProjectSettings().bindTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey");
    getModuleSettings().setProjectKey("moduleProjectKey");

    projectBindingManager.unbind();

    assertThat(getProjectSettings().isBound()).isFalse();
    assertThat(getModuleSettings().isProjectBindingOverridden()).isFalse();
  }
}
