/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
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
package org.sonarlint.intellij.inspection;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.Issue;

public class InspectionUtils {

  private static final Logger LOG = Logger.getInstance(InspectionUtils.class);

  private InspectionUtils() {
    // Utility class
  }

  @CheckForNull
  public static String computeRelativePath(Module module, VirtualFile file) {
    String rootPath = getModuleRootPath(module);
    if (rootPath == null) {
      return null;
    }
    String filePath = file.getPath();
    if (filePath.startsWith(rootPath)) {
      return filePath.substring(rootPath.length());
    }
    return null;
  }

  @CheckForNull
  public static String getModuleRootPath(Module module) {
    VirtualFile moduleRoot = getModuleRoot(module);
    return moduleRoot != null ? moduleRoot.getPath() + "/" : null;
  }

  @CheckForNull
  public static VirtualFile getModuleRoot(Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

    VirtualFile[] contentRoots = rootManager.getContentRoots();
    if (contentRoots.length != 1) {
      LOG.error("Module " + module + " contains " + contentRoots.length + " content roots and this is not supported");
      return null;
    }
    return contentRoots[0];
  }

  @NotNull
  public static TextRange getLineRange(@NotNull PsiFile psiFile, Issue issue) {
    Project project = psiFile.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return TextRange.EMPTY_RANGE;
    }
    int line = issue.getLine() != null ? issue.getLine() - 1 : 0;
    return getTextRangeForLine(document, line);
  }

  public static TextRange getLineRange(@NotNull PsiElement psiElement) {
    Project project = psiElement.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(psiElement.getContainingFile().getContainingFile());
    if (document == null) {
      return TextRange.EMPTY_RANGE;
    }
    int line = document.getLineNumber(psiElement.getTextOffset());
    int lineEndOffset = document.getLineEndOffset(line);
    return new TextRange(psiElement.getTextOffset(), lineEndOffset);
  }

  private static TextRange getTextRangeForLine(Document document, int line) {
    try {
      int lineStartOffset = document.getLineStartOffset(line);
      int lineEndOffset = document.getLineEndOffset(line);
      return new TextRange(lineStartOffset, lineEndOffset);
    } catch (IndexOutOfBoundsException e) {
      // Local file should be different than remote
      return TextRange.EMPTY_RANGE;
    }
  }

  @Nullable
  public static PsiElement getStartElementAtLine(@NotNull final PsiFile file, Issue issue) {
    if (issue.getLine() == null) {
      return file;
    }
    int ijLine = issue.getLine() - 1;
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    PsiElement element = null;
    try {
      if (document != null) {
        final int offset = document.getLineStartOffset(ijLine);
        element = file.getViewProvider().findElementAt(offset);
        if (element != null && document.getLineNumber(element.getTextRange().getStartOffset()) != ijLine) {
          element = element.getNextSibling();
        }
      }
    } catch (@NotNull final IndexOutOfBoundsException ignore) {
      // Ignore this exception
    }

    return element;
  }

  @Nullable
  public static PsiElement getEndElementAtLine(@NotNull final PsiFile file, Issue issue) {
    if (issue.getLine() == null) {
      return file;
    }
    int ijLine = issue.getLine() - 1;
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    PsiElement element = null;
    try {
      if (document != null) {
        final int offset = document.getLineEndOffset(ijLine);
        element = file.getViewProvider().findElementAt(offset);
        if (element != null && document.getLineNumber(element.getTextRange().getEndOffset()) != ijLine) {
          element = element.getPrevSibling();
        }
      }
    } catch (@NotNull final IndexOutOfBoundsException ignore) {
      // Ignore this exception
    }

    return element;
  }

  public static String getProblemMessage(@NotNull Issue issue) {
    return issue.getMessage();
  }

}
