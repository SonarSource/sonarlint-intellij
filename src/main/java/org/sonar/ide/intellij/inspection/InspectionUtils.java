/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
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
package org.sonar.ide.intellij.inspection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.model.ISonarIssue;

public class InspectionUtils {

  private static final char DELIMITER = ':';
  private static final char PACKAGE_DELIMITER = '.';
  private static final String DEFAULT_PACKAGE_NAME = "[default]";

  private InspectionUtils() {
    // Utility class
  }


  public static String getComponentKey(String moduleKey, PsiFile file) {
    if (file instanceof PsiJavaFile) {
      return getJavaComponentKey(moduleKey, (PsiJavaFile) file);
    }
    final StringBuilder result = new StringBuilder();
    result.append(moduleKey).append(":");
    final VirtualFile virtualFile = file.getVirtualFile();
    if (null != virtualFile) {
      final String filePath = virtualFile.getPath();

      VirtualFile sourceRootForFile = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(virtualFile);
      // getSourceRootForFile doesn't work in phpstorm for some reasons
      if (null == sourceRootForFile) {
        sourceRootForFile = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getContentRootForFile(virtualFile);
      }

      if (sourceRootForFile != null) {
        final String sourceRootForFilePath = sourceRootForFile.getPath() + "/";

        String baseFileName = filePath.replace(sourceRootForFilePath, "");

        if (baseFileName.equals(file.getName())) {
          result.append("[root]/");
        }

        result.append(baseFileName);
      }
    }
    return result.toString();
  }

  @NotNull
  public static TextRange getLineRange(@NotNull PsiFile psiFile, ISonarIssue issue) {
    Project project = psiFile.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return TextRange.EMPTY_RANGE;
    }
    int line = issue.line() != null ? issue.line() - 1 : 0;
    return getTextRangeForLine(document, project, psiFile, line);
  }

  @NotNull
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

  private static TextRange getTextRangeForLine(Document document, Project project, PsiFile psiFile, int line) {
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
  public static PsiElement getStartElementAtLine(@NotNull final PsiFile file, ISonarIssue issue) {
    //noinspection ConstantConditions
    if (file == null) {
      return null;
    }
    if (issue.line() == null) {
      return file;
    }
    int ijLine = issue.line() - 1;
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    PsiElement element = null;
    try {
      if (document != null) {
        final int offset = document.getLineStartOffset(ijLine);
        element = file.getViewProvider().findElementAt(offset);
        if (element != null) {
          if (document.getLineNumber(element.getTextOffset()) != ijLine) {
            element = element.getNextSibling();
          }
        }
      }
    } catch (@NotNull final IndexOutOfBoundsException ignore) {
    }

    while (element != null && element.getTextLength() == 0) {
      element = element.getNextSibling();
    }

    return element;
  }

  public static String getProblemMessage(@NotNull ISonarIssue issue) {
    return issue.isNew() ? "NEW: " + issue.message() : issue.message();
  }

  private static String getJavaComponentKey(final String moduleKey, final PsiJavaFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        String result = null;
        String packageName = file.getPackageName();
        if (StringUtils.isWhitespace(packageName)) {
          packageName = DEFAULT_PACKAGE_NAME;
        }
        String fileName = StringUtils.substringBeforeLast(file.getName(), ".");
        if (moduleKey != null && packageName != null) {
          result = new StringBuilder()
              .append(moduleKey).append(DELIMITER).append(packageName)
              .append(PACKAGE_DELIMITER).append(fileName)
              .toString();
        }
        return result;
      }
    });
  }
}
