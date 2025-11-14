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

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculatorBase;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;
import com.jetbrains.rider.cpp.fileType.psi.CppFile;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarlint.intellij.clion.AnalyzerConfiguration;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafelyInSmartMode;

public class CLionResharperAnalyzerConfiguration extends AnalyzerConfiguration {

  private static final Map<OCLanguageKind, ForcedLanguage> SUPPORTED_LANGUAGES = Map.of(
    CLanguageKind.C, ForcedLanguage.C,
    CLanguageKind.CPP, ForcedLanguage.CPP,
    CLanguageKind.OBJ_C, ForcedLanguage.OBJC);
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
    if (!SUPPORTED_LANGUAGES.containsKey(cLanguageKind)) {
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

    if (isRemoteOrWslToolchainSupported() && usingRemoteOrWslToolchain(configuration)) {
      collectPropertiesForRemoteToolchain(compilerSettings, properties);
    } else if (compilerKind instanceof MSVCCompilerKind) {
      // For MSVC, we collect built-in headers only, and the driver on CFamily side still handles '/external:I' arguments.
      collectDefinesAndIncludes(compilerSettings, properties, HeadersSearchPath::isBuiltInHeaders);
    } else if ("iar".equals(cFamilyCompiler)) {
      // For IAR, we are interested in all headers. This is necessary to support the C_INCLUDE environment variable (as it is a user header).
      collectDefinesAndIncludes(compilerSettings, properties, h -> true);
    } else {
      SonarLintConsole.get(project).debug("Did not collect any properties for " + compilerKind.getDisplayName() + " compiler");
    }

    var sonarLanguage = getSonarLanguage(cLanguageKind);
    if (sonarLanguage != null) {
      properties.put("sonarLanguage", LANGUAGE_KEYS.get(sonarLanguage));
    }
    return ConfigurationResult.of(new Configuration(file, compilerSettings.getCompilerExecutable().getAbsolutePath(), compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW), cFamilyCompiler, sonarLanguage, properties));
  }

  @Nullable
  static ForcedLanguage getSonarLanguage(OCLanguageKind language) {
    return SUPPORTED_LANGUAGES.get(language);
  }

  private boolean usingRemoteOrWslToolchain(OCResolveConfiguration configuration) {
    final var initializedWorkspaces = CidrWorkspace.getInitializedWorkspaces(project);
    CPPEnvironment cppEnvironment;
    for (var initializedWorkspace : initializedWorkspaces) {
      if (initializedWorkspace instanceof CMakeWorkspace cMakeWorkspace) {
        cppEnvironment = getCMakeCppEnvironment(cMakeWorkspace, configuration);
      } else {
        cppEnvironment = tryReflection(initializedWorkspace, project);
      }
      if (cppEnvironment != null) {
        return cppEnvironment.getToolSet().isWSL() || cppEnvironment.getToolSet().isDocker() || cppEnvironment.getToolSet().isRemote();
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
