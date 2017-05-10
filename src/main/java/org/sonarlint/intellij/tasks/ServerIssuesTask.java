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
package org.sonarlint.intellij.tasks;

import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.ServerIssues;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleStorageStatus;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

public class ServerIssuesTask extends AbstractProjectComponent {
    private static final Logger LOGGER = Logger.getInstance(ServerIssuesTask.class);
    private final SonarLintProjectSettings projectSettings;
    private final IssueProcessor issueProcessor;
    private final ConnectedSonarLintEngine engine;
    private final SonarQubeServer server;
    private final ServerIssues serverIssues;
    private final GlobalLogOutput log;

    public ServerIssuesTask(Project project, SonarLintProjectSettings projectSettings, IssueProcessor issueProcessor,
                            ConnectedSonarLintEngine engine, SonarQubeServer server, ServerIssues serverIssues) {
        super(project);
        this.projectSettings = projectSettings;
        this.issueProcessor = issueProcessor;
        this.engine = engine;
        this.server = server;
        this.serverIssues = serverIssues;
        this.log = GlobalLogOutput.get();
    }

    public Task.Modal asModal() {
        return new Task.Modal(null, "Updating SonarQube issues '" + server.getName() + "'", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ServerIssuesTask.this.run(indicator);
            }
        };
    }

    public Task.Backgroundable asBackground() {
        return new Task.Backgroundable(null, "Updating SonarQube issues '" + server.getName() + "'", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ServerIssuesTask.this.run(indicator);
            }
        };
    }

    public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Fetching data...");

        try {
            updateModules();
        } catch (Exception e) {
            LOGGER.info("Error updating issues from local server '" + server.getName() + "'", e);
            final String msg = (e.getMessage() != null) ? e.getMessage() : ("Failed to update issues for local server configuration '" + server.getName() + "'");
            ApplicationManager.getApplication().invokeAndWait(new RunnableAdapter() {
                @Override
                public void doRun() throws Exception {
                    Messages.showErrorDialog((Project) null, msg, "Update Issues Failed");
                }
            }, ModalityState.any());
        }
    }

    private void updateModules() {
        final Set<String> existingProjectKeys = engine.allModulesByKey().keySet();


        String moduleKey = projectSettings.getProjectKey();

        if (existingProjectKeys.contains(moduleKey)) {
            AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
            try {
                updateModule(moduleKey);
            } finally {
                token.finish();
            }
        } else {
            log.log("Module '" + moduleKey + "' in not analyzed on server binding '" + server.getName() + "'", LogOutput.Level.INFO);
        }

    }

    private void updateModule(String moduleKey) {
        try {
            ModuleStorageStatus status = engine.getModuleStorageStatus(moduleKey);
            List<ScannerInput.ServerIssue> serverIssues = readServerIssesFromStorage(moduleKey);
            LocalDateTime lastAnalysis = status != null ? SonarLintUtils.getLocalDateTime(status.getLastUpdateDate()) : LocalDateTime.now();

            Map<VirtualFile, Collection<LiveIssue>> issues = issueProcessor.transformServerIssues(myProject, serverIssues);
            this.serverIssues.set(lastAnalysis, issues);

            log.log("Module '" + moduleKey + "' in server binding '" + server.getName() + "', issues " + serverIssues.size() + ", files " + issues.size()+ ", updated " + lastAnalysis, LogOutput.Level.INFO);

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            // in case of error, show a message box and keep updating other modules
            final String msg = (e.getMessage() != null) ? e.getMessage() : ("Failed to update issues for local server configuration '" + server.getName() + "'");
            ApplicationManager.getApplication().invokeLater(new RunnableAdapter() {
                @Override
                public void doRun() throws Exception {
                    Messages.showErrorDialog((Project) null, msg, "Update Issues Failed");
                }
            }, ModalityState.any());
        }
    }

    private List<ScannerInput.ServerIssue> readServerIssesFromStorage(String moduleKey) throws Exception {
        StorageContainer storageContainer = ((ConnectedSonarLintEngineImpl) engine).getGlobalContainer();
        StorageManager storageManager = storageContainer.getComponentByType(StorageManager.class);

        List<Path> serverFiles = new ArrayList<>();
        getRegularFiles(storageManager.getServerIssuesPath(moduleKey), serverFiles);

        List<ScannerInput.ServerIssue> issues = new ArrayList<>();
        serverFiles.forEach(serverFile -> issues.addAll(readServerIssuesFromFile(serverFile)));
        return issues;
    }

    private void getRegularFiles(Path rootPath, List<Path> pathList) {
        try {
            try (DirectoryStream<Path> dirItem = Files.newDirectoryStream(rootPath)) {
                dirItem.forEach(item -> {
                    if (item.toFile().isDirectory()) getRegularFiles(item, pathList);
                    if (item.toFile().isFile()) pathList.add(item);
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ScannerInput.ServerIssue> readServerIssuesFromFile(Path path) {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(path.toFile()))) {
            return ProtobufUtil.readMessages(inputStream, ScannerInput.ServerIssue.parser());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
