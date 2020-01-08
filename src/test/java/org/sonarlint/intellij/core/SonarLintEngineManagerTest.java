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
import java.util.Date;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintEngineManagerTest {
  private SonarLintEngineManager manager;
  private SonarLintGlobalSettings globalSettings;
  private SonarLintEngineFactory engineFactory;
  private SonarLintProjectNotifications notifications;
  private ConnectedSonarLintEngine connectedEngine;
  private StandaloneSonarLintEngine standaloneEngine;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    globalSettings = new SonarLintGlobalSettings();
    engineFactory = mock(SonarLintEngineFactory.class);
    notifications = mock(SonarLintProjectNotifications.class);
    connectedEngine = mock(ConnectedSonarLintEngine.class);
    standaloneEngine = mock(StandaloneSonarLintEngine.class);

    when(engineFactory.createEngine(anyString())).thenReturn(connectedEngine);
    when(engineFactory.createEngine()).thenReturn(standaloneEngine);

    manager = new SonarLintEngineManager(globalSettings, engineFactory);
  }

  @Test
  public void should_get_standalone() {
    assertThat(manager.getStandaloneEngine()).isEqualTo(standaloneEngine);
    assertThat(manager.getStandaloneEngine()).isEqualTo(standaloneEngine);
    verify(engineFactory, Mockito.times(1)).createEngine();
  }

  @Test
  public void should_get_connected() {
    manager.initComponent();
    assertThat(manager.getConnectedEngine("server1")).isEqualTo(connectedEngine);
    assertThat(manager.getConnectedEngine("server1")).isEqualTo(connectedEngine);
    verify(engineFactory, Mockito.times(1)).createEngine("server1");
  }

  @Test
  public void should_fail_invalid_server() throws InvalidBindingException {
    manager.initComponent();
    exception.expect(InvalidBindingException.class);
    exception.expectMessage("Invalid server name");
    manager.getConnectedEngine(notifications, "server1", "project1");
  }

  @Test
  public void should_fail_not_updated() throws InvalidBindingException {
    globalSettings.setSonarQubeServers(Collections.singletonList(createServer("server1")));
    manager = new SonarLintEngineManager(globalSettings, engineFactory);

    manager.initComponent();

    exception.expect(InvalidBindingException.class);
    exception.expectMessage("Connection local storage is not updated");
    manager.getConnectedEngine(notifications, "server1", "project1");
  }

  @Test
  public void should_pass_checks() throws InvalidBindingException {
    when(connectedEngine.getState()).thenReturn(ConnectedSonarLintEngine.State.UPDATED);
    when(connectedEngine.getProjectStorageStatus("project1")).thenReturn(projectOk);

    globalSettings.setSonarQubeServers(Collections.singletonList(createServer("server1")));
    manager = new SonarLintEngineManager(globalSettings, engineFactory);

    manager.initComponent();
    assertThat(manager.getConnectedEngine(notifications, "server1", "project1")).isEqualTo(connectedEngine);

    verify(engineFactory, Mockito.times(1)).createEngine("server1");
    verify(connectedEngine).getState();
  }

  private static SonarQubeServer createServer(String name) {
    return SonarQubeServer.newBuilder().setName(name).build();
  }

  private static ProjectStorageStatus projectOk = new ProjectStorageStatus() {
    @Override public Date getLastUpdateDate() {
      return new Date(System.currentTimeMillis());
    }

    @Override public boolean isStale() {
      return false;
    }
  };
}
