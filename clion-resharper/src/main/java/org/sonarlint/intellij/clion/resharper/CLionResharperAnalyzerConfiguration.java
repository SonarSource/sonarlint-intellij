/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.execution.ExecutionException;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;
import com.jetbrains.rider.cpp.fileType.CppLanguage;
import com.jetbrains.rider.cpp.fileType.psi.CppFile;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.clion.common.AnalyzerConfiguration;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;

public class CLionResharperAnalyzerConfiguration extends AnalyzerConfiguration {
  private final Project project;

  public CLionResharperAnalyzerConfiguration(@NotNull Project project) {
    this.project = project;
  }

  public ConfigurationResult getConfiguration(VirtualFile file) {
    var configuration = computeReadActionSafely(file, project, () -> getConfigurationAction(file));
    return configuration != null ? configuration : ConfigurationResult.skip("The file is invalid or the project is being closed");
  }

  /**
   * Inspired from ShowCompilerInfoForFile and ClangTidyAnnotator
   */
  public ConfigurationResult getConfigurationAction(VirtualFile file) {
    var psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof CppFile cppFile)) {
      return new ConfigurationResult(psiFile + " not a CppFile");
    }
    if (!ProjectFileIndex.getInstance(cppFile.getProject()).isInSource(file)) {
      return new ConfigurationResult(cppFile + " not in project sources");
    }
    var configuration = getConfiguration(project, file);
    var cLanguageKind = getLanguageKind(cppFile.getLanguage());
    if (cLanguageKind == null) {
      return ConfigurationResult.skip("not from a C language");
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

    if (usingRemoteOrWslToolchain(configuration)) {
      collectPropertiesForRemoteToolchain(compilerSettings, properties);
    } else if (compilerKind instanceof MSVCCompilerKind) {
      collectMSVCProperties(compilerSettings, properties);
    }

    var sonarLanguage = getSonarLanguage(cppFile.getLanguage());
    if (sonarLanguage != null) {
      properties.put("sonarLanguage", LANGUAGE_KEYS.get(sonarLanguage));
    }
    return ConfigurationResult.of(new Configuration(file, compilerSettings.getCompilerExecutable().getAbsolutePath(), compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW), cFamilyCompiler, sonarLanguage, properties));
  }

  @Nullable
  static CLanguageKind getLanguageKind(Language language) {
    if (language.getDisplayName().equals(CLanguageKind.C.getDisplayName())) {
      return CLanguageKind.C;
    } else if (language.equals(CppLanguage.INSTANCE)) {
      return CLanguageKind.CPP;
    } else if (language.getDisplayName().equals(CLanguageKind.OBJ_C.getDisplayName())) {
      return CLanguageKind.OBJ_C;
    } else {
      return null;
    }
  }

  @Nullable
  static ForcedLanguage getSonarLanguage(Language language) {
    if (language.getDisplayName().equals(CLanguageKind.C.getDisplayName())) {
      return ForcedLanguage.C;
    } else if (language.equals(CppLanguage.INSTANCE)) {
      return ForcedLanguage.CPP;
    } else if (language.getDisplayName().equals(CLanguageKind.OBJ_C.getDisplayName())) {
      return ForcedLanguage.OBJC;
    } else {
      return null;
    }
  }

  private boolean usingRemoteOrWslToolchain(OCResolveConfiguration configuration) {
    final var initializedWorkspaces = CidrWorkspace.getInitializedWorkspaces(project);
    CPPEnvironment cppEnvironment = null;
    for (var initializedWorkspace : initializedWorkspaces) {
      if (initializedWorkspace instanceof CMakeWorkspace cMakeWorkspace) {
        cppEnvironment = getCMakeCppEnvironment(cMakeWorkspace, configuration);
      } else {
        cppEnvironment = tryReflection(initializedWorkspace, project);
        if (cppEnvironment != null) {
          break;
        }
      }
    }
    return cppEnvironment != null && (cppEnvironment.getToolSet().isWSL() || cppEnvironment.getToolSet().isDocker() || isSupportingAndUsingSsh(cppEnvironment));
  }

  private boolean isSupportingAndUsingSsh(CPPEnvironment cppEnvironment) {
    try {
      var method = cppEnvironment.getClass().getMethod("isSsh");
      var result = method.invoke(cppEnvironment);
      if (result instanceof Boolean supporting) {
        return supporting;
      }
      return false;
    } catch (ReflectiveOperationException e) {
      SonarLintConsole.get(project).debug("Could not support SSH");
      return false;
    }
  }

  @Nullable
  private CPPEnvironment getCMakeCppEnvironment(CMakeWorkspace cMakeWorkspace, OCResolveConfiguration configuration) {
    var cMakeConfiguration = cMakeWorkspace.getCMakeConfigurationFor(configuration);
    if (cMakeConfiguration == null) {
      SonarLintConsole.get(project).debug("cMakeConfiguration is null");
      return null;
    }
    try {
      return cMakeWorkspace.getProfileInfoFor(cMakeConfiguration).getEnvironment();
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

}
