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
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.IssuesViewTabOpener;
import org.sonarlint.intellij.core.SecurityHotspotMatcher;
import org.sonarlint.intellij.editor.SonarLintHighlighting;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityHotspotOpenerTest extends AbstractSonarLintLightTests {
  public static final String FILE_PATH = "com/sonarsource/sample/MyFile.java";

  @Before
  public void prepare() {
    WsHelperImpl wsHelper = mock(WsHelperImpl.class);
    IssuesViewTabOpener toolWindow = mock(IssuesViewTabOpener.class);
    SonarLintHighlighting sonarLintHighlighting = mock(SonarLintHighlighting.class);
    opener = new SecurityHotspotOpener(mock(ServerConfiguration.class), new SecurityHotspotMatcher(getProject()), wsHelper);

    // XXX avoid headless mode on CI to uncomment
//    loadToolWindow();

    // XXX remove when tool window loaded properly
    replaceProjectService(IssuesViewTabOpener.class, toolWindow);
    replaceProjectService(SonarLintHighlighting.class, sonarLintHighlighting);


    RemoteHotspot hotspot = new RemoteHotspot("Very hotspot",
      FILE_PATH,
      new TextRange(1, 14, 1, 20),
      "author",
      RemoteHotspot.Status.TO_REVIEW,
      null,
      new RemoteHotspot.Rule("rulekey", "rulename", "category", RemoteHotspot.Rule.Probability.HIGH, "", "", ""));
    when(wsHelper.getHotspot(any(), any())).thenReturn(Optional.of(hotspot));
  }

  @Test
  public void it_should_open_the_primary_location_file_if_exists() {
    myFixture.copyFileToProject(FILE_PATH);

    opener.open(getProject(), "", "");

    assertThat(FileEditorManager.getInstance(getProject()).getOpenFiles())
      .extracting(VirtualFile::getName)
      .containsOnly("MyFile.java");
  }

  private SecurityHotspotOpener opener;
}
