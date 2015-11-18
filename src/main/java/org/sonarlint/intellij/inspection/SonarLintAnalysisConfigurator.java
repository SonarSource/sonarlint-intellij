/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.inspection;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.IssueListener;
import org.sonarlint.intellij.config.SonarLintProjectSettings;
import org.sonarlint.intellij.console.SonarLintConsole;

public class SonarLintAnalysisConfigurator {

  public static final String PROJECT_VERSION_PROPERTY = "sonar.projectVersion";
  public static final String PROJECT_NAME_PROPERTY = "sonar.projectName";
  public static final String PROJECT_SOURCES_PROPERTY = "sonar.sources";
  public static final String PROJECT_TESTS_PROPERTY = "sonar.tests";
  public static final String JAVA_LIBRARIES_PROPERTY = "sonar.java.libraries";
  public static final String JAVA_BINARIES_PROPERTY = "sonar.java.binaries";
  public static final String JAVA_SOURCE_PROPERTY = "sonar.java.source";
  public static final String JAVA_TARGET_PROPERTY = "sonar.java.target";
  public static final String PROJECT_TEST_LIBRARIES_PROPERTY = "sonar.java.test.libraries";
  public static final String PROJECT_TEST_BINARIES_PROPERTY = "sonar.java.test.binaries";
  public static final String ENCODING_PROPERTY = "sonar.sourceEncoding";
  public static final String PROJECT_BASEDIR = "sonar.projectBaseDir";

  private static final char SEPARATOR = ',';

  private static final String JAR_REGEXP = "(.*)!/";
  private static final Pattern JAR_PATTERN = Pattern.compile(JAR_REGEXP);
  private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";

  public static void analyzeModule(Module module, Collection<String> filesToAnalyze, IssueListener listener) {
    Project p = module.getProject();
    SonarLintConsole console = SonarLintConsole.getSonarQubeConsole(p);
    SonarLintProjectSettings settings = p.getComponent(SonarLintProjectSettings.class);
    SonarQubeRunnerFacade runner = p.getComponent(SonarQubeRunnerFacade.class);

    // Configure
    Properties properties = new Properties();
    configureProjectSettings(p, properties);
    configureModuleSettings(module, properties, filesToAnalyze);

    // Analyse
    long start = System.currentTimeMillis();
    console.info("Start SonarLint analysis on " + p.getName() + "...");
    run(settings, runner, properties, console, listener);
    if (settings.isVerboseEnabled()) {
      console.info("Done in " + (System.currentTimeMillis() - start) + "ms\n");
    }
  }

  private static void configureProjectSettings(Project p, Properties properties) {
    configureEncoding(p, properties);
    properties.setProperty(PROJECT_VERSION_PROPERTY, DEFAULT_VERSION);
  }

  private static void configureEncoding(Project p, Properties properties) {
    Charset encoding = EncodingProjectManager.getInstance(p).getEncoding(null, true);
    if (encoding != null) {
      properties.setProperty(ENCODING_PROPERTY, encoding.toString());
    }
  }

  private static void configureModuleSettings(@NotNull Module ijModule,
                                              @NotNull Properties properties, Collection<String> filesToAnalyze) {
    String baseDir = InspectionUtils.getModuleRootPath(ijModule);
    if (baseDir == null) {
      throw new IllegalStateException("No basedir for module " + ijModule);
    }
    properties.setProperty(PROJECT_BASEDIR, baseDir);
    properties.setProperty(PROJECT_NAME_PROPERTY, ijModule.getName());

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(ijModule);
    configureSourceDirs(properties, moduleRootManager, filesToAnalyze);

    configureLibraries(ijModule, properties);

    configureBinaries(ijModule, properties);

    configureJavaSourceTarget(ijModule, properties);
  }

  private static void configureJavaSourceTarget(final Module ijModule, Properties properties) {
    try {
      final String languageLevel = ApplicationManager.getApplication().runReadAction(new Computable<LanguageLevel>() {
        @Override
        public LanguageLevel compute() {
          return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(ijModule);
        }
      }).getCompilerComplianceDefaultOption();
      String bytecodeTarget = CompilerConfiguration.getInstance(ijModule.getProject()).getBytecodeTargetLevel(ijModule);
      if (StringUtil.isEmpty(bytecodeTarget)) {
        // according to IDEA rule: if not specified explicitly, set target to be the same as source language level
        bytecodeTarget = languageLevel;
      }
      properties.setProperty(JAVA_SOURCE_PROPERTY, languageLevel);
      properties.setProperty(JAVA_TARGET_PROPERTY, bytecodeTarget);
    } catch (NoClassDefFoundError e) {
      // CompilerConfiguration not available for example in PHP Storm
    }
  }

  private static void configureBinaries(Module ijModule, Properties properties) {
    VirtualFile compilerOutput = getCompilerOutputPath(ijModule);
    if (compilerOutput != null) {
      String path = compilerOutput.getCanonicalPath();
      if (path != null) {
        properties.setProperty(JAVA_BINARIES_PROPERTY, path);
      }
    }
    VirtualFile testCompilerOutput = getCompilerTestOutputPath(ijModule);
    if (testCompilerOutput != null) {
      String path = testCompilerOutput.getCanonicalPath();
      if (path != null) {
        properties.setProperty(PROJECT_TEST_BINARIES_PROPERTY, path);
      }
    }
  }

  private static void configureLibraries(Module ijModule, Properties properties) {
    List<String> libs = new ArrayList<>();
    for (VirtualFile f : getProjectClasspath(ijModule)) {
      libs.add(toFile(f.getPath()));
    }
    if (!libs.isEmpty()) {
      String joinedLibs = StringUtils.join(libs, SEPARATOR);
      properties.setProperty(JAVA_LIBRARIES_PROPERTY, joinedLibs);
      // Can't differentiate main and test classpath
      properties.setProperty(PROJECT_TEST_LIBRARIES_PROPERTY, joinedLibs);
    }
  }

  private static void configureSourceDirs(Properties properties, ModuleRootManager moduleRootManager, Collection<String> filesToAnalyze) {
    Collection<String> testFolderPrefix = findTestFolderPrefixes(moduleRootManager);
    Collection<String> sources = new ArrayList<>();
    Collection<String> tests = new ArrayList<>();

    findSourceDirs(filesToAnalyze, testFolderPrefix, sources, tests);

    if (!sources.isEmpty()) {
      properties.setProperty(PROJECT_SOURCES_PROPERTY, StringUtils.join(sources, SEPARATOR));
    } else {
      // sonar.sources is mandatory
      properties.setProperty(PROJECT_SOURCES_PROPERTY, "");
    }
    if (!tests.isEmpty()) {
      properties.setProperty(PROJECT_TESTS_PROPERTY, StringUtils.join(tests, SEPARATOR));
    }
  }

  private static void findSourceDirs(Collection<String> filesToAnalyze, Collection<String> testFolderPrefix, Collection<String> sources, Collection<String> tests) {
    for (String f : filesToAnalyze) {
      boolean isTest = false;
      for (String testPrefix : testFolderPrefix) {
        if (f.startsWith(testPrefix)) {
          isTest = true;
          break;
        }
      }
      // TODO make relative to basedir
      if (isTest) {
        tests.add(f);
      } else {
        sources.add(f);
      }
    }
  }

  @NotNull
  private static Collection<String> findTestFolderPrefixes(ModuleRootManager moduleRootManager) {
    Collection<String> testFolderPrefix = new ArrayList<>();
    for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null && sourceFolder.isTestSource()) {
          testFolderPrefix.add(file.getPath());
        }
      }
    }
    return testFolderPrefix;
  }

  @NotNull
  public static VirtualFile[] getProjectClasspath(@Nullable final Module module) {
    if (module == null) {
      return new VirtualFile[0];
    }
    final List<VirtualFile> found = new LinkedList<>();
    final ModuleRootManager mrm = ModuleRootManager.getInstance(module);
    final OrderEntry[] orderEntries = mrm.getOrderEntries();
    for (final OrderEntry entry : orderEntries) {
      if (entry instanceof ModuleOrderEntry) {
        // Add dependent module output dir as library
        Module dependentModule = ((ModuleOrderEntry) entry).getModule();
        if (dependentModule != null) {
          VirtualFile output = getCompilerOutputPath(dependentModule);
          if (output != null) {
            found.add(output);
          }
        }
      } else if (entry instanceof LibraryOrderEntry) {
        Library lib = ((LibraryOrderEntry) entry).getLibrary();
        if (lib != null) {
          Collections.addAll(found, lib.getFiles(OrderRootType.CLASSES));
        }
      }
    }
    return found.toArray(new VirtualFile[found.size()]);
  }

  @Nullable
  private static VirtualFile getCompilerOutputPath(final Module module) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      return compilerModuleExtension.getCompilerOutputPath();
    }
    return null;
  }

  @Nullable
  private static VirtualFile getCompilerTestOutputPath(final Module module) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      return compilerModuleExtension.getCompilerOutputPathForTests();
    }
    return null;
  }

  private static String toFile(String path) {
    Matcher m = JAR_PATTERN.matcher(path);
    if (m.matches()) {
      return m.group(1);
    }
    return path;
  }

  public static void run(SonarLintProjectSettings projectSettings, SonarQubeRunnerFacade runner, Properties props, SonarLintConsole console, IssueListener listener) {

    runner.startAnalysis(props, listener);

  }

}
