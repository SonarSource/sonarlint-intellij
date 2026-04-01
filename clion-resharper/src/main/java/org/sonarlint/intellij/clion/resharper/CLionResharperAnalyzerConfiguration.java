/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.clion.resharper;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculatorBase;
import com.jetbrains.rider.cpp.fileType.psi.CppFile;
import java.util.HashMap;
import org.sonarlint.intellij.clion.AnalyzerConfiguration;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafelyInSmartMode;

public class CLionResharperAnalyzerConfiguration extends AnalyzerConfiguration {

  private final Project project;

  public CLionResharperAnalyzerConfiguration(Project project) {
    this.project = project;
  }

  public ConfigurationResult getConfiguration(VirtualFile file) {
    var configuration = computeReadActionSafelyInSmartMode(file, project, () -> getConfigurationAction(file));
    return configuration != null ? configuration : ConfigurationResult.skip("The file is invalid or the project is being closed");
  }

  /**
   * Inspired from ShowCompilerInfoForFile and ClangTidyAnnotator
   */
  public ConfigurationResult getConfigurationAction(VirtualFile file) {
    var psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof CppFile cppFile)) {
      return ConfigurationResult.skip(psiFile + " not a CppFile");
    }
    if (!ProjectFileIndex.getInstance(cppFile.getProject()).isInSource(file)) {
      return ConfigurationResult.skip(cppFile + " not in project sources");
    }
    var configuration = getConfiguration(project, file);

    // get the language kind of the file
    // do not use psiFile.getLanguage() because it always returns C++ in resharper nova mode
    var cLanguageKind = OCLanguageKindCalculatorBase.tryPsiFile(psiFile);
    if (cLanguageKind == null) {
      return ConfigurationResult.skip("language not found");
    }
    if (AnalyzerConfiguration.getSonarLanguage(cLanguageKind) == null) {
      return ConfigurationResult.skip("language not supported: " + cLanguageKind.getDisplayName());
    }

    if (configuration == null) {
      return ConfigurationResult.skip("configuration not found");
    }
    var compilerSettings = configuration.getCompilerSettings(cLanguageKind, file);
    var compilerKind = compilerSettings.getCompilerKind();
    if (compilerKind == null) {
      return ConfigurationResult.skip("compiler kind not found");
    }
    var cFamilyCompiler = mapToCFamilyCompiler(compilerKind);
    if (cFamilyCompiler == null) {
      return ConfigurationResult.skip("unsupported compiler " + compilerKind.getDisplayName());
    }
    var properties = new HashMap<String, String>();

    if (OCFileTypeHelpers.isHeaderFile(cppFile)) {
      properties.put("isHeaderFile", "true");
    }

    AnalyzerConfiguration.collectCompilerKindProperties(project, configuration, compilerKind, cFamilyCompiler, compilerSettings, cLanguageKind, properties);

    var sonarLanguage = AnalyzerConfiguration.getSonarLanguage(cLanguageKind);
    return ConfigurationResult.of(new Configuration(file, compilerSettings.getCompilerExecutable().getAbsolutePath(), compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW), cFamilyCompiler, sonarLanguage, properties));
  }

}
