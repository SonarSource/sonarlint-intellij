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
package org.sonarlint.intellij.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.ServerIssueTrackable;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;

public class ServerIssueUpdater extends AbstractProjectComponent {

  private static final Logger LOGGER = Logger.getInstance(ServerIssueUpdater.class);

  private static final int THREADS_NUM = 5;
  private static final int QUEUE_LIMIT = 100;
  private static final int FETCH_ALL_ISSUES_THRESHOLD = 10;
  private static final int CONNECTION_TIMEOUT = 5_000;
  private static final int READ_TIMEOUT = 2 * 60_000;

  private ExecutorService executorService;

  private final IssueManager issueManager;
  private final SonarLintProjectSettings projectSettings;
  private final ProjectBindingManager projectBindingManager;
  private final SonarLintConsole console;
  private final SonarLintAppUtils appUtils;

  ServerIssueUpdater(Project project, IssueManager issueManager, SonarLintProjectSettings projectSettings,
    ProjectBindingManager projectBindingManager, SonarLintConsole console, SonarLintAppUtils appUtils) {
    super(project);
    this.issueManager = issueManager;
    this.projectSettings = projectSettings;
    this.projectBindingManager = projectBindingManager;
    this.console = console;
    this.appUtils = appUtils;
  }

  public void fetchAndMatchServerIssues(Map<Module, Collection<VirtualFile>> filesPerModule, ProgressIndicator indicator, boolean waitForCompletion) {
    if (!projectSettings.isBindingEnabled()) {
      // not in connected mode
      return;
    }

    try {
      SonarQubeServer server = projectBindingManager.getSonarQubeServer();
      ConnectedSonarLintEngine engine = projectBindingManager.getConnectedEngine();
      String projectKey = projectSettings.getProjectKey();

      int numFiles = filesPerModule.values().stream().mapToInt(Collection::size).sum();
      boolean downloadAll = numFiles >= FETCH_ALL_ISSUES_THRESHOLD;
      String msg;

      if (downloadAll) {
        msg = "Fetching all server issues";
      } else {
        msg = "Fetching server issues in " + numFiles + SonarLintUtils.pluralize(" file", numFiles);
      }
      if (waitForCompletion) {
        msg += " (waiting for results)";
      }
      console.debug(msg);
      indicator.setText(msg);

      // submit tasks
      List<Future<Void>> updateTasks = fetchAndMatchServerIssues(projectKey, filesPerModule, server, engine, downloadAll);

      if (waitForCompletion) {
        waitForTasks(updateTasks);
      }
    } catch (InvalidBindingException e) {
      // ignore, do nothing
    }
  }

  private static void waitForTasks(List<Future<Void>> updateTasks) {
    for (Future<Void> f : updateTasks) {
      try {
        f.get(20, TimeUnit.SECONDS);
      } catch (TimeoutException ex) {
        f.cancel(true);
        LOGGER.warn("ServerIssueUpdater task expired", ex);
      } catch (Exception ex) {
        LOGGER.warn("ServerIssueUpdater task failed", ex);
      }
    }
  }

  private List<Future<Void>> fetchAndMatchServerIssues(String projectKey, Map<Module, Collection<VirtualFile>> filesPerModule,
    SonarQubeServer server, ConnectedSonarLintEngine engine, boolean downloadAll) {
    List<Future<Void>> futureList = new LinkedList<>();

    if (!downloadAll) {
      for (Map.Entry<Module, Collection<VirtualFile>> e : filesPerModule.entrySet()) {
        futureList.addAll(fetchAndMatchServerIssues(projectKey, e.getKey(), e.getValue(), server, engine));
      }
    } else {
      futureList.addAll(downloadAndMatchAllServerIssues(projectKey, filesPerModule, server, engine));
    }
    return futureList;
  }

  private List<Future<Void>> downloadAndMatchAllServerIssues(String projectKey, Map<Module, Collection<VirtualFile>> filesPerModule, SonarQubeServer server,
    ConnectedSonarLintEngine engine) {
    IssueUpdater issueUpdater = new IssueUpdater(server, engine);

    Runnable task = () -> {
      issueUpdater.downloadAllServerIssues(projectKey);

      for (Map.Entry<Module, Collection<VirtualFile>> e : filesPerModule.entrySet()) {
        ProjectBinding binding = getProjectBinding(e.getKey());
        Map<VirtualFile, String> relativePathPerFile = getRelativePaths(e.getKey().getProject(), e.getValue());

        for (Map.Entry<VirtualFile, String> entry : relativePathPerFile.entrySet()) {
          issueUpdater.fetchAndMatchFile(binding, entry.getKey(), entry.getValue());
        }
      }
    };

    return Collections.singletonList(submit(task, projectKey, null));
  }

  private ProjectBinding getProjectBinding(Module module) {
    ModuleBindingManager moduleBindingManager = SonarLintUtils.get(module, ModuleBindingManager.class);
    return moduleBindingManager.getBinding();
  }

  private List<Future<Void>> fetchAndMatchServerIssues(String projectKey, Module module, Collection<VirtualFile> files, SonarQubeServer server, ConnectedSonarLintEngine engine) {
    List<Future<Void>> futureList = new LinkedList<>();
    ProjectBinding binding = getProjectBinding(module);
    Map<VirtualFile, String> relativePathPerFile = getRelativePaths(module.getProject(), files);
    IssueUpdater issueUpdater = new IssueUpdater(server, engine);

    for (Map.Entry<VirtualFile, String> e : relativePathPerFile.entrySet()) {
      Runnable task = () -> issueUpdater.downloadAndMatchFile(binding, e.getKey(), e.getValue());
      futureList.add(submit(task, projectKey, e.getValue()));
    }
    return futureList;
  }

  private Map<VirtualFile, String> getRelativePaths(Project project, Collection<VirtualFile> files) {
    return ApplicationManager.getApplication().<Map<VirtualFile, String>>runReadAction(() -> {
      Map<VirtualFile, String> relativePathPerFile = new HashMap<>();

      for (VirtualFile file : files) {
        String relativePath = appUtils.getPathRelativeToProjectBaseDir(project, file);
        if (relativePath != null) {
          relativePathPerFile.put(file, relativePath);
        }
      }
      return relativePathPerFile;
    });
  }

  private Future<Void> submit(Runnable task, String projectKey, @Nullable String moduleRelativePath) {
    try {
      return this.executorService.submit(task, null);
    } catch (RejectedExecutionException e) {
      LOGGER.debug("fetch and match server issues rejected for projectKey=" + projectKey + ", filepath=" + moduleRelativePath, e);
      return CompletableFuture.completedFuture(null);
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

  private class IssueUpdater {
    private final SonarQubeServer server;
    private final ConnectedSonarLintEngine engine;

    private IssueUpdater(SonarQubeServer server, ConnectedSonarLintEngine engine) {
      this.server = server;
      this.engine = engine;
    }

    public void fetchAndMatchFile(ProjectBinding projectBinding, VirtualFile virtualFile, String relativePath) {
      List<ServerIssue> serverIssues = engine.getServerIssues(projectBinding, relativePath);
      matchFile(virtualFile, serverIssues);
    }

    public void downloadAndMatchFile(ProjectBinding projectBinding, VirtualFile virtualFile, String relativePath) {
      List<ServerIssue> serverIssues = fetchServerIssuesForFile(projectBinding, relativePath);
      matchFile(virtualFile, serverIssues);
    }

    public void downloadAllServerIssues(String projectKey) {
      try {
        ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server, CONNECTION_TIMEOUT, READ_TIMEOUT);
        LOGGER.debug("fetchServerIssues projectKey=" + projectKey);
        engine.downloadServerIssues(serverConfiguration, projectKey);
      } catch (DownloadException e) {
        console.info(e.getMessage());
      }
    }

    private void matchFile(VirtualFile virtualFile, List<ServerIssue> serverIssues) {
      try {
        Collection<Trackable> serverIssuesTrackable = serverIssues.stream()
          .map(ServerIssueTrackable::new)
          .collect(Collectors.toList());

        if (!serverIssuesTrackable.isEmpty()) {
          issueManager.matchWithServerIssues(virtualFile, serverIssuesTrackable);
        }
      } catch (Throwable t) {
        // note: without catching Throwable, any exceptions raised in the thread will not be visible
        console.error("error while fetching and matching server issues", t);
      }
    }

    private List<ServerIssue> fetchServerIssuesForFile(ProjectBinding projectBinding, String relativePath) {
      try {
        ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server, CONNECTION_TIMEOUT, READ_TIMEOUT);
        LOGGER.debug("fetchServerIssues projectKey=" + projectBinding.projectKey() + ", filepath=" + relativePath);
        return engine.downloadServerIssues(serverConfiguration, projectBinding, relativePath);
      } catch (DownloadException e) {
        console.info(e.getMessage());
        return engine.getServerIssues(projectBinding, relativePath);
      }
    }
  }
}
