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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeProfileInfo;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCParsedLanguageAndConfiguration;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.common.Language;

public class AnalyzerConfiguration {
  private final Project project;
  private final CMakeWorkspace cMakeWorkspace;
  private static Method preprocessorDefinesMethod;

  public AnalyzerConfiguration(@NotNull Project project) {
    this.project = project;
    cMakeWorkspace = CMakeWorkspace.getInstance(project);
  }

  public ConfigurationResult getConfiguration(VirtualFile file) {
    return ApplicationManager.getApplication().<ConfigurationResult>runReadAction(() -> getConfigurationAction(file));
  }

  /**
   * Inspired from ShowCompilerInfoForFile and ClangTidyAnnotator
   */
  public ConfigurationResult getConfigurationAction(VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof OCPsiFile)) {
      return new ConfigurationResult(psiFile + " not an OCPsiFile");
    }
    OCFile ocFile = ((OCPsiFile) psiFile).getOCFile();
    if (!ocFile.isInProjectSources()) {
      return new ConfigurationResult(ocFile + " not in project sources");
    }
    OCResolveConfiguration configuration = null;
    OCLanguageKind languageKind;
    OCParsedLanguageAndConfiguration languageAndConfiguration = ocFile.getParsedLanguageAndConfiguration();
    if (languageAndConfiguration != null) {
      configuration = languageAndConfiguration.getConfiguration();
      languageKind = languageAndConfiguration.getLanguageKind();
    } else {
      languageKind = ocFile.getKind();
    }
    if (configuration == null) {
      configuration = OCInclusionContextUtil.getResolveRootAndActiveConfiguration(file, project).getConfiguration();
    }
    if (configuration == null) {
      return ConfigurationResult.skip("configuration not found");
    }
    OCCompilerSettings compilerSettings = configuration.getCompilerSettings(ocFile.getKind(), file);
    OCCompilerKind compilerKind = compilerSettings.getCompilerKind();
    if (compilerKind == null) {
      return ConfigurationResult.skip("compiler kind not found");
    }
    String cFamilyCompiler = mapToCFamilyCompiler(compilerKind);
    if (cFamilyCompiler == null) {
      return ConfigurationResult.skip("unsupported compiler " + compilerKind.getDisplayName());
    }
    Map<String, String> properties = new HashMap<>();
    if (ocFile.isHeader()) {
      properties.put("isHeaderFile", "true");
    }

    if (usingRemoteOrWslToolchain(configuration)) {
      collectPropertiesForRemoteToolchain(compilerSettings, properties);
    } else if (compilerKind instanceof MSVCCompilerKind) {
      collectMSVCProperties(compilerSettings, properties);
    }

    return ConfigurationResult.of(new Configuration(
      file,
      compilerSettings.getCompilerExecutable().getAbsolutePath(),
      compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW),
      cFamilyCompiler,
      getSonarLanguage(languageKind),
      properties));
  }

  private static void collectPropertiesForRemoteToolchain(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));

    String includeDirs = compilerSettings.getHeadersSearchPaths().stream()
      .filter(h -> !h.isFrameworksSearchPath())
      .map(HeadersSearchPath::getPath)
      .collect(Collectors.joining("\n"));
    properties.put("includeDirs", includeDirs);

    String frameworkDirs = compilerSettings.getHeadersSearchPaths().stream()
      .filter(HeadersSearchPath::isFrameworksSearchPath)
      .map(HeadersSearchPath::getPath)
      .collect(Collectors.joining("\n"));
    properties.put("frameworkDirs", frameworkDirs);
  }

  private static void collectMSVCProperties(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put(
      "preprocessorDefines",
      getPreprocessorDefines(compilerSettings));
    properties.put(
      "builtinHeaders",
      compilerSettings.getHeadersSearchPaths().stream()
        .filter(HeadersSearchPath::isBuiltInHeaders)
        .map(HeadersSearchPath::getPath)
        .collect(Collectors.joining("\n")));
  }


  @NotNull
  private static String getPreprocessorDefines(OCCompilerSettings compilerSettings) {
    Object result;
    try {
      result = getPreprocessorDefinesMethod().invoke(compilerSettings);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    if (result instanceof List) {
      return String.join("\n", (List<String>) result);
    } else if (result instanceof String) {
      return (String) result;
    } else {
      throw new IllegalStateException(result.toString());
    }
  }

  @Nullable
  static String mapToCFamilyCompiler(OCCompilerKind compilerKind) {
    if ((compilerKind instanceof GCCCompilerKind) || (compilerKind instanceof ClangCompilerKind)) {
      return "clang";
    } else if (compilerKind instanceof MSVCCompilerKind) {
      return "msvc-cl";
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

  private boolean usingRemoteOrWslToolchain(OCResolveConfiguration configuration) {
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
    return environment != null && (environment.getToolSet().isRemote() || environment.getToolSet().isWSL());
  }

  private static Method getPreprocessorDefinesMethod() {
    if (preprocessorDefinesMethod == null) {
      try {
        preprocessorDefinesMethod = OCCompilerSettings.class.getMethod("getPreprocessorDefines");
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(e);
      }
    }
    return preprocessorDefinesMethod;
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
    final String compilerExecutable;
    final String compilerWorkingDir;
    final List<String> compilerSwitches;
    final String compilerKind;
    @Nullable
    final Language sonarLanguage;
    final Map<String, String> properties;

    public Configuration(
      VirtualFile virtualFile,
      String compilerExecutable,
      String compilerWorkingDir,
      List<String> compilerSwitches,
      String compilerKind,
      @Nullable Language sonarLanguage,
      Map<String, String> properties) {
      this.virtualFile = virtualFile;
      this.compilerExecutable = compilerExecutable;
      this.compilerWorkingDir = compilerWorkingDir;
      this.compilerSwitches = compilerSwitches;
      this.compilerKind = compilerKind;
      this.sonarLanguage = sonarLanguage;
      this.properties = properties;
    }
  }
}
