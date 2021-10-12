/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;

public class SonarLinkHandler extends TooltipLinkHandler {
  @Nullable
  @Override
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null || project.isDisposed()) {
      return null;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    Module moduleForFile = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(psiFile.getVirtualFile());
    if (moduleForFile == null) {
      return null;
    }

    ProjectBindingManager projectBindingManager = SonarLintUtils.getService(project, ProjectBindingManager.class);
    try {
      SonarLintFacade sonarlintFacade = projectBindingManager.getFacade(moduleForFile);
      String description = sonarlintFacade.getDescription(refSuffix);
      String name = sonarlintFacade.getRuleName(refSuffix);
      return transform(refSuffix, name, description);
    } catch (InvalidBindingException e) {
      return "";
    }

  }

  private static String transform(String ruleKey, @Nullable String ruleName, @Nullable String description) {
    if (description == null || ruleName == null) {
      return "<html><body><code>" + ruleKey + "</code></br></body></html>";
    }

    return "<html><body>"
      + "<h2>" + StringEscapeUtils.escapeHtml(ruleName) + "</h2>"
      + "<code>" + ruleKey + "</code></br>"
      + removeBlankLines(description)
      + "</body></html>";
  }

  private static String removeBlankLines(String description) {
    return description.replaceAll("(?m)^\\s*\r?\n", "");
  }
}
