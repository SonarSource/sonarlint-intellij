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
package org.sonarlint.intellij.clion;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeProfileInfo;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageUtils;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.psi.OCParsedLanguageAndConfiguration;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import javax.annotation.Nullable;

public class AnalyzerConfiguration {
  private final Project project;
  private final CMakeWorkspace cMakeWorkspace;

  public AnalyzerConfiguration(@NotNull Project project) {
    this.project = project;
    cMakeWorkspace = CMakeWorkspace.getInstance(project);
  }

  /**
   * Inspired from ShowCompilerInfoForFile and ClangTidyAnnotator
   */
  public ConfigurationResult getConfiguration(VirtualFile file) {
    OCPsiFile psiFile = ApplicationManager.getApplication().<OCPsiFile>runReadAction(() -> OCLanguageUtils.asOCPsiFile(project, file));
    if (psiFile == null || !psiFile.isInProjectSources()) {
      return new ConfigurationResult(psiFile + " not in project sources");
    }
    OCResolveConfiguration configuration = null;
    OCLanguageKind languageKind;
    OCParsedLanguageAndConfiguration languageAndConfiguration = psiFile.getParsedLanguageAndConfiguration();
    if (languageAndConfiguration != null) {
      configuration = languageAndConfiguration.getConfiguration();
      languageKind = languageAndConfiguration.getLanguageKind();
    } else {
      languageKind = psiFile.getKind();
    }
    if (configuration == null) {
      configuration = ApplicationManager.getApplication().<OCResolveConfiguration>runReadAction(
        () -> OCInclusionContextUtil.getResolveRootAndActiveConfiguration(file, project).getConfiguration());
    }
    if (configuration == null) {
      return ConfigurationResult.skip("configuration not found");
    }
    if (usingRemoteToolchain(configuration)) {
      return ConfigurationResult.skip("use a remote toolchain");
    }
    OCCompilerSettings compilerSettings = configuration.getCompilerSettings(psiFile.getKind(), file);
    OCCompilerKind compilerKind = compilerSettings.getCompilerKind();
    if (compilerKind == null) {
      return ConfigurationResult.skip("compiler kind not found");
    }
    String cFamilyCompiler = mapToCFamilyCompiler(compilerKind);
    if (cFamilyCompiler == null) {
      return ConfigurationResult.skip("unsupported compiler " + compilerKind.getDisplayName());
    }
    return ConfigurationResult.of(new Configuration(file, compilerSettings, "clang", getSonarLanguage(languageKind), psiFile.isHeader()));
  }

  @Nullable
  static String mapToCFamilyCompiler(OCCompilerKind compilerKind) {
    if ((compilerKind instanceof GCCCompilerKind) || (compilerKind instanceof ClangCompilerKind)) {
      return "clang";
    }
    return null;
  }

  @Nullable
  static Language getSonarLanguage(OCLanguageKind languageKind) {
    if (languageKind.equals(CLanguageKind.C)) {
      return Language.C;
    } else if (languageKind.equals(CLanguageKind.CPP)) {
      return Language.CPP;
    } else if (languageKind.equals(CLanguageKind.OBJ_C)) {
      return Language.OBJC;
    } else {
      return null;
    }
  }

  private boolean usingRemoteToolchain(OCResolveConfiguration configuration) {
    CMakeConfiguration cMakeConfiguration = cMakeWorkspace.getCMakeConfigurationFor(configuration);
    if (cMakeConfiguration == null) {
      // remote toolchains are supported only for CMake projects
      return false;
    }
    CMakeProfileInfo profileInfo;
    try {
      profileInfo = cMakeWorkspace.getProfileInfoFor(cMakeConfiguration);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
    CPPEnvironment environment = profileInfo.getEnvironment();
    return environment != null && environment.getToolSet().isRemote();
  }

  public static class ConfigurationResult {
    @Nullable
    private final Configuration configuration;
    @Nullable
    private final String skipReason;

    private ConfigurationResult(Configuration configuration) {
      this.configuration = configuration;
      this.skipReason = null;
    }

    private ConfigurationResult(@Nullable String skipReason) {
      this.skipReason = skipReason;
      this.configuration = null;
    }

    public boolean hasConfiguration() {
      return configuration != null;
    }

    public Configuration getConfiguration() {
      if (!hasConfiguration()) {
        throw new UnsupportedOperationException();
      }
      return configuration;
    }

    public String getSkipReason() {
      if (hasConfiguration()) {
        throw new UnsupportedOperationException();
      }
      return skipReason;
    }

    public static ConfigurationResult of(Configuration configuration) {
      return new ConfigurationResult(configuration);
    }

    public static ConfigurationResult skip(String skipReason) {
      return new ConfigurationResult(skipReason);
    }
  }

  public static class Configuration {
    final VirtualFile virtualFile;
    final OCCompilerSettings compilerSettings;
    final String compiler;
    final boolean isHeaderFile;
    @Nullable
    final Language sonarLanguage;

    public Configuration(VirtualFile virtualFile, OCCompilerSettings compilerSettings, String compiler, @Nullable Language sonarLanguage, boolean isHeaderFile) {
      this.virtualFile = virtualFile;
      this.compilerSettings = compilerSettings;
      this.compiler = compiler;
      this.sonarLanguage = sonarLanguage;
      this.isHeaderFile = isHeaderFile;
    }
  }
}
