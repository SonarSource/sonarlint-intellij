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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;

import java.nio.charset.Charset;
import java.util.ArrayList;
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
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.util.SonarLintAppUtils;
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
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    List<AnalysisConfigurator.AnalysisConfiguration> contributedConfigurations = getConfigurationFromConfiguratorEP(module, filesToAnalyze, console);

    Map<String, String> contributedProperties = collectContributedExtraProperties(console, contributedConfigurations);

    Map<VirtualFile, Language> contributedLanguages = collectContributedLanguages(console, contributedConfigurations);

    // configure files
    List<ClientInputFile> inputFiles = getInputFiles(module, filesToAnalyze, contributedLanguages);

    // Analyze

    try {
      ProjectBindingManager projectBindingManager = SonarLintUtils.getService(myProject, ProjectBindingManager.class);
      SonarLintFacade facade = projectBindingManager.getFacade(module, true);

      String what;
      if (filesToAnalyze.size() == 1) {
        what = "'" + filesToAnalyze.iterator().next().getName() + "'";
      } else {
        what = filesToAnalyze.size() + " files";
      }

      console.info("Analysing " + what + "...");
      AnalysisResults result = facade.startAnalysis(module, inputFiles, listener, contributedProperties, progressMonitor);
      console.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
      SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
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
    Map<String, String> contributedProperties = new HashMap<>();
    for (AnalysisConfigurator.AnalysisConfiguration config : contributedConfigurations) {
      for (Map.Entry<String, String> entry : config.extraProperties.entrySet()) {
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
    Map<VirtualFile, Language> contributedLanguages = new HashMap<>();
    for (AnalysisConfigurator.AnalysisConfiguration config : contributedConfigurations) {
      for (Map.Entry<VirtualFile, Language> entry : config.forcedLanguages.entrySet()) {
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
    List<AnalysisConfigurator.AnalysisConfiguration> contributedConfigurations = new ArrayList<>();
    for (AnalysisConfigurator config : AnalysisConfigurator.EP_NAME.getExtensionList()) {
      console.debug("Configuring analysis with " + config.getClass().getName());
      contributedConfigurations.add(config.configure(module, filesToAnalyze));
    }
    return contributedConfigurations;
  }

  private List<ClientInputFile> getInputFiles(Module module, Collection<VirtualFile> filesToAnalyze, Map<VirtualFile, Language> contributedLanguages) {
    return ApplicationManager.getApplication().<List<ClientInputFile>>runReadAction(() -> filesToAnalyze.stream()
      .map(f -> createClientInputFile(module, f, contributedLanguages.get(f)))
      .filter(Objects::nonNull)
      .collect(Collectors.toList()));
  }

  @CheckForNull
  public ClientInputFile createClientInputFile(Module module, VirtualFile virtualFile, @Nullable Language language) {
    boolean test = TestSourcesFilter.isTestSources(virtualFile, module.getProject());
    Charset charset = getEncoding(virtualFile);
    String relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, virtualFile);
    if (relativePath != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
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
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      String textInDocument = document != null ? document.getText() : null;
      long documentModificationStamp = document != null ? document.getModificationStamp() : 0;
      return new DefaultClientInputFile(virtualFile, relativePath, test, charset, textInDocument, documentModificationStamp, language);
    });
  }

  private static long readDocumentModificationStamp(VirtualFile virtualFile) {
    return ReadAction.compute(() -> {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      return document != null ? document.getModificationStamp() : 0;
    });
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
