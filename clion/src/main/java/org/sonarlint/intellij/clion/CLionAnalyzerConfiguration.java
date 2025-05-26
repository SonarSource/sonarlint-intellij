/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;
import java.util.HashMap;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.clion.common.AnalyzerConfiguration;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafelyInSmartMode;

public class CLionAnalyzerConfiguration extends AnalyzerConfiguration {
  private final Project project;

  public CLionAnalyzerConfiguration(@NotNull Project project) {
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
      return new ConfigurationResult(psiFile + " not an OCPsiFile");
    }
    if (!ocFile.isInProjectSources()) {
      return new ConfigurationResult(ocFile + " not in project sources");
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

    if (isRemoteOrWslToolchainSupported() && usingRemoteOrWslToolchain(configuration)) {
      collectPropertiesForRemoteToolchain(compilerSettings, properties);
    } else if (compilerKind instanceof MSVCCompilerKind) {
      // For MSVC, we collect built-in headers only, and the driver on CFamily side still handles '/external:I' arguments.
      collectDefinesAndIncludes(compilerSettings, properties, HeadersSearchPath::isBuiltInHeaders);
    } else if ("iar".equals(cFamilyCompiler)) {
      // For IAR, we are interested in all headers. This is necessary to support the C_INCLUDE environment variable (as it is a user header).
      collectDefinesAndIncludes(compilerSettings, properties, h -> true);
    }

    var sonarLanguage = getSonarLanguage(languageKind);
    if (sonarLanguage != null) {
      properties.put("sonarLanguage", LANGUAGE_KEYS.get(sonarLanguage));
    }
    return ConfigurationResult.of(new Configuration(file, compilerSettings.getCompilerExecutable().getAbsolutePath(), compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW), cFamilyCompiler, sonarLanguage, properties));
  }

  @Nullable
  static ForcedLanguage getSonarLanguage(@Nullable OCLanguageKind languageKind) {
    if (CLanguageKind.C.equals(languageKind)) {
      return ForcedLanguage.C;
    } else if (CLanguageKind.CPP.equals(languageKind)) {
      return ForcedLanguage.CPP;
    } else if (CLanguageKind.OBJ_C.equals(languageKind)) {
      return ForcedLanguage.OBJC;
    } else {
      return null;
    }
  }

  private boolean usingRemoteOrWslToolchain(OCResolveConfiguration configuration) {
    final var initializedWorkspaces = CidrWorkspace.getInitializedWorkspaces(project);
    for (var initializedWorkspace : initializedWorkspaces) {
      if (initializedWorkspace instanceof CMakeWorkspace) {
        var cppEnvironment = getCMakeCppEnvironment(initializedWorkspace, configuration);
        if (cppEnvironment != null) {
          return cppEnvironment.getToolSet().isRemote() || cppEnvironment.getToolSet().isWSL() || cppEnvironment.getToolSet().isDocker();
        }
      } else {
        var cppEnvironment = tryReflection(initializedWorkspace, project);
        if (cppEnvironment != null) {
          return cppEnvironment.getToolSet().isRemote() || cppEnvironment.getToolSet().isWSL() || cppEnvironment.getToolSet().isDocker();
        }
      }
    }
    SonarLintConsole.get(project).debug("Not using remote or WSL toolchain");
    return false;
  }

  private boolean isRemoteOrWslToolchainSupported() {
    try {
      Class.forName("com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace");
      return true;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      SonarLintConsole.get(project).debug("Could not support remote or WSL toolchain");
      return false;
    }
  }

  @Nullable
  private CPPEnvironment getCMakeCppEnvironment(CidrWorkspace cdirWorkspace, OCResolveConfiguration configuration) {
    var cMakeWorkspace = (CMakeWorkspace) cdirWorkspace;
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
