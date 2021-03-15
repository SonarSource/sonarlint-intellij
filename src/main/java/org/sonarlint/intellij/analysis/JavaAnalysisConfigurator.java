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
package org.sonarlint.intellij.analysis;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

import static org.sonarlint.intellij.util.SonarLintUtils.isEmpty;

public class JavaAnalysisConfigurator implements AnalysisConfigurator {
  private static final Logger LOGGER = Logger.getInstance(JavaAnalysisConfigurator.class);

  private static final String JAVA_LIBRARIES_PROPERTY = "sonar.java.libraries";
  private static final String JAVA_BINARIES_PROPERTY = "sonar.java.binaries";
  private static final String JAVA_SOURCE_PROPERTY = "sonar.java.source";
  private static final String JAVA_TARGET_PROPERTY = "sonar.java.target";
  private static final String JAVA_TEST_LIBRARIES_PROPERTY = "sonar.java.test.libraries";
  private static final String JAVA_TEST_BINARIES_PROPERTY = "sonar.java.test.binaries";
  private static final String JAVA_JDK_HOME_PROPERTY = "sonar.java.jdkHome";

  private static final char SEPARATOR = ',';

  @Override
  public Map<String, String> configure(@NotNull Module ijModule, Collection<VirtualFile> filesToAnalyze) {
    JavaModuleClasspath moduleClasspath = new JavaModuleClasspath();
    moduleClasspath.dependentModules().add(ijModule);
    collectModuleClasspath(moduleClasspath, ijModule, true, false);
    Map<String, String> properties = new HashMap<>();
    setMultiValuePropertyIfNonEmpty(properties, JAVA_LIBRARIES_PROPERTY, moduleClasspath.libraries());
    setMultiValuePropertyIfNonEmpty(properties, JAVA_TEST_LIBRARIES_PROPERTY, moduleClasspath.testLibraries());
    setMultiValuePropertyIfNonEmpty(properties, JAVA_BINARIES_PROPERTY, moduleClasspath.binaries());
    setMultiValuePropertyIfNonEmpty(properties, JAVA_TEST_BINARIES_PROPERTY, moduleClasspath.testBinaries());
    configureJavaSourceTarget(ijModule, properties);
    if (moduleClasspath.getJdkHome() != null) {
      properties.put(JAVA_JDK_HOME_PROPERTY, moduleClasspath.getJdkHome());
    }
    return properties;
  }

  private static void setMultiValuePropertyIfNonEmpty(Map<String, String> properties, String propKey, Set<String> values) {
    if (!values.isEmpty()) {
      String joinedLibs = StringUtils.join(values, SEPARATOR);
      properties.put(propKey, joinedLibs);
    }
  }

  private static void configureJavaSourceTarget(final Module ijModule, Map<String, String> properties) {
    LanguageLevel languageLevel = ApplicationManager.getApplication()
      .<LanguageLevel>runReadAction(() -> EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(ijModule));
    final String languageLevelStr = getLanguageLevelOption(languageLevel);
    String bytecodeTarget = CompilerConfiguration.getInstance(ijModule.getProject()).getBytecodeTargetLevel(ijModule);
    if (isEmpty(bytecodeTarget)) {
      // according to IDEA rule: if not specified explicitly, set target to be the same as source language level
      bytecodeTarget = languageLevelStr;
    }
    properties.put(JAVA_SOURCE_PROPERTY, languageLevelStr);
    properties.put(JAVA_TARGET_PROPERTY, bytecodeTarget);
  }

  private static String getLanguageLevelOption(LanguageLevel level) {
    return JpsJavaSdkType.complianceOption(level.toJavaVersion());
  }

  private static void collectModuleClasspath(JavaModuleClasspath moduleClasspath, @Nullable final Module module, boolean topLevel, boolean testClasspathOnly) {
    if (module == null) {
      return;
    }
    processCompilerOutput(moduleClasspath, module, topLevel, testClasspathOnly);
    final ModuleRootManager mrm = ModuleRootManager.getInstance(module);
    final OrderEntry[] orderEntries = mrm.getOrderEntries();
    for (final OrderEntry entry : orderEntries) {
      if ((!topLevel && !isExported(entry)) || !entry.isValid()) {
        continue;
      }
      if (entry instanceof ModuleOrderEntry) {
        processModuleOrderEntry(moduleClasspath, testClasspathOnly, (ModuleOrderEntry) entry);
      } else if (entry instanceof LibraryOrderEntry) {
        processLibraryOrderEntry(moduleClasspath, (LibraryOrderEntry) entry, testClasspathOnly);
      } else if (entry instanceof JdkOrderEntry) {
        processJdkOrderEntry(module, moduleClasspath, ((JdkOrderEntry) entry).getJdk());
      }
    }
  }

  private static void processModuleOrderEntry(JavaModuleClasspath moduleClasspath, boolean testClasspathOnly, ModuleOrderEntry moduleOrderEntry) {
    Module dependentModule = moduleOrderEntry.getModule();
    Set<Module> alreadyVisitedModules = (testClasspathOnly || isOnlyForTestClasspath(moduleOrderEntry.getScope())) ? moduleClasspath.testDependentModules()
      : moduleClasspath.dependentModules();
    if (!alreadyVisitedModules.contains(dependentModule)) {
      // Protect against circular dependencies
      alreadyVisitedModules.add(dependentModule);
      collectModuleClasspath(moduleClasspath, dependentModule, false, testClasspathOnly || isOnlyForTestClasspath(moduleOrderEntry.getScope()));
    }
  }

  private static void processLibraryOrderEntry(JavaModuleClasspath moduleClasspath, LibraryOrderEntry libraryOrderEntry, boolean testClasspathOnly) {
    Library lib = libraryOrderEntry.getLibrary();
    final boolean libOnlyForTestClasspath = testClasspathOnly || isOnlyForTestClasspath(libraryOrderEntry.getScope());
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
    VirtualFile output = getCompilerOutputPath(module);
    if (output != null) {
      final String outputPath = VfsUtilCore.virtualToIoFile(output).getAbsolutePath();
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
    VirtualFile testOutput = getCompilerTestOutputPath(module);
    if (testOutput != null) {
      final String testOutputPath = VfsUtilCore.virtualToIoFile(testOutput).getAbsolutePath();
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
    return (entry instanceof ExportableOrderEntry) && ((ExportableOrderEntry) entry).isExported();
  }

  private static void processJdkOrderEntry(final Module module, JavaModuleClasspath moduleClasspath, Sdk jdk) {
    String jdkHomePath = jdk.getHomePath();
    if (moduleClasspath.getJdkHome() != null) {
      LOGGER.warn("Multiple Jdk configured for module: " + module.getName());
    } else {
      moduleClasspath.setJdkHome(jdkHomePath);
    }
    if (jdkHomePath != null && JdkUtil.isModularRuntime(jdkHomePath)) {
      final File jrtFs = new File(jdkHomePath, "lib/jrt-fs.jar");
      if (jrtFs.isFile()) {
        final String jrtFsPath = jrtFs.getAbsolutePath();
        moduleClasspath.libraries().add(jrtFsPath);
        moduleClasspath.testLibraries().add(jrtFsPath);
      } else {
        LOGGER.warn("Unable to locate jrt-fs.jar");
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
    return Arrays.asList(lib.getFiles(OrderRootType.CLASSES));
  }

  @CheckForNull
  private static VirtualFile getCompilerOutputPath(final Module module) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      VirtualFile file = compilerModuleExtension.getCompilerOutputPath();
      // see SLI-107
      if (exists(file)) {
        return file;
      }
    }
    return null;
  }

  @CheckForNull
  private static VirtualFile getCompilerTestOutputPath(final Module module) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      VirtualFile file = compilerModuleExtension.getCompilerOutputPathForTests();
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
