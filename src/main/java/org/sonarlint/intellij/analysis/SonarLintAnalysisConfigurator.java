/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.test.TestRunnerFacade;
import org.sonarlint.intellij.util.SonarLintConstants;
import org.sonarlint.intellij.util.SonarLintUtils;

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

  private SonarLintAnalysisConfigurator() {
    //static only
  }

  public static void analyzeModule(Module module, Collection<VirtualFile> filesToAnalyze, IssueListener listener) {
    Project p = module.getProject();
    SonarLintConsole console = SonarLintConsole.getSonarQubeConsole(p);
    SonarQubeRunnerFacade runner;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runner = TestRunnerFacade.getInstance();
    } else {
      runner = p.getComponent(SonarQubeRunnerFacade.class);
    }

    // Configure
    Properties properties = new Properties();
    configureProjectSettings(p, properties);
    configureModuleSettings(module, properties, filesToAnalyze);

    // Analyze
    long start = System.currentTimeMillis();
    console.info("Start SonarLint analysis on " + p.getName() + "...");
    run(runner, properties, listener);
    console.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
  }

  private static void configureProjectSettings(Project p, Properties properties) {
    configureEncoding(p, properties);
    properties.setProperty(PROJECT_VERSION_PROPERTY, DEFAULT_VERSION);
    properties.setProperty(SonarLintConstants.USE_WS_CACHE, Boolean.toString(true));
  }

  private static void configureEncoding(Project p, Properties properties) {
    Charset encoding = EncodingProjectManager.getInstance(p).getEncoding(null, true);
    if (encoding != null) {
      properties.setProperty(ENCODING_PROPERTY, encoding.toString());
    }
  }

  private static void configureModuleSettings(@NotNull Module ijModule,
    @NotNull Properties properties, Collection<VirtualFile> filesToAnalyze) {
    String baseDir = SonarLintUtils.getModuleRootPath(ijModule);

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
      final String languageLevel = getLanguageLevelOption(ApplicationManager.getApplication().runReadAction(new Computable<LanguageLevel>() {
        @Override
        public LanguageLevel compute() {
          return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(ijModule);
        }
      }));
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

  private static String getLanguageLevelOption(LanguageLevel level) {
    switch (level) {
      case JDK_1_3:
        return "1.3";
      case JDK_1_4:
        return "1.4";
      case JDK_1_5:
        return "1.5";
      case JDK_1_6:
        return "1.6";
      case JDK_1_7:
        return "1.7";
      case JDK_1_8:
        return "8";
      case JDK_1_9:
        return "9";
      default:
        return "";
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

  private static void configureSourceDirs(Properties properties, ModuleRootManager moduleRootManager, Collection<VirtualFile> filesToAnalyze) {
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

  private static void findSourceDirs(Collection<VirtualFile> filesToAnalyze, Collection<String> testFolderPrefix, Collection<String> sources, Collection<String> tests) {
    for (VirtualFile f : filesToAnalyze) {
      String filePath = f.getPath();
      boolean isTest = false;
      for (String testPrefix : testFolderPrefix) {
        if (filePath.startsWith(testPrefix)) {
          isTest = true;
          break;
        }
      }
      // TODO make relative to basedir
      if (isTest) {
        tests.add(filePath);
      } else {
        sources.add(filePath);
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

  public static void run(SonarQubeRunnerFacade runner, Properties props, IssueListener listener) {
    String name = Thread.currentThread().getName();

    // some thread names conflict with Persistit
    try {
      Thread.currentThread().setName("sonarlint-analysis");
      runner.startAnalysis(props, listener);
    } finally {
      Thread.currentThread().setName(name);
    }

  }

}
