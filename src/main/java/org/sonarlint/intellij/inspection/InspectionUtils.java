/**
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
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NonNls;
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
  public static TextRange getLineRange(@NotNull PsiFile psiFile, int line) {
    Project project = psiFile.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return TextRange.EMPTY_RANGE;
    }
    return getTextRangeForLine(document, line);
  }

  private static TextRange getTextRangeForLine(Document document, int line) {
    int lineStartOffset = document.getLineStartOffset(line - 1);
    int lineEndOffset = document.getLineEndOffset(line - 1);
    return new TextRange(lineStartOffset, lineEndOffset);
  }

  public static String getProblemMessage(@NotNull Issue issue, boolean isLocal) {
    if (isLocal) {
      @NonNls
      final String link = " <a "
        + "href=\"#sonarissue/" + issue.getRuleKey() + "\""
        + (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "")
        + ">" + issue.getRuleKey()
        + "</a> ";
      return XmlStringUtil.wrapInHtml(link + XmlStringUtil.escapeString(issue.getMessage()));
    } else {
      return issue.getRuleKey() + " " + issue.getMessage();
    }
  }

}
