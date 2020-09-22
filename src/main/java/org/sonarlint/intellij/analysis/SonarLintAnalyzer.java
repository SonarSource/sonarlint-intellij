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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

public class SonarLintAnalyzer {

  private final Project myProject;

  public SonarLintAnalyzer(Project project) {
    myProject = project;
  }

  public AnalysisResults analyzeModule(Module module, Collection<VirtualFile> filesToAnalyze, IssueListener listener, ProgressMonitor progressMonitor) {
    // Configure plugin properties. Nothing might be done if there is no configurator available for the extensions loaded in runtime.
    long start = System.currentTimeMillis();
    Map<String, String> pluginProps = new HashMap<>();
    List<AnalysisConfigurator> analysisConfigurators = AnalysisConfigurator.EP_NAME.getExtensionList();
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    if (analysisConfigurators.isEmpty()) {
      console.info("No analysis configurators found");
    }
    for (AnalysisConfigurator config : analysisConfigurators) {
      console.debug("Configuring analysis with " + config.getClass().getName());
      pluginProps.putAll(config.configure(module));
    }

    // configure files
    List<ClientInputFile> inputFiles = getInputFiles(module, filesToAnalyze);

    // Analyze

    try {
      ProjectBindingManager projectBindingManager = SonarLintUtils.getService(myProject, ProjectBindingManager.class);
      SonarLintFacade facade = projectBindingManager.getFacade(true);

      String what;
      if (filesToAnalyze.size() == 1) {
        what = "'" + filesToAnalyze.iterator().next().getName() + "'";
      } else {
        what = filesToAnalyze.size() + " files";
      }

      console.info("Analysing " + what + "...");
      AnalysisResults result = facade.startAnalysis(inputFiles, listener, pluginProps, progressMonitor);
      console.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
      SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
      if (result.languagePerFile().size() == 1 && result.failedAnalysisFiles().isEmpty()) {
        telemetry.analysisDoneOnSingleLanguage(Optional.ofNullable(result.languagePerFile().values().iterator().next()).map(Language::getLanguageKey).orElse(null), (int) (System.currentTimeMillis() - start));
      } else {
        telemetry.analysisDoneOnMultipleFiles();
      }
      return result;
    } catch (InvalidBindingException e) {
      // should not happen, as analysis should not have been submitted in this case.
      throw new IllegalStateException(e);
    }
  }

  private List<ClientInputFile> getInputFiles(Module module, Collection<VirtualFile> filesToAnalyze) {
    return ApplicationManager.getApplication().<List<ClientInputFile>>runReadAction(() -> filesToAnalyze.stream()
      .map(f -> createClientInputFile(module, f))
      .filter(Objects::nonNull)
      .collect(Collectors.toList())
    );
  }

  @CheckForNull
  private ClientInputFile createClientInputFile(Module module, VirtualFile virtualFile) {
    boolean test = TestSourcesFilter.isTestSources(virtualFile, module.getProject());
    Charset charset = getEncoding(virtualFile);
    String relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, virtualFile);
    if (relativePath != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      if (fileDocumentManager.isFileModified(virtualFile)) {
        return new DefaultClientInputFile(virtualFile, relativePath, test, charset, fileDocumentManager.getDocument(virtualFile));
      } else {
        return new DefaultClientInputFile(virtualFile, relativePath, test, charset);
      }
    }
    return null;
  }

  private Charset getEncoding(VirtualFile f) {
    EncodingProjectManager encodingProjectManager = EncodingProjectManager.getInstance(myProject);
    Charset encoding = encodingProjectManager.getEncoding(f, true);
    if (encoding != null) {
      return encoding;
    }
    return Charset.defaultCharset();
  }
}
