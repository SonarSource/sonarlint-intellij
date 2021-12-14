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
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.preprocessor.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.commons.Language;

public class AnalyzerConfiguration {
  private final Project project;

  private static final BiFunction<VirtualFile, Project, OCResolveConfiguration> FALLBACK_CONFIGURATION_RESOLVER = (f, p) -> null;
  private static BiFunction<VirtualFile, Project, OCResolveConfiguration> configurationResolver = null;
  private static Method preprocessorDefinesMethod;

  public AnalyzerConfiguration(@NotNull Project project) {
    this.project = project;
  }

  public ConfigurationResult getConfiguration(VirtualFile file) {
    return ApplicationManager.getApplication().<ConfigurationResult>runReadAction(() -> getConfigurationAction(file));
  }

  /**
   * Inspired from ShowCompilerInfoForFile and ClangTidyAnnotator
   */
  public ConfigurationResult getConfigurationAction(VirtualFile file) {
    var psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof OCPsiFile)) {
      return new ConfigurationResult(psiFile + " not an OCPsiFile");
    }
    var ocFile = ((OCPsiFile) psiFile).getOCFile();
    if (!ocFile.isInProjectSources()) {
      return new ConfigurationResult(ocFile + " not in project sources");
    }
    OCResolveConfiguration configuration = null;
    OCLanguageKind languageKind;
    var languageAndConfiguration = ocFile.getParsedLanguageAndConfiguration();
    if (languageAndConfiguration != null) {
      configuration = languageAndConfiguration.getConfiguration();
      languageKind = languageAndConfiguration.getLanguageKind();
    } else {
      languageKind = ocFile.getKind();
    }
    if (configuration == null) {
      configuration = getConfigurationResolver(project).apply(file, project);
    }
    if (configuration == null) {
      return ConfigurationResult.skip("configuration not found");
    }
    var compilerSettings = configuration.getCompilerSettings(ocFile.getKind(), file);
    var compilerKind = compilerSettings.getCompilerKind();
    if (compilerKind == null) {
      return ConfigurationResult.skip("compiler kind not found");
    }
    var cFamilyCompiler = mapToCFamilyCompiler(compilerKind);
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

    var includeDirs = compilerSettings.getHeadersSearchPaths().stream()
      .filter(h -> !h.isFrameworksSearchPath())
      .map(HeadersSearchPath::getPath)
      .collect(Collectors.joining("\n"));
    properties.put("includeDirs", includeDirs);

    var frameworkDirs = compilerSettings.getHeadersSearchPaths().stream()
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
      return String.join("\n", (List<String>) result) + "\n";
    } else if (result instanceof String) {
      return result + "\n";
    } else {
      throw new IllegalStateException(result.toString());
    }
  }

  @Nullable
  static String mapToCFamilyCompiler(OCCompilerKind compilerKind) {
    switch (compilerKind.getDisplayName()) {
      case "AppleClang":
      case "Clang":
      case "GCC":
        return "clang";
      case "MSVC":
        return "msvc-cl";
      default:
        return null;
    }
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
    final var initializedWorkspaces = CidrWorkspace.getInitializedWorkspaces(project);
    CPPEnvironment cppEnvironment = null;
    for (var initializedWorkspace : initializedWorkspaces) {
      if (initializedWorkspace instanceof CMakeWorkspace) {
        cppEnvironment = getCMakeCppEnvironment((CMakeWorkspace) initializedWorkspace, configuration);
      } else {
        cppEnvironment = tryReflection(initializedWorkspace);
        if (cppEnvironment != null) {
          break;
        }
      }
    }
    return cppEnvironment != null && (cppEnvironment.getToolSet().isRemote() || cppEnvironment.getToolSet().isWSL());
  }

  @Nullable
  private CPPEnvironment tryReflection(CidrWorkspace initializedWorkspace) {
    // Use reflection to check if workspace is instanceof com.jetbrains.cidr.project.workspace.WorkspaceWithEnvironment interface has getEnvironment() method
    final Method classMethod;
    try {
      classMethod = initializedWorkspace.getClass().getMethod("getEnvironment");
    } catch (NoSuchMethodException e) {
      SonarLintConsole.get(project).debug(initializedWorkspace.getClass().getName() + " has no getEnvironment() method");
      return null;
    }
    Object result;
    try {
      result = classMethod.invoke(initializedWorkspace);
    } catch (ReflectiveOperationException e) {
      SonarLintConsole.get(project).debug(e.getMessage());
      return null;
    }
    result = unWrapList(result);
    if (result instanceof CPPEnvironment) {
      return (CPPEnvironment) result;
    }
    return null;
  }

  private static Object unWrapList(Object result) {
    if (result instanceof List) {
      // getEnvironment returns a singleton list
      result = ((List<?>) result).get(0);
    }
    return result;
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

  private static BiFunction<VirtualFile, Project, OCResolveConfiguration> getConfigurationResolver(Project project) {
    if (configurationResolver == null) {
      configurationResolver = loadConfigurationResolver(project);
    }
    return configurationResolver;
  }

  private static BiFunction<VirtualFile, Project, OCResolveConfiguration> loadConfigurationResolver(Project project) {
    // Before 2021.3: com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil.getResolveRootAndActiveConfiguration
    try {
      final var method = OCInclusionContextUtil.class.getMethod("getResolveRootAndActiveConfiguration", VirtualFile.class, Project.class);
      return (f, p) -> {
        try {
          final var result = method.invoke(null, f, p);
          return result == null ? null : ((OCResolveRootAndConfiguration) result).getConfiguration();
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
      };
    } catch (NoSuchMethodException e) {
      SonarLintConsole.get(project).debug("com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil$getResolveRootAndActiveConfiguration not found");
    }

    // Starting from 2021.3 com.jetbrains.cidr.lang.workspace.OCResolveConfigurations.getPreselectedConfiguration
    Class<?> ocResolveConfigurationsClass;
    try {
      ocResolveConfigurationsClass = OCResolveConfiguration.class.forName("com.jetbrains.cidr.lang.workspace.OCResolveConfigurations");
    } catch (ClassNotFoundException e) {
      SonarLintConsole.get(project).debug("com.jetbrains.cidr.lang.workspace.OCResolveConfigurations not found");
      return FALLBACK_CONFIGURATION_RESOLVER;
    }
    try {
      final var getPreselectedConfiguration = ocResolveConfigurationsClass.getMethod("getPreselectedConfiguration", VirtualFile.class, Project.class);
      return (f, p) -> {
        try {
          final Object result = getPreselectedConfiguration.invoke(null, f, p);
          return result == null ? null : (OCResolveConfiguration) result;
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
      };
    } catch (NoSuchMethodException e) {
      SonarLintConsole.get(project).debug("com.jetbrains.cidr.lang.workspace.OCResolveConfigurations$getPreselectedConfiguration not found");
    }
    return FALLBACK_CONFIGURATION_RESOLVER;
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
