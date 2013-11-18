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

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.toolwindow.SonarQubeToolWindowFactory;
import org.sonar.runner.api.ForkedRunner;
import org.sonar.runner.api.ProcessMonitor;
import org.sonar.runner.api.StreamConsumer;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SonarRunnerAnalysis {

  private static final Logger LOG = Logger.getInstance(SonarRunnerAnalysis.class);

  public static final String PROJECT_BRANCH_PROPERTY = "sonar.branch";
  public static final String PROJECT_VERSION_PROPERTY = "sonar.projectVersion";
  public static final String PROJECT_KEY_PROPERTY = "sonar.projectKey";
  public static final String PROJECT_NAME_PROPERTY = "sonar.projectName";
  public static final String PROJECT_LANGUAGE_PROPERTY = "sonar.language";
  public static final String PROJECT_SOURCES_PROPERTY = "sonar.sources";
  public static final String PROJECT_TESTS_PROPERTY = "sonar.tests";
  public static final String PROJECT_LIBRARIES_PROPERTY = "sonar.libraries";
  public static final String PROJECT_MODULES_PROPERTY = "sonar.modules";
  public static final String ENCODING_PROPERTY = "sonar.sourceEncoding";
  public static final String PROJECT_BASEDIR = "sonar.projectBaseDir";

  private static final char SEPARATOR = ',';

  private static final String jarRegexp = "(jar://)?(.*)!/";
  private static final Pattern jarPattern = Pattern.compile(jarRegexp);


  public File analyzeSingleModuleProject(ProgressIndicator indicator, Project p, ProjectSettings projectSettings, SonarQubeServer server, boolean debugEnabled, String jvmArgs) {
    // Use SonarQube Runner

    // Configure
    Properties properties = new Properties();
    configureProjectSettings(p, properties, indicator);
    File baseDir = new File(p.getBasePath());
    File projectSpecificWorkDir = new File(new File(baseDir, ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR), "sonarqube");
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
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
    ModuleManager moduleManager = ModuleManager.getInstance(p);
    Module[] ijModules = moduleManager.getModules();
    properties.setProperty(PROJECT_NAME_PROPERTY, p.getName());
    configureModuleSettings(p, settings, ijModules[0], properties, "", p.getBasePath());
  }

  private void configureModuleSettings(Project p, ProjectSettings settings, Module ijModule, Properties properties, String prefix, String baseDir) {
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
    // Only on root module
    if ("".equals(prefix)) {
      if (mavenProjectsManager.isMavenizedModule(ijModule)) {
        MavenProject mavenModule = mavenProjectsManager.findProject(ijModule);
        properties.setProperty(PROJECT_VERSION_PROPERTY, mavenModule.getMavenId().getVersion());
      } else {
        properties.setProperty(PROJECT_VERSION_PROPERTY, "1.0-SNAPSHOT");
      }
    }
    properties.setProperty(prefix + PROJECT_KEY_PROPERTY, settings.getModuleKeys().get(ijModule.getName()));
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
    for (Library lib : LibraryTablesRegistrar.getInstance().getLibraryTable(p).getLibraries()) {
      for (VirtualFile f : lib.getFiles(OrderRootType.CLASSES)) {
        libs.add(toFile(f.getPath()));
      }
    }
    if (!libs.isEmpty()) {
      properties.setProperty(prefix + PROJECT_LIBRARIES_PROPERTY, StringUtils.join(libs, SEPARATOR));
    }
    if (mavenProjectsManager.isMavenizedModule(ijModule)) {
      MavenProject mavenModule = mavenProjectsManager.findProject(ijModule);
      if (mavenModule.isAggregator()) {
        List<MavenProject> submodules = mavenProjectsManager.getModules(mavenModule);
        Set<String> submoduleKeys = new HashSet<String>();
        Map<String, String> modulesPathsAndNames = mavenModule.getModulesPathsAndNames();
        for (Map.Entry<String, String> modulePathAndName : modulesPathsAndNames.entrySet()) {
          String subModulePath = modulePathAndName.getKey();
          String subModuleName = modulePathAndName.getValue();
          Module ijSubModule = ProjectFileIndex.SERVICE.getInstance(p).getModuleForFile(LocalFileSystem.getInstance().findFileByPath(subModulePath));
          String key = settings.getModuleKeys().get(ijSubModule.getName());
          if (key != null) {
            configureModuleSettings(p, settings, ijSubModule, properties, subModuleName + ".", ijSubModule.getModuleFile().getParent().getPath());
            submoduleKeys.add(subModuleName);
          }
        }
        properties.setProperty(prefix + PROJECT_MODULES_PROPERTY, StringUtils.join(submoduleKeys, SEPARATOR));
      }
    }
  }

  private String toFile(String path) {
    Matcher m = jarPattern.matcher(path);
    if (m.matches()) {
      return m.group(1);
    }
    return path;
  }


  public void run(Project project, Properties props, boolean debugEnabled, String jvmArgs, final ProgressIndicator monitor) {

    try {
      final ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
      final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarQubeToolWindowFactory.ID);
      toolWindow.show(new Runnable() {
        @Override
        public void run() {
          Content content = toolWindow.getContentManager().getFactory().createContent(consoleView.getComponent(), "SonarQube Console", true);
          toolWindow.getContentManager().addContent(content);
        }
      });

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
              consoleView.print(text + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
            }
          })
          .setStdErr(new StreamConsumer() {
            public void consumeLine(String text) {
              consoleView.print(text + "\n", ConsoleViewContentType.ERROR_OUTPUT);
            }
          })
          .execute();

    } catch (Exception e) {
      handleException(monitor, e);
    }

  }

  private void handleException(final ProgressIndicator monitor, Exception e) {
    if (monitor.isCanceled()) {
      // On OSX it seems that cancelling produce an exception
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
