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
package org.sonar.ide.intellij.inspection;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class SonarQubeInspectionExtensionsFactory extends InspectionExtensionsFactory {


  @Override
  public GlobalInspectionContextExtension createGlobalInspectionContextExtension() {
    return new SonarQubeInspectionContext();
  }

  @Nullable
  @Override
  public RefManagerExtension createRefManagerExtension(RefManager refManager) {
    return null;
  }

  @Nullable
  @Override
  public HTMLComposerExtension createHTMLComposerExtension(HTMLComposer composer) {
    return null;
  }

  @Override
  public boolean isToCheckMember(PsiElement element, String id) {
    return false;
  }

  @Nullable
  @Override
  public String getSuppressedInspectionIdsIn(PsiElement element) {
    return null;
  }

  @Override
  public boolean isProjectConfiguredToRunInspections(Project project, boolean online) {
    return true;
  }
}
