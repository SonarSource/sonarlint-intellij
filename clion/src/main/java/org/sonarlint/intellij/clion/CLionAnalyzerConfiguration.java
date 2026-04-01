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
package org.sonarlint.intellij.clion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.util.HashMap;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafelyInSmartMode;

public class CLionAnalyzerConfiguration extends AnalyzerConfiguration {
  private final Project project;

  public CLionAnalyzerConfiguration(Project project) {
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
    if (!(psiFile instanceof OCPsiFile ocFile)) {
      return ConfigurationResult.skip(psiFile + " not an OCPsiFile");
    }
    if (!ocFile.isInProjectSources()) {
      return ConfigurationResult.skip(ocFile + " not in project sources");
    }
    OCResolveConfiguration configuration = null;
    OCLanguageKind languageKind = null;
    var languageAndConfiguration = ocFile.getParsedLanguageAndConfiguration();
    if (languageAndConfiguration != null) {
      configuration = languageAndConfiguration.getConfiguration();
      languageKind = languageAndConfiguration.getLanguageKind();
    } else {
      try {
        languageKind = ocFile.getKind();
      } catch (Exception e) {
        SonarLintConsole.get(project).error("Could not retrieve language kind", e);
      }
    }
    if (configuration == null) {
      configuration = getConfiguration(project, file);
    }
    if (configuration == null) {
      return ConfigurationResult.skip("configuration not found");
    }
    OCCompilerSettings compilerSettings;
    try {
      compilerSettings = configuration.getCompilerSettings(ocFile.getKind(), file);
    } catch (Exception e) {
      return ConfigurationResult.skip("compiler settings not found");
    }
    var compilerKind = compilerSettings.getCompilerKind();
    if (compilerKind == null) {
      return ConfigurationResult.skip("compiler kind not found");
    }
    var cFamilyCompiler = mapToCFamilyCompiler(compilerKind);
    if (cFamilyCompiler == null) {
      return ConfigurationResult.skip("unsupported compiler " + compilerKind.getDisplayName());
    }
    var properties = new HashMap<String, String>();
    if (ocFile.isHeader()) {
      properties.put("isHeaderFile", "true");
    }

    AnalyzerConfiguration.collectCompilerKindProperties(project, configuration, compilerKind, cFamilyCompiler, compilerSettings, languageKind, properties);

    var sonarLanguage = AnalyzerConfiguration.getSonarLanguage(languageKind);
    return ConfigurationResult.of(new Configuration(file, compilerSettings.getCompilerExecutable().getAbsolutePath(), compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW), cFamilyCompiler, sonarLanguage, properties));
  }

}
