package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

class PathValidator implements StoreKeyValidator<String> {
  private final Project project;

  public PathValidator(Project project) {
    this.project = project;
  }

  @Override public Boolean apply(String relativeFilePath) {
    VirtualFile virtualFile = project.getBaseDir().findFileByRelativePath(relativeFilePath);
    return virtualFile != null && virtualFile.isValid();
  }
}
