/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.common.analysis.FileExclusionContributor;

public class CFamilyFileExclusionContributor implements FileExclusionContributor {

  @Override
  public ExcludeResult shouldExclude(Module module, VirtualFile fileToAnalyze) {
    var psiFile = PsiManager.getInstance(module.getProject()).findFile(fileToAnalyze);
    if(!(psiFile instanceof OCPsiFile)){
      return  ExcludeResult.notExcluded();
    }
    var configurationResult = new AnalyzerConfiguration(module.getProject()).getConfiguration(fileToAnalyze);
    if (configurationResult.hasConfiguration()) {
      return ExcludeResult.notExcluded();
    }
    return ExcludeResult.excluded(configurationResult.getSkipReason());
  }
}
