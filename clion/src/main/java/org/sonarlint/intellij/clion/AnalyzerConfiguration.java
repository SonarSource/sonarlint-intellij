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
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurations;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;

public class AnalyzerConfiguration {
  private static final Map<ForcedLanguage, String> LANGUAGE_KEYS = Map.of(ForcedLanguage.C, "c", ForcedLanguage.CPP, "cpp", ForcedLanguage.OBJC, "objc");
  private final Project project;
  private static Method preprocessorDefinesMethod;

  public AnalyzerConfiguration(@NotNull Project project) {
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
    if (!(psiFile instanceof OCPsiFile ocFile)) {
      return new ConfigurationResult(psiFile + " not an OCPsiFile");
    }
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
      configuration = getConfiguration(project, file);
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

    var sonarLanguage = getSonarLanguage(languageKind);
    if (sonarLanguage != null) {
      properties.put("sonarLanguage", LANGUAGE_KEYS.get(sonarLanguage));
    }
    return ConfigurationResult.of(new Configuration(file, compilerSettings.getCompilerExecutable().getAbsolutePath(), compilerSettings.getCompilerWorkingDir().getAbsolutePath(),
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW), cFamilyCompiler, sonarLanguage, properties));
  }

  private static void collectPropertiesForRemoteToolchain(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));

    var includeDirs = compilerSettings.getHeadersSearchPaths().stream().filter(h -> !h.isFrameworksSearchPath()).map(HeadersSearchPath::getPath).collect(Collectors.joining("\n"));
    properties.put("includeDirs", includeDirs);

    var frameworkDirs = compilerSettings.getHeadersSearchPaths().stream().filter(HeadersSearchPath::isFrameworksSearchPath).map(HeadersSearchPath::getPath)
      .collect(Collectors.joining("\n"));
    properties.put("frameworkDirs", frameworkDirs);
  }

  private static void collectMSVCProperties(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));
    properties.put("builtinHeaders",
      compilerSettings.getHeadersSearchPaths().stream().filter(HeadersSearchPath::isBuiltInHeaders).map(HeadersSearchPath::getPath).collect(Collectors.joining("\n")));
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
    return switch (compilerKind.getDisplayName()) {
      case "AppleClang", "Clang", "GCC" -> "clang";
      case "clang-cl" -> "clang-cl";
      case "MSVC" -> "msvc-cl";
      default -> null;
    };
  }

  @Nullable
  static ForcedLanguage getSonarLanguage(OCLanguageKind languageKind) {
    if (languageKind.equals(CLanguageKind.C)) {
      return ForcedLanguage.C;
    } else if (languageKind.equals(CLanguageKind.CPP)) {
      return ForcedLanguage.CPP;
    } else if (languageKind.equals(CLanguageKind.OBJ_C)) {
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
        cppEnvironment = tryReflection(initializedWorkspace);
        if (cppEnvironment != null) {
          break;
        }
      }
    }
    return cppEnvironment != null && (cppEnvironment.getToolSet().isRemote() || cppEnvironment.getToolSet().isWSL() || cppEnvironment.getToolSet().isDocker());
  }

  @Nullable
  private CPPEnvironment tryReflection(CidrWorkspace initializedWorkspace) {
    // Use reflection to check if workspace is instanceof com.jetbrains.cidr.project.workspace.WorkspaceWithEnvironment interface has
    // getEnvironment() method
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
    if (result instanceof CPPEnvironment cppEnvironment) {
      return cppEnvironment;
    }
    return null;
  }

  private static Object unWrapList(Object result) {
    if (result instanceof List<?> list) {
      // getEnvironment returns a singleton list
      result = list.get(0);
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

  @CheckForNull
  private static OCResolveConfiguration getConfiguration(Project project, VirtualFile file) {
    return OCResolveConfigurations.getPreselectedConfiguration(file, project);
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

  public record Configuration(VirtualFile virtualFile, String compilerExecutable, String compilerWorkingDir,
    List<String> compilerSwitches, String compilerKind,
    @Nullable ForcedLanguage sonarLanguage, Map<String, String> properties) {
  }

}
