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
package org.sonarlint.intellij.core;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.issue.IssueStore;
import org.sonarlint.intellij.issue.ServerIssuePointer;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;

public class ServerIssueUpdater extends AbstractProjectComponent {

  private static final Logger LOGGER = Logger.getInstance(ServerIssueUpdater.class);

  private static final int THREADS_NUM = 5;

  private ExecutorService executorService;

  private final IssueStore store;

  public ServerIssueUpdater(Project project, IssueStore store) {
    super(project);
    this.store = store;
  }

  public void trackServerIssues(VirtualFile virtualFile) {
    SonarLintProjectSettings projectSettings = SonarLintUtils.get(myProject, SonarLintProjectSettings.class);
    if (!projectSettings.isBindingEnabled()) {
      // not in connected mode
      return;
    }

    String serverId = projectSettings.getServerId();
    String moduleKey = projectSettings.getProjectKey();
    if (serverId == null || moduleKey == null) {
      // not bound to SQ project
      return;
    }

    ConnectedSonarLintEngine engine = SonarLintUtils.get(SonarLintServerManager.class).getConnectedEngine(serverId);

    String relativePath = getRelativePath(virtualFile);

    fetchAndMatchServerIssues(virtualFile, engine, moduleKey, relativePath);
  }

  private void fetchAndMatchServerIssues(VirtualFile virtualFile, ConnectedSonarLintEngine engine, String moduleKey, String relativePath) {
    Runnable task = () -> {
      Iterator<ServerIssue> serverIssues = fetchServerIssues(engine, moduleKey, relativePath);

      Collection<IssuePointer> serverIssuePointers = toStream(serverIssues).map(ServerIssuePointer::new).collect(Collectors.toList());

      if (!serverIssuePointers.isEmpty()) {
        store.matchWithServerIssues(virtualFile, serverIssuePointers);
      }
    };
    try {
      this.executorService.submit(task);
    } catch (RejectedExecutionException e) {
      LOGGER.debug("fetch and match server issues rejected for moduleKey=" + moduleKey + ", filepath=" + relativePath, e);
    }
  }

  private static <T> Stream<T> toStream(Iterator<T> iterator) {
    Iterable<T> iterable = () -> iterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  private Iterator<ServerIssue> fetchServerIssues(ConnectedSonarLintEngine engine, String moduleKey, String relativePath) {
    try {
      LOGGER.debug("fetchServerIssues moduleKey=" + moduleKey + ", filepath=" + relativePath);
      return engine.downloadServerIssues(moduleKey, relativePath);
    } catch (SonarLintWrappedException e) {
      SonarLintConsole.get(myProject).error("could not download server issues", e);
      return engine.getServerIssues(moduleKey, relativePath);
    } catch (Throwable t) {
      SonarLintConsole.get(myProject).error("could not get server issues", t);
      return Collections.emptyIterator();
    }
  }

  private String getRelativePath(VirtualFile virtualFile) {
    if (myProject.getBasePath() == null) {
      throw new IllegalStateException("no base path in default project");
    }
    return Paths.get(myProject.getBasePath()).relativize(Paths.get(virtualFile.getPath())).toString();
  }

  @Override
  public void initComponent() {
    this.executorService = Executors.newFixedThreadPool(THREADS_NUM);
  }

  @Override
  public void disposeComponent() {
    List<Runnable> rejected = executorService.shutdownNow();
    if (!rejected.isEmpty()) {
      LOGGER.debug("rejected " + rejected.size() + " pending tasks");
    }
  }
}
