/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
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

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.runner.api.ForkedRunner;
import org.sonar.runner.api.ProcessMonitor;
import org.sonar.runner.api.ScanProperties;
import org.sonar.runner.api.StreamConsumer;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SonarRunnerAnalysis {

  private static final Logger LOG = Logger.getInstance(SonarRunnerAnalysis.class);

  public static final String PROJECT_BRANCH_PROPERTY = "sonar.branch";
  public static final String PROJECT_VERSION_PROPERTY = "sonar.projectVersion";
  public static final String PROJECT_KEY_PROPERTY = "sonar.projectKey";
  public static final String MODULE_KEY_PROPERTY = "sonar.moduleKey";
  public static final String PROJECT_NAME_PROPERTY = "sonar.projectName";
  public static final String PROJECT_LANGUAGE_PROPERTY = "sonar.language";
  public static final String PROJECT_SOURCES_PROPERTY = "sonar.sources";
  public static final String PROJECT_TESTS_PROPERTY = "sonar.tests";
  public static final String PROJECT_LIBRARIES_PROPERTY = "sonar.libraries";
  public static final String PROJECT_BINARIES_PROPERTY = "sonar.binaries";
  public static final String PROJECT_MODULES_PROPERTY = "sonar.modules";
  public static final String ENCODING_PROPERTY = "sonar.sourceEncoding";
  public static final String PROJECT_BASEDIR = "sonar.projectBaseDir";
  public static final String WORK_DIR = "sonar.working.directory";

  private static final char SEPARATOR = ',';

  private static final String JAR_REGEXP = "(.*)!/";
  private static final Pattern JAR_PATTERN = Pattern.compile(JAR_REGEXP);


  public File analyzeProject(ProgressIndicator indicator, Project p, ProjectSettings projectSettings, SonarQubeServer server, boolean debugEnabled, String jvmArgs) {
    // Use SonarQube Runner

    // Configure
    Properties properties = new Properties();
    configureProjectSettings(p, properties, indicator);
    File baseDir = new File(p.getBasePath());
    File projectSpecificWorkDir = new File(new File(baseDir, ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR), "sonarqube");
    properties.setProperty(WORK_DIR, projectSpecificWorkDir.getAbsolutePath());
    File outputFile = new File(projectSpecificWorkDir, "sonar-report.json");
    GlobalConfigurator.configureAnalysis(p, outputFile, projectSettings, server, debugEnabled, new PropertyParamWrapper(properties));

    // Analyse
    // To be sure to not reuse something from a previous analysis
    FileUtils.deleteQuietly(outputFile);
    long start = System.currentTimeMillis();
    LOG.info("Start SonarQube analysis on " + p.getName() + "...\n");
    run(p, properties, debugEnabled, jvmArgs, indicator);
    LOG.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
    return outputFile;
  }

  private void configureProjectSettings(Project p, Properties properties, ProgressIndicator indicator) {
    ProjectSettings settings = p.getComponent(ProjectSettings.class);
    ModuleManager moduleManager = ModuleManager.getInstance(p);
    Module[] ijModules = moduleManager.getModules();
    properties.setProperty(PROJECT_NAME_PROPERTY, p.getName());
    configureEncoding(p, properties);
    configureModuleSettings(p, settings, ijModules[0], properties, "", p.getBasePath());
  }

  private void configureEncoding(Project p, Properties properties) {
    Charset encoding = EncodingProjectManager.getInstance(p).getEncoding(null, true);
    if (encoding != null) {
      properties.setProperty(ENCODING_PROPERTY, encoding.toString());
    }
  }

  private void configureModuleSettings(@NotNull Project p, @NotNull ProjectSettings settings, @NotNull Module ijModule, @NotNull Properties properties, @NotNull String prefix, @NotNull String baseDir) {
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
    if ("".equals(prefix)) {
      // Only on root module
      configureProjectVersion(ijModule, properties, mavenProjectsManager);
      properties.setProperty(PROJECT_KEY_PROPERTY, settings.getModuleKeys().get(ijModule.getName()));
    } else {
      // Only on modules
      properties.setProperty(prefix + MODULE_KEY_PROPERTY, settings.getModuleKeys().get(ijModule.getName()));
    }
    properties.setProperty(prefix + PROJECT_BASEDIR, baseDir);

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(ijModule);
    Set<String> sources = new LinkedHashSet<String>();
    Set<String> tests = new LinkedHashSet<String>();
    for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null) {
          if (sourceFolder.isTestSource()) {
            tests.add(file.getPath());
          } else {
            sources.add(file.getPath());
          }
        }
      }
    }
    if (!sources.isEmpty()) {
      properties.setProperty(prefix + PROJECT_SOURCES_PROPERTY, StringUtils.join(sources, SEPARATOR));
    }
    if (!tests.isEmpty()) {
      properties.setProperty(prefix + PROJECT_TESTS_PROPERTY, StringUtils.join(tests, SEPARATOR));
    }

    List<String> libs = new ArrayList<String>();
    for (VirtualFile f : getProjectClasspath(ijModule)) {
      libs.add(toFile(f.getPath()));
    }
    if (!libs.isEmpty()) {
      properties.setProperty(prefix + PROJECT_LIBRARIES_PROPERTY, StringUtils.join(libs, SEPARATOR));
    }

    VirtualFile compilerOutput = getCompilerOutputPath(ijModule);
    if (compilerOutput != null) {
      properties.setProperty(prefix + ScanProperties.PROJECT_BINARY_DIRS, compilerOutput.getCanonicalPath());
    }

    if (mavenProjectsManager.isMavenizedModule(ijModule)) {
      MavenProject mavenModule = mavenProjectsManager.findProject(ijModule);
      if (mavenModule != null && mavenModule.isAggregator()) {
        List<MavenProject> submodules = mavenProjectsManager.getModules(mavenModule);
        Set<String> submoduleKeys = new HashSet<String>();
        Map<String, String> modulesPathsAndNames = mavenModule.getModulesPathsAndNames();
        for (Map.Entry<String, String> modulePathAndName : modulesPathsAndNames.entrySet()) {
          String subModulePath = modulePathAndName.getKey();
          String subModuleName = modulePathAndName.getValue();
          VirtualFile file = LocalFileSystem.getInstance().findFileByPath(subModulePath);
          if (file != null) {
            Module ijSubModule = ProjectFileIndex.SERVICE.getInstance(p).getModuleForFile(file);
            if (ijSubModule != null) {
              String key = settings.getModuleKeys().get(ijSubModule.getName());
              if (key != null) {
                VirtualFile moduleFile = ijSubModule.getModuleFile();
                if (moduleFile != null) {
                  configureModuleSettings(p, settings, ijSubModule, properties, subModuleName + ".", moduleFile.getParent().getPath());
                  submoduleKeys.add(subModuleName);
                }
              }
            }
          }
        }
        properties.setProperty(prefix + PROJECT_MODULES_PROPERTY, StringUtils.join(submoduleKeys, SEPARATOR));
      }
    }
  }

  @NotNull
  public static VirtualFile[] getProjectClasspath(@Nullable final Module module) {
    //noinspection ConstantConditions
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
        VirtualFile output = getCompilerOutputPath(dependentModule);
        if (output != null) {
          found.add(output);
        }
      } else if (entry instanceof LibraryOrderEntry) {
        Collections.addAll(found, ((LibraryOrderEntry) entry).getLibrary().getFiles(OrderRootType.CLASSES));
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


  private void configureProjectVersion(Module ijModule, Properties properties, MavenProjectsManager mavenProjectsManager) {
    if (mavenProjectsManager.isMavenizedModule(ijModule)) {
      MavenProject mavenModule = mavenProjectsManager.findProject(ijModule);
      if (mavenModule != null) {
        String version = mavenModule.getMavenId().getVersion();
        properties.setProperty(PROJECT_VERSION_PROPERTY, version != null ? version : "1.0-SNAPSHOT");
      } else {
        properties.setProperty(PROJECT_VERSION_PROPERTY, "1.0-SNAPSHOT");
      }
    } else {
      properties.setProperty(PROJECT_VERSION_PROPERTY, "1.0-SNAPSHOT");
    }
  }

  private String toFile(String path) {
    Matcher m = JAR_PATTERN.matcher(path);
    if (m.matches()) {
      return m.group(1);
    }
    return path;
  }


  public void run(Project project, Properties props, boolean debugEnabled, String jvmArgs, final ProgressIndicator monitor) {

    final SonarQubeConsole console = SonarQubeConsole.getSonarQubeConsole(project);
    try {
      if (debugEnabled) {
        LOG.info("Start sonar-runner with args:\n" + propsToString(props));
      }

      ForkedRunner.create(new ProcessMonitor() {
        @Override
        public boolean stop() {
          return monitor.isCanceled();
        }
      })
          .setApp("IntelliJ IDEA", ApplicationInfo.getInstance().getFullVersion())
          .addProperties(props)
          .addJvmArguments(jvmArgs.trim().split("\\s+"))
          .setStdOut(new StreamConsumer() {
            public void consumeLine(String text) {
              console.info(text);
            }
          })
          .setStdErr(new StreamConsumer() {
            public void consumeLine(String text) {
              console.error(text);
            }
          })
          .execute();

    } catch (Exception e) {
      handleException(monitor, e, console);
    }

  }

  private void handleException(final ProgressIndicator monitor, Exception e, SonarQubeConsole console) {
    if (monitor.isCanceled()) {
      // On OSX it seems that cancelling produce an exception so we just ignore it
      return;
    }
    throw new RuntimeException(e);
  }

  private static String propsToString(Properties props) {
    StringBuilder builder = new StringBuilder();
    for (Object key : props.keySet()) {
      builder.append(key).append("=").append(props.getProperty(key.toString())).append("\n");
    }
    return builder.toString();
  }
}
