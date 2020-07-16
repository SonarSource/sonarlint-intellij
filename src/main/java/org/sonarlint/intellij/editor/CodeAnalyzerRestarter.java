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

public class CodeAnalyzerRestarter implements IssueStoreListener {
  private final MessageBus messageBus;
  private final Project myProject;
  private final DaemonCodeAnalyzer codeAnalyzer;

  protected CodeAnalyzerRestarter(Project project) {
    this(project, DaemonCodeAnalyzer.getInstance(project));
  }


  /**
   * TODO Replace @Deprecated with @NonInjectable when switching to 2019.3 API level
   * @deprecated in 4.2 to silence a check in 2019.3
   */
  @Deprecated
  CodeAnalyzerRestarter(Project project, DaemonCodeAnalyzer codeAnalyzer) {
    myProject = project;
    this.messageBus = project.getMessageBus();
    this.codeAnalyzer = codeAnalyzer;
  }

  public void init() {
    MessageBusConnection busConnection = messageBus.connect(myProject);
    busConnection.subscribe(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC, this);
  }

  void refreshAllFiles() {
    if (myProject.isDisposed()) {
      return;
    }
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);

    VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
    Arrays.stream(openFiles)
      .map(this::getPsi)
      .filter(Objects::nonNull)
      .forEach(codeAnalyzer::restart);
  }

  void refreshFiles(Collection<VirtualFile> changedFiles) {
    if (myProject.isDisposed()) {
      return;
    }
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
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
    return PsiManager.getInstance(myProject).findFile(virtualFile);
  }

  @Override public void filesChanged(final Map<VirtualFile, Collection<LiveIssue>> map) {
    ApplicationManager.getApplication().invokeLater(() -> refreshFiles(map.keySet()));
  }

  @Override public void allChanged() {
    ApplicationManager.getApplication().invokeLater(this::refreshAllFiles);
  }
}
