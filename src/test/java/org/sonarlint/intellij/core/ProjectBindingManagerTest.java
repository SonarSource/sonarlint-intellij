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
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProjectBindingManagerTest {
  private ProjectBindingManager projectBindingManager;
  private SonarLintProjectSettings settings;
  private SonarLintGlobalSettings globalSettings;

  private StandaloneSonarLintEngine standaloneEngine;
  private ConnectedSonarLintEngine connectedEngine;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    SonarLintConsole console = mock(SonarLintConsole.class);
    Project project = mock(Project.class);
    SonarLintEngineManager engineManager = mock(SonarLintEngineManager.class);
    SonarLintProjectNotifications notifications = mock(SonarLintProjectNotifications.class);

    standaloneEngine = mock(StandaloneSonarLintEngine.class);
    connectedEngine = mock(ConnectedSonarLintEngine.class);

    settings = new SonarLintProjectSettings();
    globalSettings = new SonarLintGlobalSettings();
    when(engineManager.getStandaloneEngine()).thenReturn(standaloneEngine);
    when(engineManager.getConnectedEngine(any(SonarLintProjectNotifications.class), anyString(), anyString())).thenReturn(connectedEngine);
    when(project.getBasePath()).thenReturn("");
    projectBindingManager = new ProjectBindingManager(project, engineManager, settings, globalSettings, notifications, console);
  }

  @Test
  public void should_create_facade_standalone() {
    assertThat(projectBindingManager.getFacadeForAnalysis()).isNotNull();
  }

  @Test
  public void should_create_facade_connected() {
    settings.setBindingEnabled(true);
    settings.setProjectKey("project1");
    settings.setServerId("server1");
    assertThat(projectBindingManager.getFacadeForAnalysis()).isNotNull();
  }

  @Test
  public void should_find_sq_server() {
    settings.setBindingEnabled(true);
    settings.setProjectKey("project1");
    settings.setServerId("server1");

    SonarQubeServer server = new SonarQubeServer();
    server.setName("server1");
    globalSettings.setSonarQubeServers(Collections.singletonList(server));
    assertThat(projectBindingManager.getSonarQubeServer()).isEqualTo(server);
  }

  @Test
  public void fail_if_cant_find_server() {
    settings.setBindingEnabled(true);
    settings.setProjectKey("project1");
    settings.setServerId("server1");

    SonarQubeServer server = new SonarQubeServer();
    server.setName("server2");
    globalSettings.setSonarQubeServers(Collections.singletonList(server));
    exception.expect(IllegalStateException.class);
    projectBindingManager.getSonarQubeServer();
  }

  @Test
  public void fail_invalid_server_binding() {
    settings.setBindingEnabled(true);
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Project has an invalid binding");
    assertThat(projectBindingManager.getFacadeForAnalysis()).isNotNull();
  }

  @Test
  public void fail_invalid_module_binding() {
    settings.setBindingEnabled(true);
    settings.setServerId("server1");
    settings.setProjectKey(null);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Project has an invalid binding");
    assertThat(projectBindingManager.getFacadeForAnalysis()).isNotNull();
  }
}
