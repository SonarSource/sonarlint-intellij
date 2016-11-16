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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import javax.annotation.Nullable;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SonarLintAnalyzer {
  private final ProjectBindingManager projectBindingManager;
  private final EncodingProjectManager encodingProjectManager;
  private final SonarLintConsole console;
  private final FileDocumentManager fileDocumentManager;
  private final Application app;

  public SonarLintAnalyzer(ProjectBindingManager projectBindingManager, EncodingProjectManager encodingProjectManager,
    SonarLintConsole console, FileDocumentManager fileDocumentManager, Application app) {
    this.projectBindingManager = projectBindingManager;
    this.encodingProjectManager = encodingProjectManager;
    this.console = console;
    this.fileDocumentManager = fileDocumentManager;
    this.app = app;
  }

  public AnalysisResults analyzeModule(Module module, Collection<VirtualFile> filesToAnalyze, IssueListener listener) {
    // Configure plugin properties. Nothing might be done if there is no configurator available for the extensions loaded in runtime.
    Map<String, String> pluginProps = new HashMap<>();
    AnalysisConfigurator[] analysisConfigurators = AnalysisConfigurator.EP_NAME.getExtensions();
    if (analysisConfigurators.length > 0) {
      for (AnalysisConfigurator config : analysisConfigurators) {
        console.debug("Configuring analysis with " + config.getClass().getName());
        pluginProps.putAll(config.configure(module));
      }
    } else {
      console.info("No analysis configurator found");
    }

    // configure files
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    List<ClientInputFile> inputFiles = getInputFiles(moduleRootManager, filesToAnalyze);

    // Analyze
    long start = System.currentTimeMillis();

    SonarLintFacade facade = projectBindingManager.getFacadeForAnalysis();

    String what;
    if (filesToAnalyze.size() == 1) {
      what = "'" + filesToAnalyze.iterator().next().getName() + "'";
    } else {
      what = Integer.toString(filesToAnalyze.size()) + " files";
    }

    console.info("Analysing " + what + "...");
    AnalysisResults result = facade.startAnalysis(inputFiles, listener, pluginProps);
    console.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
    return result;
  }

  private List<ClientInputFile> getInputFiles(ModuleRootManager moduleRootManager, Collection<VirtualFile> filesToAnalyze) {
    Collection<String> testFolderPrefix = findTestFolderPrefixes(moduleRootManager);
    List<ClientInputFile> inputFiles = new LinkedList<>();

    AccessToken token = app.acquireReadActionLock();
    try {
      for (VirtualFile f : filesToAnalyze) {
        boolean test = isTestFile(testFolderPrefix, f);
        Charset charset = getEncoding(f);
        if (fileDocumentManager.isFileModified(f)) {
          inputFiles.add(new DefaultClientInputFile(f, test, charset, fileDocumentManager.getDocument(f)));
        } else {
          inputFiles.add(new DefaultClientInputFile(f, test, charset));
        }
      }
    } finally {
      token.finish();
    }

    return inputFiles;
  }

  private static Collection<String> findTestFolderPrefixes(ModuleRootManager moduleRootManager) {
    Collection<String> testFolderPrefix = new ArrayList<>();
    for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null && sourceFolder.isTestSource()) {
          testFolderPrefix.add(file.getPath());
        }
      }
    }

    return testFolderPrefix;
  }

  private Charset getEncoding(@Nullable VirtualFile f) {
    if (f != null) {
      Charset encoding = encodingProjectManager.getEncoding(f, true);
      if (encoding != null) {
        return encoding;
      }
    }
    return Charset.defaultCharset();
  }

  private static boolean isTestFile(Collection<String> testFolderPrefix, VirtualFile f) {
    String filePath = f.getPath();
    for (String testPrefix : testFolderPrefix) {
      if (filePath.startsWith(testPrefix)) {
        return true;
      }
    }
    return false;
  }

}
