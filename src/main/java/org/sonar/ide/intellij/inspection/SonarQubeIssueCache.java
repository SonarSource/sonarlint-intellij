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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SonarQubeIssueCache implements ProjectComponent {

  private Map<PsiFile, List<IssueOnPsiElement>> localIssuesByFile = new HashMap<PsiFile, List<IssueOnPsiElement>>();
  private List<PsiFile> modifiedFiles = new ArrayList<PsiFile>();

  public Map<PsiFile, List<IssueOnPsiElement>> getLocalIssuesByElement() {
    return localIssuesByFile;
  }

  public List<PsiFile> getModifiedFile() {
    return modifiedFiles;
  }

  @Override
  public void projectOpened() {
    // Nothing to do
  }

  @Override
  public void projectClosed() {
    // Nothing to do
  }

  @Override
  public void initComponent() {
    // Nothing to do
  }

  @Override
  public void disposeComponent() {
    // Nothing to do
  }

  @NotNull
  @Override
  public String getComponentName() {
    return this.getClass().getSimpleName();
  }
}
