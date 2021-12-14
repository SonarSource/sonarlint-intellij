/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.module.Module;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintAnalyzerTest extends AbstractSonarLintLightTests {
  private ProjectBindingManager projectBindingManager = mock(ProjectBindingManager.class);
  private SonarLintFacade facade = mock(SonarLintFacade.class);

  private SonarLintAnalyzer analyzer;

  @Before
  public void prepare() throws InvalidBindingException {
    replaceProjectService(ProjectBindingManager.class, projectBindingManager);
    analyzer = new SonarLintAnalyzer(getProject());
    when(projectBindingManager.getFacade(getModule(), true)).thenReturn(facade);
    when(facade.startAnalysis(any(Module.class), anyList(), any(IssueListener.class), anyMap(), any(ClientProgressMonitor.class))).thenReturn(new AnalysisResults());
  }

  @Test
  public void testAnalysis() {
    var file = myFixture.copyFileToProject("foo.php", "foo.php");
    var listener = mock(IssueListener.class);

    analyzer.analyzeModule(getModule(), Collections.singleton(file), listener, mock(ClientProgressMonitor.class));

    verify(facade).startAnalysis(eq(getModule()), anyList(), eq(listener), anyMap(), any(ClientProgressMonitor.class));
  }
}
