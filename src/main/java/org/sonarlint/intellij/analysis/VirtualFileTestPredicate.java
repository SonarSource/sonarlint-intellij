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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public class VirtualFileTestPredicate extends ModuleAdapter implements Predicate<VirtualFile> {
  private final Collection<String> testFolderPrefixes;
  private final ModuleRootManager moduleRootManager;

  public VirtualFileTestPredicate(Module module) {
    this.moduleRootManager = ModuleRootManager.getInstance(module);
    this.testFolderPrefixes = findTestFolderPrefixes();
  }

  @Override public boolean test(VirtualFile virtualFile) {
    return isTestFile(testFolderPrefixes, virtualFile);
  }

  private Collection<String> findTestFolderPrefixes() {
    Collection<String> testFolderPrefix = new ArrayList<>();
    for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null && sourceFolder.isTestSource()) {
          testFolderPrefix.add(file.getPath());
        }
      }
    }

    return testFolderPrefix;
  }

  private static boolean isTestFile(Collection<String> testFolderPrefix, VirtualFile f) {
    String filePath = f.getPath();
    for (String testPrefix : testFolderPrefix) {
      if (filePath.startsWith(testPrefix)) {
        return true;
      }
    }
    return false;
  }
}
