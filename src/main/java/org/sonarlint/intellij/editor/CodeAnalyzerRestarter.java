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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.IssueStoreListener;

public class CodeAnalyzerRestarter extends AbstractProjectComponent implements IssueStoreListener {
  private final FileEditorManager fileEditorManager;
  private final DaemonCodeAnalyzer codeAnalyzer;
  private final PsiManager psiManager;
  private final MessageBus messageBus;

  protected CodeAnalyzerRestarter(Project project, FileEditorManager fileEditorManager,
    DaemonCodeAnalyzer codeAnalyzer, PsiManager psiManager, MessageBus messageBus) {
    super(project);
    this.fileEditorManager = fileEditorManager;
    this.codeAnalyzer = codeAnalyzer;
    this.psiManager = psiManager;
    this.messageBus = messageBus;
  }

  @Override
  public void initComponent() {
    MessageBusConnection busConnection = messageBus.connect(myProject);
    busConnection.subscribe(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC, this);
  }

  void refreshAllFiles() {
    VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
    Arrays.stream(openFiles)
      .map(this::getPsi)
      .filter(Objects::nonNull)
      .forEach(codeAnalyzer::restart);
  }

  void refreshFiles(Collection<VirtualFile> changedFiles) {
    VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
    Arrays.stream(openFiles)
      .filter(changedFiles::contains)
      .map(this::getPsi)
      .filter(Objects::nonNull)
      .forEach(codeAnalyzer::restart);
  }

  @CheckForNull
  private PsiFile getPsi(VirtualFile virtualFile) {
    if (!virtualFile.isValid()) {
      return null;
    }
    return psiManager.findFile(virtualFile);
  }

  @Override public void filesChanged(final Map<VirtualFile, Collection<LiveIssue>> map) {
    ApplicationManager.getApplication().invokeLater(() -> refreshFiles(map.keySet()));
  }

  @Override public void allChanged() {
    ApplicationManager.getApplication().invokeLater(this::refreshAllFiles);
  }
}
