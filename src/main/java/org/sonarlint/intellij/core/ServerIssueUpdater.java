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
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.ServerIssueTrackable;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;

public class ServerIssueUpdater extends AbstractProjectComponent {

  private static final Logger LOGGER = Logger.getInstance(ServerIssueUpdater.class);

  private static final int THREADS_NUM = 5;
  private static final int QUEUE_LIMIT = 20;

  private ExecutorService executorService;

  private final IssueManager issueManager;
  private final SonarLintProjectSettings projectSettings;
  private final ProjectBindingManager projectBindingManager;
  private final SonarLintConsole console;

  ServerIssueUpdater(Project project, IssueManager issueManager, SonarLintProjectSettings projectSettings,
    ProjectBindingManager projectBindingManager, SonarLintConsole console) {
    super(project);
    this.issueManager = issueManager;
    this.projectSettings = projectSettings;
    this.projectBindingManager = projectBindingManager;
    this.console = console;
  }

  public void fetchAndMatchServerIssues(Collection<VirtualFile> virtualFiles) {
    if (!projectSettings.isBindingEnabled()) {
      // not in connected mode
      return;
    }

    SonarQubeServer server = projectBindingManager.getSonarQubeServer();
    ConnectedSonarLintEngine engine = projectBindingManager.getConnectedEngine();
    String moduleKey = projectSettings.getProjectKey();

    for (VirtualFile virtualFile : virtualFiles) {
      String relativePath = SonarLintUtils.getRelativePath(myProject, virtualFile);
      fetchAndMatchServerIssues(virtualFile, server, engine, moduleKey, relativePath);
    }
  }

  private void fetchAndMatchServerIssues(VirtualFile virtualFile, SonarQubeServer server, ConnectedSonarLintEngine engine, String moduleKey, String relativePath) {
    Runnable task = new IssueUpdateRunnable(server, virtualFile, engine, moduleKey, relativePath);
    try {
      this.executorService.submit(task);
    } catch (RejectedExecutionException e) {
      LOGGER.debug("fetch and match server issues rejected for moduleKey=" + moduleKey + ", filepath=" + relativePath, e);
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

  private class IssueUpdateRunnable implements Runnable {
    private final SonarQubeServer server;
    private final VirtualFile virtualFile;
    private final ConnectedSonarLintEngine engine;
    private final String moduleKey;
    private final String relativePath;

    private IssueUpdateRunnable(SonarQubeServer server, VirtualFile virtualFile, ConnectedSonarLintEngine engine, String moduleKey, String relativePath) {
      this.server = server;
      this.virtualFile = virtualFile;
      this.engine = engine;
      this.moduleKey = moduleKey;
      this.relativePath = relativePath;
    }

    @Override public void run() {
      try {
        Iterator<ServerIssue> serverIssues = fetchServerIssues(server, engine, moduleKey, relativePath);
        Collection<Trackable> serverIssuesTrackable = toStream(serverIssues).map(ServerIssueTrackable::new).collect(Collectors.toList());

        if (!serverIssuesTrackable.isEmpty()) {
          issueManager.matchWithServerIssues(virtualFile, serverIssuesTrackable);
        }
      } catch (Throwable t) {
        // note: without catching Throwable, any exceptions raised in the thread will not be visible
        console.error("error while fetching and matching server issues", t);
      }
    }

    private <T> Stream<T> toStream(Iterator<T> iterator) {
      Iterable<T> iterable = () -> iterator;
      return StreamSupport.stream(iterable.spliterator(), false);
    }

    private Iterator<ServerIssue> fetchServerIssues(SonarQubeServer server, ConnectedSonarLintEngine engine, String moduleKey, String relativePath) {
      try {
        ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
        LOGGER.debug("fetchServerIssues moduleKey=" + moduleKey + ", filepath=" + relativePath);
        String fileKey = SonarLintUtils.toFileKey(relativePath);
        return engine.downloadServerIssues(serverConfiguration, moduleKey, fileKey);
      } catch (DownloadException e) {
        console.info(e.getMessage());
        return engine.getServerIssues(moduleKey, relativePath);
      }
    }
  }
}
