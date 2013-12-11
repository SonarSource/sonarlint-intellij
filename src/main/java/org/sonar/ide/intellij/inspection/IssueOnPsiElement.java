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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.model.ISonarIssue;


public class IssueOnPsiElement {

  private final PsiFile psiFile;
  private PsiElement psiElement;
  private final ISonarIssue issue;


  public IssueOnPsiElement(final PsiFile psiFile, final ISonarIssue issue) {
    this.psiFile = psiFile;
    this.issue = issue;

  }

  public PsiFile getPsiFile() {
    return psiFile;
  }

  public ISonarIssue getIssue() {
    return issue;
  }

  @Nullable
  public PsiElement getPsiElement() {
    if (psiElement == null) {
      this.psiElement = InspectionUtils.getStartElementAtLine(psiFile, issue);
    }
    return psiElement;
  }
}
