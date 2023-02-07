/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;

public class CodeAnalyzerRestarter {
  private final Project myProject;
  private final DaemonCodeAnalyzer codeAnalyzer;

  protected CodeAnalyzerRestarter(Project project) {
    this(project, DaemonCodeAnalyzer.getInstance(project));
  }


  @NonInjectable
  CodeAnalyzerRestarter(Project project, DaemonCodeAnalyzer codeAnalyzer) {
    myProject = project;
    this.codeAnalyzer = codeAnalyzer;
  }


  public void refreshOpenFiles() {
    if (myProject.isDisposed()) {
      return;
    }
    var fileEditorManager = FileEditorManager.getInstance(myProject);

    var openFiles = fileEditorManager.getOpenFiles();
    Stream.of(openFiles)
      .map(this::getPsi)
      .filter(Objects::nonNull)
      .forEach(codeAnalyzer::restart);
  }

  public void refreshFiles(Collection<VirtualFile> changedFiles) {
    if (myProject.isDisposed()) {
      return;
    }
    var fileEditorManager = FileEditorManager.getInstance(myProject);
    var openFiles = fileEditorManager.getOpenFiles();
    Stream.of(openFiles)
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
}
