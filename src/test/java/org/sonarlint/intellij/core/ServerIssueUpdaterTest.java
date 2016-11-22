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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServerIssueUpdaterTest extends SonarTest {
  public static final String PROJECT_KEY = "foo";
  private ServerIssueUpdater updater;
  private SonarLintProjectSettings settings;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Mock
  private Project project;
  @Mock
  private IssueManager issueManager;
  @Mock
  private ProjectBindingManager bindingManager;
  @Mock
  private SonarLintConsole console;

  private Path projectBaseDir;

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);
    super.register(app, SonarApplication.class, mock(SonarApplication.class));

    projectBaseDir = temp.newFolder().toPath();

    when(project.getBasePath()).thenReturn( FileUtil.toSystemIndependentName(projectBaseDir.toString()));
    settings = new SonarLintProjectSettings();
    settings.setProjectKey(PROJECT_KEY);
    updater = new ServerIssueUpdater(project, issueManager, settings, bindingManager, console);
  }

  @Test
  public void should_do_nothing_if_not_connected() {
    VirtualFile file = mock(VirtualFile.class);
    settings.setBindingEnabled(false);

    updater.fetchAndMatchServerIssues(Collections.singletonList(file));
    verifyZeroInteractions(bindingManager);
    verifyZeroInteractions(issueManager);
  }

  @Test
  public void testServerIssueTracking() {
    VirtualFile file = mock(VirtualFile.class);
    ServerIssue serverIssue = mock(ServerIssue.class);
    String filename = "MyFile.txt";
    when(file.getPath()).thenReturn(FileUtil.toSystemIndependentName(projectBaseDir.resolve(filename).toString()));

    // mock creation of engine / server
    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    when(bindingManager.getConnectedEngine()).thenReturn(engine);
    SonarQubeServer server = mock(SonarQubeServer.class);
    when(server.getHostUrl()).thenReturn("http://dummyserver:9000");
    when(bindingManager.getSonarQubeServer()).thenReturn(server);

    // mock issues downloaded
    when(engine.downloadServerIssues(any(ServerConfiguration.class), eq(PROJECT_KEY), eq(filename)))
      .thenReturn(Collections.singleton(serverIssue).iterator());

    // run
    settings.setBindingEnabled(true);

    updater.initComponent();
    updater.fetchAndMatchServerIssues(Collections.singletonList(file));

    verify(issueManager, timeout(3000).times(1)).matchWithServerIssues(eq(file), argThat(issues -> issues.size() == 1));

    verify(bindingManager).getConnectedEngine();
    verify(console, never()).error(anyString());
    verify(console, never()).error(anyString(), any(Throwable.class));
  }
}
