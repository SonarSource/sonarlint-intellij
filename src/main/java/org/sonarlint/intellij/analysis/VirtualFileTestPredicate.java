package org.sonarlint.intellij.analysis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public class VirtualFileTestPredicate implements Predicate<VirtualFile> {
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
