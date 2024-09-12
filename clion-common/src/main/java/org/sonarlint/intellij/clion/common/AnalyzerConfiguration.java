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
package org.sonarlint.intellij.clion.common;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurations;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.project.workspace.CidrWorkspace;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public abstract class AnalyzerConfiguration {

  private static Method preprocessorDefinesMethod;

  public static final Map<ForcedLanguage, String> LANGUAGE_KEYS = Map.of(ForcedLanguage.C, "c", ForcedLanguage.CPP, "cpp", ForcedLanguage.OBJC, "objc");

  public static class ConfigurationResult {
    @Nullable
    private final Configuration configuration;
    @Nullable
    private final String skipReason;

    public ConfigurationResult(@Nullable Configuration configuration) {
      this.configuration = configuration;
      this.skipReason = null;
    }

    public ConfigurationResult(@Nullable String skipReason) {
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

  @Nullable
  public static OCResolveConfiguration getConfiguration(Project project, VirtualFile file) {
    return OCResolveConfigurations.getPreselectedConfiguration(file, project);
  }

  public static Method getPreprocessorDefinesMethod() {
    if (preprocessorDefinesMethod == null) {
      try {
        preprocessorDefinesMethod = OCCompilerSettings.class.getMethod("getPreprocessorDefines");
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(e);
      }
    }
    return preprocessorDefinesMethod;
  }

  private static Object unWrapList(Object result) {
    if (result instanceof List<?> list) {
      // getEnvironment returns a singleton list
      result = list.get(0);
    }
    return result;
  }

  @Nullable
  public CPPEnvironment tryReflection(CidrWorkspace initializedWorkspace, Project project) {
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

  @Nullable
  public static String mapToCFamilyCompiler(OCCompilerKind compilerKind) {
    return switch (compilerKind.getDisplayName()) {
      case "AppleClang", "Clang", "GCC" -> "clang";
      case "clang-cl" -> "clang-cl";
      case "MSVC" -> "msvc-cl";
      default -> null;
    };
  }

  public static void collectPropertiesForRemoteToolchain(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));

    var includeDirs = compilerSettings.getHeadersSearchPaths().stream().filter(h -> !h.isFrameworksSearchPath()).map(HeadersSearchPath::getPath).collect(Collectors.joining("\n"));
    properties.put("includeDirs", includeDirs);

    var frameworkDirs = compilerSettings.getHeadersSearchPaths().stream().filter(HeadersSearchPath::isFrameworksSearchPath).map(HeadersSearchPath::getPath)
      .collect(Collectors.joining("\n"));
    properties.put("frameworkDirs", frameworkDirs);
  }

  public static void collectMSVCProperties(OCCompilerSettings compilerSettings, Map<String, String> properties) {
    properties.put("preprocessorDefines", getPreprocessorDefines(compilerSettings));
    properties.put("builtinHeaders",
      compilerSettings.getHeadersSearchPaths().stream().filter(HeadersSearchPath::isBuiltInHeaders).map(HeadersSearchPath::getPath).collect(Collectors.joining("\n")));
  }

  @NotNull
  public static String getPreprocessorDefines(OCCompilerSettings compilerSettings) {
    Object result;
    try {
      result = getPreprocessorDefinesMethod().invoke(compilerSettings);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    if (result instanceof List) {
      return ((List<String>) result).stream()
        .map(String::trim)
        .collect(Collectors.joining("\n")) + "\n";
    } else if (result instanceof String resultString) {
      return Arrays.stream(resultString.split("\n"))
        .map(String::trim)
        .collect(Collectors.joining("\n")) + "\n";
    } else {
      throw new IllegalStateException(result.toString());
    }
  }

}
