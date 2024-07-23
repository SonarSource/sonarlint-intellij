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
package org.sonarlint.intellij.java;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isEmpty;

public class JavaAnalysisConfigurator implements AnalysisConfigurator {

  private static final String JAVA_LIBRARIES_PROPERTY = "sonar.java.libraries";
  private static final String JAVA_BINARIES_PROPERTY = "sonar.java.binaries";
  private static final String JAVA_SOURCE_PROPERTY = "sonar.java.source";
  private static final String JAVA_TARGET_PROPERTY = "sonar.java.target";
  private static final String JAVA_TEST_LIBRARIES_PROPERTY = "sonar.java.test.libraries";
  private static final String JAVA_TEST_BINARIES_PROPERTY = "sonar.java.test.binaries";
  private static final String JAVA_JDK_HOME_PROPERTY = "sonar.java.jdkHome";
  private static final String JAVA_ENABLE_PREVIEW = "sonar.java.enablePreview";

  private static final char SEPARATOR = ',';

  @Override
  public AnalysisConfiguration configure(@NotNull Module ijModule, Collection<VirtualFile> filesToAnalyze) {
    var config = new AnalysisConfiguration();
    var moduleClasspath = new JavaModuleClasspath();
    moduleClasspath.dependentModules().add(ijModule);
    collectModuleClasspath(moduleClasspath, ijModule, true, false);
    var properties = config.extraProperties;
    setMultiValuePropertyIfNonEmpty(properties, JAVA_LIBRARIES_PROPERTY, moduleClasspath.libraries());
    setMultiValuePropertyIfNonEmpty(properties, JAVA_TEST_LIBRARIES_PROPERTY, moduleClasspath.testLibraries());
    setMultiValuePropertyIfNonEmpty(properties, JAVA_BINARIES_PROPERTY, moduleClasspath.binaries());
    setMultiValuePropertyIfNonEmpty(properties, JAVA_TEST_BINARIES_PROPERTY, moduleClasspath.testBinaries());
    configureJavaSourceTarget(ijModule, properties);
    if (moduleClasspath.getJdkHome() != null) {
      properties.put(JAVA_JDK_HOME_PROPERTY, moduleClasspath.getJdkHome());
    }
    return config;
  }

  private static void setMultiValuePropertyIfNonEmpty(Map<String, String> properties, String propKey, Set<String> values) {
    if (!values.isEmpty()) {
      var joinedLibs = StringUtils.join(values.stream().map(JavaAnalysisConfigurator::csvEscape).toList(), SEPARATOR);
      properties.put(propKey, joinedLibs);
    }
  }

  private static String csvEscape(String string) {
    // escape only when needed
    return string.contains(",") ? ("\"" + string + "\"") : string;
  }

  private static void configureJavaSourceTarget(final Module ijModule, Map<String, String> properties) {
    var languageLevel = computeReadActionSafely(ijModule.getProject(), () -> LanguageLevelUtil.getEffectiveLanguageLevel(ijModule));
    if (languageLevel == null) {
      return;
    }
    final var languageLevelStr = getLanguageLevelOption(languageLevel);
    var bytecodeTarget = CompilerConfiguration.getInstance(ijModule.getProject()).getBytecodeTargetLevel(ijModule);
    if (isEmpty(bytecodeTarget)) {
      // according to IDEA rule: if not specified explicitly, set target to be the same as source language level
      bytecodeTarget = languageLevelStr;
    }
    properties.put(JAVA_SOURCE_PROPERTY, languageLevelStr);
    properties.put(JAVA_TARGET_PROPERTY, bytecodeTarget);
    properties.put(JAVA_ENABLE_PREVIEW, String.valueOf(languageLevel.isPreview()));
  }

  private static String getLanguageLevelOption(LanguageLevel level) {
    return JpsJavaSdkType.complianceOption(level.toJavaVersion());
  }

  private static void collectModuleClasspath(JavaModuleClasspath moduleClasspath, @Nullable final Module module, boolean topLevel, boolean testClasspathOnly) {
    if (module == null) {
      return;
    }
    processCompilerOutput(moduleClasspath, module, topLevel, testClasspathOnly);
    final var mrm = ModuleRootManager.getInstance(module);
    final var orderEntries = mrm.getOrderEntries();
    for (final var entry : orderEntries) {
      if ((!topLevel && !isExported(entry)) || !entry.isValid()) {
        continue;
      }
      if (entry instanceof ModuleOrderEntry moduleEntry) {
        processModuleOrderEntry(moduleClasspath, testClasspathOnly, moduleEntry);
      } else if (entry instanceof LibraryOrderEntry libraryEntry) {
        processLibraryOrderEntry(moduleClasspath, libraryEntry, testClasspathOnly);
      } else if (entry instanceof JdkOrderEntry jdkEntry && jdkEntry.getJdk() != null) {
        processJdkOrderEntry(module, moduleClasspath, jdkEntry.getJdk());
      }
    }
  }

  private static void processModuleOrderEntry(JavaModuleClasspath moduleClasspath, boolean testClasspathOnly, ModuleOrderEntry moduleOrderEntry) {
    var dependentModule = moduleOrderEntry.getModule();
    var alreadyVisitedModules = (testClasspathOnly || isOnlyForTestClasspath(moduleOrderEntry.getScope())) ? moduleClasspath.testDependentModules()
      : moduleClasspath.dependentModules();
    if (!alreadyVisitedModules.contains(dependentModule)) {
      // Protect against circular dependencies
      alreadyVisitedModules.add(dependentModule);
      collectModuleClasspath(moduleClasspath, dependentModule, false, testClasspathOnly || isOnlyForTestClasspath(moduleOrderEntry.getScope()));
    }
  }

  private static void processLibraryOrderEntry(JavaModuleClasspath moduleClasspath, LibraryOrderEntry libraryOrderEntry, boolean testClasspathOnly) {
    var lib = libraryOrderEntry.getLibrary();
    final var libOnlyForTestClasspath = testClasspathOnly || isOnlyForTestClasspath(libraryOrderEntry.getScope());
    getLibraryEntries(lib)
      .stream()
      .map(VfsUtilCore::virtualToIoFile)
      .map(File::getAbsolutePath)
      .forEach(libPath -> {
        if (libOnlyForTestClasspath) {
          moduleClasspath.testLibraries().add(libPath);
        } else {
          addProductionClasspathEntry(moduleClasspath, libPath);
        }
      });
  }

  private static boolean isOnlyForTestClasspath(DependencyScope scope) {
    return !scope.isForProductionRuntime() && !scope.isForProductionCompile();
  }

  private static void processCompilerOutput(JavaModuleClasspath moduleClasspath, @NotNull Module module, boolean topLevel, boolean testModule) {
    var output = getCompilerOutputPath(module);
    if (output != null) {
      final var outputPath = VfsUtilCore.virtualToIoFile(output).getAbsolutePath();
      if (topLevel) {
        moduleClasspath.binaries().add(outputPath);
        // Production .class should be on tests classpath
        moduleClasspath.testLibraries().add(outputPath);
      } else {
        // Output dir of dependents modules should be considered as libraries
        if (testModule) {
          moduleClasspath.testLibraries().add(outputPath);
        } else {
          addProductionClasspathEntry(moduleClasspath, outputPath);
        }
      }
    }
    var testOutput = getCompilerTestOutputPath(module);
    if (testOutput != null) {
      final var testOutputPath = VfsUtilCore.virtualToIoFile(testOutput).getAbsolutePath();
      if (topLevel) {
        moduleClasspath.testBinaries().add(testOutputPath);
      }
      // Test output dir of dependents modules are not visible
    }
  }

  private static void addProductionClasspathEntry(JavaModuleClasspath moduleClasspath, final String libPath) {
    moduleClasspath.libraries().add(libPath);
    // Production classpath entries should be also added to the tests classpath
    moduleClasspath.testLibraries().add(libPath);
  }

  private static boolean isExported(OrderEntry entry) {
    return (entry instanceof ExportableOrderEntry exportableEntry) && exportableEntry.isExported();
  }

  private static void processJdkOrderEntry(final Module module, JavaModuleClasspath moduleClasspath, Sdk jdk) {
    var jdkHomePath = jdk.getHomePath();
    if (moduleClasspath.getJdkHome() != null) {
      SonarLintConsole.get(module.getProject()).info("Multiple Jdk configured for module: " + module.getName());
    } else {
      moduleClasspath.setJdkHome(jdkHomePath);
    }
    if (jdkHomePath != null && JdkUtil.isModularRuntime(jdkHomePath)) {
      final var jrtFs = new File(jdkHomePath, "lib/jrt-fs.jar");
      if (jrtFs.isFile()) {
        final var jrtFsPath = jrtFs.getAbsolutePath();
        moduleClasspath.libraries().add(jrtFsPath);
        moduleClasspath.testLibraries().add(jrtFsPath);
      } else {
        SonarLintConsole.get(module.getProject()).info("Unable to locate jrt-fs.jar");
      }
    }
    Stream.of(jdk.getRootProvider().getFiles(OrderRootType.CLASSES))
      .filter(f -> !JrtFileSystem.isModuleRoot(f))
      .map(VfsUtilCore::virtualToIoFile)
      .map(File::getAbsolutePath)
      .forEach(jdkLib -> {
        moduleClasspath.libraries().add(jdkLib);
        moduleClasspath.testLibraries().add(jdkLib);
      });
  }

  private static Collection<VirtualFile> getLibraryEntries(@Nullable Library lib) {
    if (lib == null) {
      return Collections.emptyList();
    }
    return List.of(lib.getFiles(OrderRootType.CLASSES));
  }

  @CheckForNull
  private static VirtualFile getCompilerOutputPath(final Module module) {
    final var compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      var file = compilerModuleExtension.getCompilerOutputPath();
      // see SLI-107
      if (exists(file)) {
        return file;
      }
    }
    return null;
  }

  @CheckForNull
  private static VirtualFile getCompilerTestOutputPath(final Module module) {
    final var compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      var file = compilerModuleExtension.getCompilerOutputPathForTests();
      if (exists(file)) {
        return file;
      }
    }
    return null;
  }

  /**
   * Checks if the file exists in the physical FS. It doesn't rely on the automatic refresh of the virtual FS, because
   * sometimes it's status is out of date, and the file doesn't actually exist in the FS.
   * It will trigger a refresh of the virtual file, which means to refresh it's status and attributes, and calling all listeners.
   */
  private static boolean exists(@Nullable VirtualFile file) {
    if (file == null) {
      return false;
    }

    return file.exists();
  }

}
