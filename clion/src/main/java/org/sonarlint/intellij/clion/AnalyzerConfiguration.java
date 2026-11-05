/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurations;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public abstract class AnalyzerConfiguration {

  public static final Map<ForcedLanguage, String> LANGUAGE_KEYS = Map.of(ForcedLanguage.C, "c", ForcedLanguage.CPP, "cpp", ForcedLanguage.OBJC, "objc");

  public abstract ConfigurationResult getConfiguration(VirtualFile file);

  public static class ConfigurationResult {
    @Nullable
    private final Configuration configuration;
    @Nullable
    private final String skipReason;
    private final boolean temporarilyUnavailable;

    public ConfigurationResult(@Nullable Configuration configuration) {
      this.configuration = configuration;
      this.skipReason = null;
      this.temporarilyUnavailable = false;
    }

    private ConfigurationResult(@Nullable String skipReason, boolean temporarilyUnavailable) {
      this.skipReason = skipReason;
      this.configuration = null;
      this.temporarilyUnavailable = temporarilyUnavailable;
    }

    public boolean hasConfiguration() {
      return configuration != null;
    }

    /**
     * No configuration now, but the file may still be analyzable, e.g. the lookup was cancelled while the IDE updates its
     * project model after a CMake project is loaded. Such a file must not be excluded, or it is never sent for analysis.
     */
    public boolean isTemporarilyUnavailable() {
      return temporarilyUnavailable;
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
      return new ConfigurationResult(skipReason, false);
    }

    public static ConfigurationResult temporarilyUnavailable(String reason) {
      return new ConfigurationResult(reason, true);
    }
  }

  /**
   * Result when the configuration read action returned nothing: either the file or the project is gone, or the read
   * action was cancelled, which happens while the IDE writes to its project model.
   */
  public static ConfigurationResult noConfigurationComputed(Project project, VirtualFile file) {
    if (project.isDisposed() || !file.isValid()) {
      return ConfigurationResult.skip("The file is invalid or the project is being closed");
    }
    return ConfigurationResult.temporarilyUnavailable("The configuration could not be computed yet, e.g. while the IDE updates its project model");
  }

  public record Configuration(VirtualFile virtualFile, String compilerExecutable, String compilerWorkingDir,
    List<String> compilerSwitches, String compilerKind,
    @Nullable ForcedLanguage sonarLanguage, Map<String, String> properties) {
  }

  @Nullable
  public static OCResolveConfiguration getConfiguration(Project project, VirtualFile file) {
    return OCResolveConfigurations.getPreselectedConfiguration(file, project);
  }

  @Nullable
  public static String mapToCFamilyCompiler(OCCompilerKind compilerKind) {
    return switch (compilerKind.getDisplayName()) {
      case "AppleClang", "Clang", "GCC" -> "clang";
      case "clang-cl" -> "clang-cl";
      case "MSVC" -> "msvc-cl";
      case "IAR" -> "iar";
      default -> null;
    };
  }

  @Nullable
  public static ForcedLanguage getSonarLanguage(@Nullable OCLanguageKind languageKind) {
    return switch (languageKind) {
      case CLanguageKind.C -> ForcedLanguage.C;
      case CLanguageKind.CPP -> ForcedLanguage.CPP;
      case CLanguageKind.OBJ_C -> ForcedLanguage.OBJC;
      case null, default -> null;
    };
  }

  public static boolean isRemoteWslOrDockerCMakeToolchain(Project project, OCResolveConfiguration configuration) {
    for (var initializedWorkspace : CidrWorkspace.getInitializedWorkspaces(project)) {
      if (initializedWorkspace instanceof CMakeWorkspace cMakeWorkspace) {
        var cppEnvironment = getCMakeCppEnvironment(project, cMakeWorkspace, configuration);
        if (cppEnvironment != null) {
          return cppEnvironment.getToolSet().isSsh() || cppEnvironment.getToolSet().isWSL() || cppEnvironment.getToolSet().isDocker();
        }
      }
    }
    SonarLintConsole.get(project).debug("Not using remote or WSL toolchain");
    return false;
  }

  @Nullable
  private static CPPEnvironment getCMakeCppEnvironment(Project project, CMakeWorkspace cMakeWorkspace, OCResolveConfiguration configuration) {
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

  public static void collectCompilerKindProperties(Project project, OCResolveConfiguration resolveConfiguration, OCCompilerKind compilerKind,
    String cFamilyCompiler, OCCompilerSettings compilerSettings, @Nullable OCLanguageKind languageKind, Map<String, String> properties) {
    if (isRemoteWslOrDockerCMakeToolchain(project, resolveConfiguration)) {
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

    var sonarLanguage = getSonarLanguage(languageKind);
    if (sonarLanguage != null) {
      properties.put("sonarLanguage", LANGUAGE_KEYS.get(sonarLanguage));
    }
  }

  public static void collectPropertiesForRemoteToolchain(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));

    var includeDirs = compilerSettings.getHeadersSearchPaths().stream().filter(h -> !h.isFrameworksSearchPath()).map(HeadersSearchPath::getPath).collect(Collectors.joining("\n"));
    properties.put("includeDirs", includeDirs);

    var frameworkDirs = compilerSettings.getHeadersSearchPaths().stream().filter(HeadersSearchPath::isFrameworksSearchPath).map(HeadersSearchPath::getPath)
      .collect(Collectors.joining("\n"));
    properties.put("frameworkDirs", frameworkDirs);
  }

  public static void collectDefinesAndIncludes(OCCompilerSettings compilerSettings, Map<String, String> properties, Predicate<HeadersSearchPath> headerPathFilter) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));
    properties.put("builtinHeaders",
      compilerSettings.getHeadersSearchPaths().stream().filter(headerPathFilter).map(HeadersSearchPath::getPath).collect(Collectors.joining("\n")));
  }

  @NotNull
  public static String getPreprocessorDefines(OCCompilerSettings compilerSettings) {
    return compilerSettings.getPreprocessorDefines().stream().map(String::trim).collect(Collectors.joining("\n")) + "\n";
  }

}
