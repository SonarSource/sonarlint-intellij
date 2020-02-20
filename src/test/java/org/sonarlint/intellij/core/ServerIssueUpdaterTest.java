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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServerIssueUpdaterTest extends SonarTest {
  public static final String PROJECT_KEY = "foo";
  public static final ProjectBinding PROJECT_BINDING = new ProjectBinding(PROJECT_KEY, "", "");
  private SonarLintProjectSettings settings = new SonarLintProjectSettings();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Project project = mock(Project.class);
  private IssueManager issueManager = mock(IssueManager.class);
  private ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private SonarLintConsole console = mock(SonarLintConsole.class);
  private ProgressIndicator indicator = mock(ProgressIndicator.class);
  private ModuleBindingManager moduleBindingManager = mock(ModuleBindingManager.class);
  private SonarLintAppUtils appUtils = mock(SonarLintAppUtils.class);
  private ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);

  private ServerIssueUpdater updater = new ServerIssueUpdater(project, issueManager, settings, bindingManager, console, appUtils);

  @Before
  public void prepare() throws IOException, InvalidBindingException {
    super.register(app, SonarApplication.class, mock(SonarApplication.class));
    super.register(module, ModuleBindingManager.class, moduleBindingManager);
    when(module.getProject()).thenReturn(project);
    Path projectBaseDir = temp.newFolder().toPath();
    when(moduleBindingManager.getBinding()).thenReturn(new ProjectBinding(PROJECT_KEY, "", ""));
    when(indicator.isModal()).thenReturn(false);
    when(project.getBasePath()).thenReturn(FileUtil.toSystemIndependentName(projectBaseDir.toString()));
    settings.setProjectKey(PROJECT_KEY);

    // mock creation of engine / server
    when(bindingManager.getConnectedEngine()).thenReturn(engine);
    SonarQubeServer server = mock(SonarQubeServer.class);
    when(server.getHostUrl()).thenReturn("http://dummyserver:9000");
    when(bindingManager.getSonarQubeServer()).thenReturn(server);
  }

  @Test
  public void should_do_nothing_if_not_connected() {
    VirtualFile file = mock(VirtualFile.class);
    settings.setBindingEnabled(false);

    updater.fetchAndMatchServerIssues(Collections.singletonMap(module, Collections.singletonList(file)), indicator, false);
    verifyZeroInteractions(bindingManager);
    verifyZeroInteractions(issueManager);
  }

  @Test
  public void testServerIssueTracking() throws InvalidBindingException {
    VirtualFile file = mock(VirtualFile.class);
    ServerIssue serverIssue = mock(ServerIssue.class);
    String filename = "MyFile.txt";
    when(appUtils.getPathRelativeToProjectBaseDir(project, file)).thenReturn(filename);

    // mock issues downloaded
    when(engine.downloadServerIssues(any(ServerConfiguration.class), eq(PROJECT_BINDING), eq(filename)))
      .thenReturn(Collections.singletonList(serverIssue));

    // run
    settings.setBindingEnabled(true);

    updater.initComponent();
    updater.fetchAndMatchServerIssues(Collections.singletonMap(module, Collections.singletonList(file)), indicator, false);

    verify(issueManager, timeout(3000).times(1)).matchWithServerIssues(eq(file), argThat(issues -> issues.size() == 1));

    verify(bindingManager).getConnectedEngine();
    verify(console, never()).error(anyString());
    verify(console, never()).error(anyString(), any(Throwable.class));
  }

  @Test
  public void testDownloadAllServerIssues() throws InvalidBindingException {
    List<VirtualFile> files = new LinkedList<>();
    for (int i = 0; i < 10; i++) {
      VirtualFile file = mock(VirtualFile.class);
      when(appUtils.getPathRelativeToProjectBaseDir(project, file)).thenReturn("MyFile" + i + ".txt");
      files.add(file);
    }
    ServerIssue serverIssue = mock(ServerIssue.class);

    // mock issues fetched
    when(engine.getServerIssues(eq(PROJECT_BINDING), anyString())).thenReturn(Collections.singletonList(serverIssue));

    // run
    settings.setBindingEnabled(true);

    updater.initComponent();
    updater.fetchAndMatchServerIssues(Collections.singletonMap(module, files), indicator, false);

    verify(issueManager, timeout(3000).times(10)).matchWithServerIssues(any(VirtualFile.class), argThat(issues -> issues.size() == 1));
    verify(engine).downloadServerIssues(any(ServerConfiguration.class), eq(PROJECT_KEY));
    verify(bindingManager).getConnectedEngine();
    verify(console, never()).error(anyString());
    verify(console, never()).error(anyString(), any(Throwable.class));
  }
}
