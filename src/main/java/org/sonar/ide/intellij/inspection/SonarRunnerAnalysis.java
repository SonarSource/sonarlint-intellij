/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.inspection;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;
import org.sonar.runner.api.ScanProperties;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SonarRunnerAnalysis {

  public static final String PROJECT_VERSION_PROPERTY = "sonar.projectVersion";
  public static final String PROJECT_KEY_PROPERTY = "sonar.projectKey";
  public static final String PROJECT_NAME_PROPERTY = "sonar.projectName";
  public static final String PROJECT_SOURCES_PROPERTY = "sonar.sources";
  public static final String PROJECT_TESTS_PROPERTY = "sonar.tests";
  public static final String PROJECT_LIBRARIES_PROPERTY = "sonar.java.libraries";
  public static final String PROJECT_BINARIES_PROPERTY = "sonar.java.binaries";
    public static final String ENCODING_PROPERTY = "sonar.sourceEncoding";
  public static final String PROJECT_BASEDIR = "sonar.projectBaseDir";

  private static final char SEPARATOR = ',';

  private static final String JAR_REGEXP = "(.*)!/";
  private static final Pattern JAR_PATTERN = Pattern.compile(JAR_REGEXP);
  private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";


  public static void analyzeModule(Module module, Collection<String> filesToAnalyze, IssueListener listener) {
    Project p = module.getProject();
    SonarQubeConsole console = SonarQubeConsole.getSonarQubeConsole(p);
    ProjectSettings settings = p.getComponent(ProjectSettings.class);
    SonarQubeRunner runner = p.getComponent(SonarQubeRunner.class);

    // Configure
    Properties properties = new Properties();
    configureProjectSettings(p, properties);
    configureModuleSettings(p, settings, module, properties, filesToAnalyze);

    // Analyse
    long start = System.currentTimeMillis();
    console.info("Start SonarQube analysis on " + p.getName() + "...\n");
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

  private static void configureModuleSettings(@NotNull Project p, @NotNull ProjectSettings settings, @NotNull Module ijModule,
                                          @NotNull Properties properties, Collection<String> filesToAnalyze) {
    String baseDir = InspectionUtils.getModuleRootPath(ijModule);
    if (baseDir == null) {
      throw new IllegalStateException("No basedir for module " + ijModule);
    }
    properties.setProperty(PROJECT_BASEDIR, baseDir);
    properties.setProperty(PROJECT_NAME_PROPERTY, ijModule.getName());

    // TODO
    // properties.setProperty(PROJECT_KEY_PROPERTY, settings.getProjectKey());

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(ijModule);
    configureSourceDirs(properties, moduleRootManager, filesToAnalyze);

    configureLibraries(ijModule, properties);

    configureBinaries(ijModule, properties);
  }

  private static void configureBinaries(Module ijModule, Properties properties) {
    VirtualFile compilerOutput = getCompilerOutputPath(ijModule);
    if (compilerOutput != null) {
      String path = compilerOutput.getCanonicalPath();
      if (path != null) {
        properties.setProperty(PROJECT_BINARIES_PROPERTY, path);
      }
    }
  }

  private static void configureLibraries(Module ijModule, Properties properties) {
    List<String> libs = new ArrayList<String>();
    for (VirtualFile f : getProjectClasspath(ijModule)) {
      libs.add(toFile(f.getPath()));
    }
    if (!libs.isEmpty()) {
      properties.setProperty(PROJECT_LIBRARIES_PROPERTY, StringUtils.join(libs, SEPARATOR));
    }
  }

  private static void configureSourceDirs(Properties properties, ModuleRootManager moduleRootManager, Collection<String> filesToAnalyze) {
    Collection<String> testFolderPrefix = new ArrayList<>();
    for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null) {
          if (sourceFolder.isTestSource()) {
            testFolderPrefix.add(file.getPath());
          }
        }
      }
    }
    Collection<String> sources = new ArrayList<>();
    Collection<String> tests = new ArrayList<>();

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

    if (!sources.isEmpty()) {
      properties.setProperty(PROJECT_SOURCES_PROPERTY, StringUtils.join(sources, SEPARATOR));
    }
    if (!tests.isEmpty()) {
      properties.setProperty(PROJECT_TESTS_PROPERTY, StringUtils.join(tests, SEPARATOR));
    }
  }

  @NotNull
  public static VirtualFile[] getProjectClasspath(@Nullable final Module module) {
    if (module == null) {
      return new VirtualFile[0];
    }
    final List<VirtualFile> found = new LinkedList<VirtualFile>();
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
  public static VirtualFile getCompilerOutputPath(final Module module) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      return compilerModuleExtension.getCompilerOutputPath();
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


  public static void run(ProjectSettings projectSettings, SonarQubeRunner runner, Properties props, SonarQubeConsole console, IssueListener listener) {

      if (projectSettings.isVerboseEnabled()) {
        console.info("Start sonar-runner with args:\n" + propsToString(props));
      }

    runner.startAnalysis(props, listener);

  }


  private static String propsToString(Properties props) {
    StringBuilder builder = new StringBuilder();
    for (Object key : props.keySet()) {
      builder.append(key).append("=").append(props.getProperty(key.toString())).append("\n");
    }
    return builder.toString();
  }
}
