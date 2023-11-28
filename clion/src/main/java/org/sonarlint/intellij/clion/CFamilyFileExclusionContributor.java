/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.common.analysis.FileExclusionContributor;

public class CFamilyFileExclusionContributor implements FileExclusionContributor {

  @Override
  public ExcludeResult shouldExclude(Module module, VirtualFile fileToAnalyze) {
    if (!isSupported(module.getProject(), fileToAnalyze)) {
      return ExcludeResult.notExcluded();
    }
    var configurationResult = new AnalyzerConfiguration(module.getProject()).getConfiguration(fileToAnalyze);
    if (configurationResult.hasConfiguration()) {
      return ExcludeResult.notExcluded();
    }
    return ExcludeResult.excluded(configurationResult.getSkipReason());
  }

  private static boolean isSupported(Project project, VirtualFile file) {
    var psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile != null && (isInstanceOf(psiFile, "com.jetbrains.cidr.lang.psi.OCPsiFile") || isInstanceOf(psiFile, "com.jetbrains.rider.cpp.fileType.psi.CppFile"));
  }

  private static boolean isInstanceOf(Object object, String className) {
    try {
      return Class.forName(className).isInstance(object);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
