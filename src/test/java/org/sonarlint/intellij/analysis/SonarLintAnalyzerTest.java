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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.telemetry.SonarLintTelemetryImpl;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintAnalyzerTest extends SonarTest {
  private ProjectBindingManager projectBindingManager = mock(ProjectBindingManager.class);
  private EncodingProjectManager encodingProjectManager = mock(EncodingProjectManager.class);
  private SonarLintConsole console = mock(SonarLintConsole.class);
  private AnalysisConfigurator configurator = mock(AnalysisConfigurator.class);
  private SonarLintFacade facade = mock(SonarLintFacade.class);
  private FileDocumentManager fileDocumentManager = mock(FileDocumentManager.class);
  private VirtualFileTestPredicate testPredicate = mock(VirtualFileTestPredicate.class);
  private SonarLintTelemetryImpl telemetry = mock(SonarLintTelemetryImpl.class);
  private SonarLintAppUtils appUtils = mock(SonarLintAppUtils.class);

  private SonarLintAnalyzer analyzer;

  @Before
  public void prepare() throws InvalidBindingException {
    analyzer = new SonarLintAnalyzer(projectBindingManager, encodingProjectManager, console, fileDocumentManager, telemetry, appUtils);
    when(projectBindingManager.getFacade(true)).thenReturn(facade);
    when(facade.startAnalysis(anyList(), any(IssueListener.class), anyMap(), any(ProgressMonitor.class))).thenReturn(new DefaultAnalysisResult());
    super.register(module, VirtualFileTestPredicate.class, testPredicate);
    super.register(module, AnalysisConfigurator.class, configurator);
    super.registerEP(AnalysisConfigurator.EP_NAME, AnalysisConfigurator.class);
    when(project.getBasePath()).thenReturn("project");
  }

  @Test
  public void testAnalysis() {
    VirtualFile file = mock(VirtualFile.class);
    when(file.getPath()).thenReturn("project/testFile");
    IssueListener listener = mock(IssueListener.class);
    when(app.getDefaultModalityState()).thenReturn(ModalityState.NON_MODAL);
    analyzer.analyzeModule(module, Collections.singleton(file), listener, mock(ProgressMonitor.class));
    verify(facade).startAnalysis(anyList(), eq(listener), eq(Collections.emptyMap()), any(ProgressMonitor.class));
  }
}
