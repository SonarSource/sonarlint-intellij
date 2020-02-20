/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import static org.sonarlint.intellij.util.SonarLintUtils.isEmpty;

public class JavaAnalysisConfigurator implements AnalysisConfigurator {
  private static final String JAVA_LIBRARIES_PROPERTY = "sonar.java.libraries";
  private static final String JAVA_BINARIES_PROPERTY = "sonar.java.binaries";
  private static final String JAVA_SOURCE_PROPERTY = "sonar.java.source";
  private static final String JAVA_TARGET_PROPERTY = "sonar.java.target";
  private static final String JAVA_TEST_LIBRARIES_PROPERTY = "sonar.java.test.libraries";
  private static final String JAVA_TEST_BINARIES_PROPERTY = "sonar.java.test.binaries";

  private static final char SEPARATOR = ',';

  private static final String JAR_REGEXP = "(.*)!/";
  private static final Pattern JAR_PATTERN = Pattern.compile(JAR_REGEXP);

  @Override
  public Map<String, String> configure(@NotNull Module ijModule) {
    Map<String, String> properties = new HashMap<>();
    configureLibraries(ijModule, properties);
    configureBinaries(ijModule, properties);
    configureJavaSourceTarget(ijModule, properties);
    return properties;
  }

  private static void configureJavaSourceTarget(final Module ijModule, Map<String, String> properties) {
    try {
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
    } catch (BootstrapMethodError | NoClassDefFoundError e) {
      // (DM): some components are not available in some flavours, for example ConpilerConfiguration and Language Level in PHP storm or CLion.
      // Even though this class should now only be loaded when the Java extensions are available, I leave this to be safe
    }
  }

  private static String getLanguageLevelOption(LanguageLevel level) {
    return JpsJavaSdkType.complianceOption(level.toJavaVersion());
  }

  private static void configureBinaries(Module ijModule, Map<String, String> properties) {
    String testPath = null;
    VirtualFile testCompilerOutput = getCompilerTestOutputPath(ijModule);
    if (testCompilerOutput != null) {
      testPath = testCompilerOutput.getCanonicalPath();
    }

    VirtualFile compilerOutput = getCompilerOutputPath(ijModule);
    if (compilerOutput != null) {
      String path = compilerOutput.getCanonicalPath();
      if (path != null) {
        properties.put(JAVA_BINARIES_PROPERTY, path);
        testPath = (testPath != null) ? (testPath + SEPARATOR + path) : path;
      }
    }

    if (testPath != null) {
      properties.put(JAVA_TEST_BINARIES_PROPERTY, testPath);
    }
  }

  private static void configureLibraries(Module ijModule, Map<String, String> properties) {
    List<String> libs = new ArrayList<>();
    for (VirtualFile f : getProjectClasspath(ijModule)) {
      libs.add(toFile(f.getPath()));
    }
    if (!libs.isEmpty()) {
      String joinedLibs = StringUtils.join(libs, SEPARATOR);
      properties.put(JAVA_LIBRARIES_PROPERTY, joinedLibs);
      // Can't differentiate main and test classpath
      properties.put(JAVA_TEST_LIBRARIES_PROPERTY, joinedLibs);
    }
  }

  private static List<VirtualFile> getProjectClasspath(@Nullable final Module module) {
    if (module == null) {
      return Collections.emptyList();
    }
    final List<VirtualFile> found = new ArrayList<>();
    final ModuleRootManager mrm = ModuleRootManager.getInstance(module);
    final OrderEntry[] orderEntries = mrm.getOrderEntries();
    for (final OrderEntry entry : orderEntries) {
      if (entry instanceof ModuleOrderEntry) {
        // Add dependent module output dir as library
        Module dependentModule = ((ModuleOrderEntry) entry).getModule();
        found.addAll(getModuleEntries(dependentModule));
      } else if (entry instanceof LibraryOrderEntry) {
        Library lib = ((LibraryOrderEntry) entry).getLibrary();
        found.addAll(getLibraryEntries(lib));
      }
    }
    return found;
  }

  private static Collection<VirtualFile> getLibraryEntries(@Nullable Library lib) {
    if (lib == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(lib.getFiles(OrderRootType.CLASSES));
  }

  private static Collection<VirtualFile> getModuleEntries(@Nullable Module dependentModule) {
    if (dependentModule == null) {
      return Collections.emptyList();
    }
    VirtualFile output = getCompilerOutputPath(dependentModule);
    if (output == null) {
      return Collections.emptyList();
    }
    return Collections.singleton(output);
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

  private static String toFile(String path) {
    Matcher m = JAR_PATTERN.matcher(path);
    if (m.matches()) {
      return m.group(1);
    }
    return path;
  }
}
