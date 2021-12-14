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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

public class SonarLintAnalyzer {

  private final Project myProject;

  public SonarLintAnalyzer(Project project) {
    myProject = project;
  }

  public AnalysisResults analyzeModule(Module module, Collection<VirtualFile> filesToAnalyze, IssueListener listener, ClientProgressMonitor progressMonitor) {
    // Configure plugin properties. Nothing might be done if there is no configurator available for the extensions loaded in runtime.
    var start = System.currentTimeMillis();
    var console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    var contributedConfigurations = getConfigurationFromConfiguratorEP(module, filesToAnalyze, console);

    var contributedProperties = collectContributedExtraProperties(console, contributedConfigurations);

    var contributedLanguages = collectContributedLanguages(console, contributedConfigurations);

    // configure files
    var inputFiles = getInputFiles(module, filesToAnalyze, contributedLanguages);

    // Analyze

    try {
      var projectBindingManager = SonarLintUtils.getService(myProject, ProjectBindingManager.class);
      var facade = projectBindingManager.getFacade(module, true);

      var what = filesToAnalyze.size() == 1 ?
        String.format("'%s'", filesToAnalyze.iterator().next().getName()) :
        String.format("%d files", filesToAnalyze.size());

      console.info("Analysing " + what + "...");
      var result = facade.startAnalysis(module, inputFiles, listener, contributedProperties, progressMonitor);
      console.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
      var telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
      if (result.languagePerFile().size() == 1 && result.failedAnalysisFiles().isEmpty()) {
        telemetry.analysisDoneOnSingleLanguage(result.languagePerFile().values().iterator().next(), (int) (System.currentTimeMillis() - start));
      } else {
        telemetry.analysisDoneOnMultipleFiles();
      }

      return result;
    } catch (InvalidBindingException e) {
      // should not happen, as analysis should not have been submitted in this case.
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private static Map<String, String> collectContributedExtraProperties(SonarLintConsole console, List<AnalysisConfigurator.AnalysisConfiguration> contributedConfigurations) {
    var contributedProperties = new HashMap<String, String>();
    for (var config : contributedConfigurations) {
      for (var entry : config.extraProperties.entrySet()) {
        if (contributedProperties.containsKey(entry.getKey()) && !Objects.equals(contributedProperties.get(entry.getKey()), entry.getValue())) {
          console.error("The same property " + entry.getKey() + " was contributed by multiple configurators with different values: " +
            contributedProperties.get(entry.getKey()) + " / " + entry.getValue());
        }
        contributedProperties.put(entry.getKey(), entry.getValue());
      }
    }
    return contributedProperties;
  }

  @NotNull
  private static Map<VirtualFile, Language> collectContributedLanguages(SonarLintConsole console, List<AnalysisConfigurator.AnalysisConfiguration> contributedConfigurations) {
    var contributedLanguages = new HashMap<VirtualFile, Language>();
    for (var config : contributedConfigurations) {
      for (var entry : config.forcedLanguages.entrySet()) {
        if (contributedLanguages.containsKey(entry.getKey()) && !Objects.equals(contributedLanguages.get(entry.getKey()), entry.getValue())) {
          console.error("The same file " + entry.getKey() + " has its language forced by multiple configurators with different values: " +
            contributedLanguages.get(entry.getKey()) + " / " + entry.getValue());
        }
        contributedLanguages.put(entry.getKey(), entry.getValue());
      }
    }
    return contributedLanguages;
  }

  @NotNull
  private static List<AnalysisConfigurator.AnalysisConfiguration> getConfigurationFromConfiguratorEP(Module module, Collection<VirtualFile> filesToAnalyze,
    SonarLintConsole console) {
    return AnalysisConfigurator.EP_NAME.getExtensionList().stream()
      .map(config -> {
        console.debug("Configuring analysis with " + config.getClass().getName());
        return config.configure(module, filesToAnalyze);
      })
      .collect(Collectors.toList());
  }

  private List<ClientInputFile> getInputFiles(Module module, Collection<VirtualFile> filesToAnalyze, Map<VirtualFile, Language> contributedLanguages) {
    return ApplicationManager.getApplication().<List<ClientInputFile>>runReadAction(() -> filesToAnalyze.stream()
      .map(f -> createClientInputFile(module, f, contributedLanguages.get(f)))
      .filter(Objects::nonNull)
      .collect(Collectors.toList()));
  }

  @CheckForNull
  public ClientInputFile createClientInputFile(Module module, VirtualFile virtualFile, @Nullable Language language) {
    var test = TestSourcesFilter.isTestSources(virtualFile, module.getProject());
    var charset = getEncoding(virtualFile);
    var relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, virtualFile);
    if (relativePath != null) {
      var fileDocumentManager = FileDocumentManager.getInstance();
      if (fileDocumentManager.isFileModified(virtualFile)) {
        return createInputFileFromDocument(virtualFile, language, test, charset, relativePath);
      } else {
        return new DefaultClientInputFile(virtualFile, relativePath, test, charset, null, readDocumentModificationStamp(virtualFile), language);
      }
    }
    return null;
  }

  private static DefaultClientInputFile createInputFileFromDocument(VirtualFile virtualFile, @org.jetbrains.annotations.Nullable Language language, boolean test, Charset charset,
    String relativePath) {
    return ReadAction.compute(() -> {
      var document = FileDocumentManager.getInstance().getDocument(virtualFile);
      var textInDocument = document != null ? document.getText() : null;
      var documentModificationStamp = document != null ? document.getModificationStamp() : 0;
      return new DefaultClientInputFile(virtualFile, relativePath, test, charset, textInDocument, documentModificationStamp, language);
    });
  }

  private static long readDocumentModificationStamp(VirtualFile virtualFile) {
    return ReadAction.compute(() -> {
      var document = FileDocumentManager.getInstance().getDocument(virtualFile);
      return document != null ? document.getModificationStamp() : 0;
    });
  }

  private Charset getEncoding(VirtualFile f) {
    var encodingProjectManager = EncodingProjectManager.getInstance(myProject);
    var encoding = encodingProjectManager.getEncoding(f, true);
    if (encoding != null) {
      return encoding;
    }
    return Charset.defaultCharset();
  }
}
