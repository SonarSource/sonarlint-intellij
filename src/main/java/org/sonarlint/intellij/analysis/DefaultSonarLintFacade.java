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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLogOutput;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.IssueListener;
import org.sonarsource.sonarlint.core.SonarLintClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultSonarLintFacade extends AbstractProjectComponent implements SonarLintFacade {

  private final SonarLintProjectSettings projectSettings;
  private final SonarLintConsole console;
  private boolean started;
  private SonarLintClient sonarlintClient;

  public DefaultSonarLintFacade(Project project, SonarLintProjectSettings projectSettings, SonarLintConsole console) {
    super(project);
    this.projectSettings = projectSettings;
    this.console = console;
  }

  @Override
  public void projectClosed() {
    stop();
  }

  @Nullable
  @Override
  public synchronized String getDescription(String ruleKey) {
    if(!started) {
      return null;
    }

    return sonarlintClient.getHtmlRuleDescription(ruleKey);
  }

  @Override
  public synchronized void startAnalysis(List<AnalysisConfiguration.InputFile> inputFiles, IssueListener issueListener, Map<String, String> additionalProps) {
    if (!started) {
      tryStart();
    }
    if (!started) {
      return;
    }

    Path baseDir = Paths.get(myProject.getBasePath());
    Path workDir = baseDir.resolve(ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR).resolve("sonarlint").toAbsolutePath();
    Map<String, String> props = projectSettings.getAdditionalProperties();
    AnalysisConfiguration config = new AnalysisConfiguration(baseDir, workDir, inputFiles, props);

    sonarlintClient.setVerbose(projectSettings.isVerboseEnabled());
    if (projectSettings.isVerboseEnabled()) {
      SonarApplication sonarLint = ApplicationManager.getApplication().getComponent(SonarApplication.class);
      console.info(String.format("SonarLint [%s] additional properties:%n%s", sonarLint.getVersion(), propsToString(props)));
    }

    sonarlintClient.analyze(config, issueListener);
  }

  private URL[] loadPlugins() throws IOException, URISyntaxException {
    URL pluginsDir = this.getClass().getClassLoader().getResource("plugins");

    if (pluginsDir == null) {
      throw new IllegalStateException("Couldn't find plugins");
    }

    List<URL> pluginsUrls = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(pluginsDir.toURI()))) {
      for (Path path : directoryStream) {
        console.debug("Found plugin: " + path.getFileName().toString());
        pluginsUrls.add(path.toUri().toURL());
      }
    }
    return pluginsUrls.toArray(new URL[pluginsUrls.size()]);
  }

  private static String propsToString(Map<String, String> props) {
    StringBuilder builder = new StringBuilder();
    for (Object key : props.keySet()) {
      builder.append(key).append("=").append(props.get(key.toString())).append("\n");
    }
    return builder.toString();
  }

  private void tryStart() {
    SonarApplication sonarLintPlugin = ApplicationManager.getApplication().getComponent(SonarApplication.class);

    /*
     * Some components in the container use the context classloader to find resources. For example, the ServiceLoader uses it by default
     * to find services declared by some libs.
     */
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

    try {
      URL[] plugins = loadPlugins();

      sonarlintClient = SonarLintClient.builder()
        .addPlugins(plugins)
        .setLogOutput(new SonarLogOutput(console))
        .setVerbose(projectSettings.isVerboseEnabled())
        .build();

      console.info("Starting SonarLint " + sonarLintPlugin.getVersion());
      sonarlintClient.start();
      this.started = true;
    } catch (Exception e) {
      console.error("Unable to start SonarLint", e);
      sonarlintClient = null;
      started = false;
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  @Override
  public synchronized void stop() {
    if (sonarlintClient != null) {
      sonarlintClient.stop();
      sonarlintClient = null;
    }
    started = false;
  }
}
