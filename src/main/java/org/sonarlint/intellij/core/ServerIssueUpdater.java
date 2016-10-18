/*
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.issue.ServerIssuePointer;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;

public class ServerIssueUpdater extends AbstractProjectComponent {

  private static final Logger LOGGER = Logger.getInstance(ServerIssueUpdater.class);

  private static final int THREADS_NUM = 5;
  private static final int QUEUE_LIMIT = 20;

  private ExecutorService executorService;

  private final IssueManager store;

  public ServerIssueUpdater(Project project, IssueManager store) {
    super(project);
    this.store = store;
  }

  public void fetchAndMatchServerIssues(VirtualFile virtualFile) {
    SonarLintProjectSettings projectSettings = SonarLintUtils.get(myProject, SonarLintProjectSettings.class);
    if (!projectSettings.isBindingEnabled()) {
      // not in connected mode
      return;
    }

    String serverId = projectSettings.getServerId();
    String moduleKey = projectSettings.getProjectKey();
    if (serverId == null) {
      SonarLintProjectNotifications.get(myProject).notifyServerIdInvalid();
      return;
    }

    if (moduleKey == null) {
      SonarLintProjectNotifications.get(myProject).notifyModuleInvalid();
      return;
    }

    ConnectedSonarLintEngine engine = SonarLintUtils.get(SonarLintServerManager.class).getConnectedEngine(serverId);

    String relativePath = SonarLintUtils.getRelativePath(myProject, virtualFile);

    fetchAndMatchServerIssues(virtualFile, engine, moduleKey, relativePath);
  }

  private void fetchAndMatchServerIssues(VirtualFile virtualFile, ConnectedSonarLintEngine engine, String moduleKey, String relativePath) {
    Runnable task = () -> {
      try {
        Iterator<ServerIssue> serverIssues = fetchServerIssues(engine, moduleKey, relativePath);

        Collection<IssuePointer> serverIssuePointers = toStream(serverIssues).map(ServerIssuePointer::new).collect(Collectors.toList());

        if (!serverIssuePointers.isEmpty()) {
          store.matchWithServerIssues(virtualFile, serverIssuePointers);
        }
      } catch (Throwable t) {
        // note: without catching Throwable, any exceptions raised in the thread will not be visible
        SonarLintConsole.get(myProject).error("error while fetching and matching server issues", t);
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
    }
  }

  @Override
  public void initComponent() {
    // Equivalent to Executors.newFixedThreadPool(THREADS_NUM), but instead of the default unlimited LinkedBlockingQueue,
    // we use ArrayBlockingQueue with a cap. This means that if QUEUE_LIMIT tasks are already queued (and THREADS_NUM being executed),
    // new tasks will be rejected with RejectedExecutionException.
    // http://www.nurkiewicz.com/2014/11/executorservice-10-tips-and-tricks.html
    final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
    this.executorService = new ThreadPoolExecutor(THREADS_NUM, THREADS_NUM, 0L, TimeUnit.MILLISECONDS, queue);
  }

  @Override
  public void disposeComponent() {
    List<Runnable> rejected = executorService.shutdownNow();
    if (!rejected.isEmpty()) {
      LOGGER.debug("rejected " + rejected.size() + " pending tasks");
    }
  }
}
