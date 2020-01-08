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

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLinkHandler extends TooltipLinkHandler {
  @Nullable
  @Override
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null || project.isDisposed()) {
      return null;
    }

    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    try {
      SonarLintFacade sonarlint = projectBindingManager.getFacade();
      String description = sonarlint.getDescription(refSuffix);
      String name = sonarlint.getRuleName(refSuffix);
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
      + removeEmptyLines(description)
      + "</body></html>";
  }

  private static String removeEmptyLines(String description) {
    return description.replaceAll("\n(\\s*\n)+", "\n");
  }
}
