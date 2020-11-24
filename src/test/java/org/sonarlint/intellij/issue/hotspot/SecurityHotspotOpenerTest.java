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
package org.sonarlint.intellij.issue.hotspot;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.IssuesViewTabOpener;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.editor.SonarLintHighlighting;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityHotspotOpenerTest extends AbstractSonarLintLightTests {
  public static final String FILE_PATH = "com/sonarsource/sample/MyFile.java";
  public static final String SERVER_ID = "name";
  public static final String CONNECTED_URL = "connectedUrl";
  public static final String PROJECT_KEY = "projectKey";
  private ProjectManager projectManager;
  private WsHelperImpl wsHelper;

  @Before
  public void prepare() {
    wsHelper = mock(WsHelperImpl.class);
    IssuesViewTabOpener toolWindow = mock(IssuesViewTabOpener.class);
    SonarLintHighlighting sonarLintHighlighting = mock(SonarLintHighlighting.class);
    projectManager = mock(ProjectManager.class);
    opener = new SecurityHotspotOpener(wsHelper, projectManager);

    // XXX avoid headless mode on CI to uncomment
    // loadToolWindow();

    // XXX remove when tool window loaded properly
    replaceProjectService(IssuesViewTabOpener.class, toolWindow);
    replaceProjectService(SonarLintHighlighting.class, sonarLintHighlighting);
  }

  @Test
  public void it_should_fail_when_no_connection_exists_for_the_given_url() {
    SecurityHotspotOpeningResult result = opener.open("", "", "notConnectedUrl");

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.NO_MATCHING_CONNECTION);
  }

  @Test
  public void it_should_fail_when_no_project_is_opened() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[0]);
    registerConnection();

    SecurityHotspotOpeningResult result = opener.open("", "", CONNECTED_URL);

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.PROJECT_NOT_FOUND);
  }

  @Test
  public void it_should_fail_when_a_single_open_project_is_not_bound() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {getProject()});
    registerConnection();

    SecurityHotspotOpeningResult result = opener.open("", "", CONNECTED_URL);

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.PROJECT_NOT_FOUND);
  }

  @Test
  public void it_should_fail_when_a_single_open_project_is_bound_to_a_different_server() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {getProject()});
    bindProject(PROJECT_KEY, "differentServer");

    SecurityHotspotOpeningResult result = opener.open(PROJECT_KEY, "", CONNECTED_URL);

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.PROJECT_NOT_FOUND);
  }

  @Test
  public void it_should_fail_when_a_single_open_project_is_bound_to_a_different_project_key() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {getProject()});
    bindProject("differentProjectKey", SERVER_ID);

    SecurityHotspotOpeningResult result = opener.open(PROJECT_KEY, "", CONNECTED_URL);

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.PROJECT_NOT_FOUND);
  }

  @Test
  public void it_should_fail_when_an_error_occurs_when_fetching_hotspot_details() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {getProject()});
    bindProject(PROJECT_KEY, SERVER_ID);
    when(wsHelper.getHotspot(any(), any())).thenReturn(Optional.empty());

    SecurityHotspotOpeningResult result = opener.open(PROJECT_KEY, "", CONNECTED_URL);

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.HOTSPOT_DETAILS_NOT_FOUND);
  }

  @Test
  public void it_should_fail_when_the_primary_location_file_does_not_exist() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {getProject()});
    bindProject(PROJECT_KEY, SERVER_ID);
    RemoteHotspot hotspot = new RemoteHotspot("Very hotspot",
      FILE_PATH,
      new TextRange(1, 14, 1, 20),
      "author",
      RemoteHotspot.Status.TO_REVIEW,
      null,
      new RemoteHotspot.Rule("rulekey", "rulename", "category", RemoteHotspot.Rule.Probability.HIGH, "", "", ""));
    when(wsHelper.getHotspot(any(), any())).thenReturn(Optional.of(hotspot));

    SecurityHotspotOpeningResult result = opener.open(PROJECT_KEY, "", CONNECTED_URL);

    assertThat(result).isEqualTo(SecurityHotspotOpeningResult.LOCATION_NOT_MATCHING);
  }

  @Test
  public void it_should_open_the_primary_location_file_when_exists() {
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {getProject()});
    bindProject(PROJECT_KEY, SERVER_ID);
    myFixture.copyFileToProject(FILE_PATH);
    RemoteHotspot hotspot = new RemoteHotspot("Very hotspot",
      FILE_PATH,
      new TextRange(1, 14, 1, 20),
      "author",
      RemoteHotspot.Status.TO_REVIEW,
      null,
      new RemoteHotspot.Rule("rulekey", "rulename", "category", RemoteHotspot.Rule.Probability.HIGH, "", "", ""));
    when(wsHelper.getHotspot(any(), any())).thenReturn(Optional.of(hotspot));

    opener.open(PROJECT_KEY, "", CONNECTED_URL);

    assertThat(FileEditorManager.getInstance(getProject()).getOpenFiles())
      .extracting(VirtualFile::getName)
      .containsOnly("MyFile.java");
  }

  private void bindProject(String projectKey, String serverId) {
    registerConnection();
    getProjectSettings().setBindingEnabled(true);
    getProjectSettings().setProjectKey(projectKey);
    getProjectSettings().setConnectionName(serverId);
  }

  private void registerConnection() {
    getGlobalSettings().setServerConnections(
      Collections.singletonList(ServerConnection.newBuilder().setHostUrl(CONNECTED_URL).setName(SERVER_ID).build())
    );
  }

  private SecurityHotspotOpener opener;
}
