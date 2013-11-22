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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.model.ISonarIssue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InspectionUtils {

  private static final Logger LOG = Logger.getInstance(InspectionUtils.class);

  private static final char DELIMITER = ':';
  private static final char PACKAGE_DELIMITER = '.';
  public static final String DEFAULT_PACKAGE_NAME = "[default]";

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

  public static @NotNull TextRange getTextRange(@NotNull Project project, @NotNull PsiFile psiFile, int line) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return TextRange.EMPTY_RANGE;
    }
    int lineStartOffset = document.getLineStartOffset(line - 1);
    int lineEndOffset = document.getLineEndOffset(line - 1);
    return new TextRange(lineStartOffset, lineEndOffset);
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
