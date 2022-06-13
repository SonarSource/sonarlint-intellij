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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ServerIssueUpdaterTest extends AbstractSonarLintLightTests {
  private static final String SERVER_ID = "myServer";
  public static final String PROJECT_KEY = "foo";
  public static final ProjectBinding PROJECT_BINDING = new ProjectBinding(PROJECT_KEY, "", "");
  private static final String FOO_PHP = "foo.php";

  private IssueManager issueManager = mock(IssueManager.class);
  private SonarLintConsole mockedConsole = mock(SonarLintConsole.class);
  private ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);

  private ServerIssueUpdater underTest;

  @Before
  public void prepare() throws InvalidBindingException {
    var bindingManager = spy(SonarLintUtils.getService(getProject(), ProjectBindingManager.class));
    replaceProjectService(IssueManager.class, issueManager);
    replaceProjectService(SonarLintConsole.class, mockedConsole);
    replaceProjectService(ProjectBindingManager.class, bindingManager);
    doReturn(engine).when(bindingManager).getConnectedEngine();
    doReturn(engine).when(bindingManager).getValidConnectedEngine();
    underTest = new ServerIssueUpdater(getProject());
    getGlobalSettings().setServerConnections(List.of(ServerConnection.newBuilder().setName(SERVER_ID).setHostUrl("http://dummyserver:9000").build()));
    getProjectSettings().setConnectionName(SERVER_ID);
    getProjectSettings().setProjectKey(PROJECT_KEY);

  }

  @After
  public void dispose() {
    underTest.dispose();
  }

  @Test
  public void should_do_nothing_if_not_connected() {
    var file = myFixture.copyFileToProject(FOO_PHP, FOO_PHP);
    getProjectSettings().setBindingEnabled(false);

    underTest.fetchAndMatchServerIssues(Map.of(getModule(), List.of(file)), new EmptyProgressIndicator(), false);
    verifyNoInteractions(issueManager);
  }

  @Test
  public void testServerIssueTracking() {
    var file = myFixture.copyFileToProject(FOO_PHP, FOO_PHP);
    var serverIssue = mock(ServerIssue.class);

    // mock issues downloaded
    when(engine.downloadAllServerIssuesForFile(any(EndpointParams.class), any(), eq(PROJECT_BINDING), eq(FOO_PHP), isNull(), isNull()))
      .thenReturn(List.of(serverIssue));

    // run
    getProjectSettings().setBindingEnabled(true);

    underTest.fetchAndMatchServerIssues(Map.of(getModule(), List.of(file)), new EmptyProgressIndicator(), false);

    verify(issueManager, timeout(3000).times(1)).matchWithServerIssues(eq(file), argThat(issues -> issues.size() == 1));

    verify(mockedConsole, never()).error(anyString());
    verify(mockedConsole, never()).error(anyString(), any(Throwable.class));
  }

  @Test
  public void testDownloadAllServerIssues() {

    List<VirtualFile> files = new LinkedList<>();
    for (int i = 0; i < 10; i++) {
      VirtualFile file = myFixture.copyFileToProject(FOO_PHP, "foo" + i + ".php");
      files.add(file);
    }
    var serverIssue = mock(ServerIssue.class);

    // mock issues fetched
    when(engine.getServerIssues(eq(PROJECT_BINDING), anyString(), anyString())).thenReturn(List.of(serverIssue));

    // run
    getProjectSettings().setBindingEnabled(true);

    underTest.fetchAndMatchServerIssues(Map.of(getModule(), files), new EmptyProgressIndicator(), false);

    verify(issueManager, timeout(3000).times(10)).matchWithServerIssues(any(VirtualFile.class), argThat(issues -> issues.size() == 1));
    verify(engine).downloadAllServerIssues(any(), any(), eq(PROJECT_KEY), anyString(), isNull());
    verify(mockedConsole, never()).error(anyString());
    verify(mockedConsole, never()).error(anyString(), any(Throwable.class));
  }
}
