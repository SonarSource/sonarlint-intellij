/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;
import org.sonarlint.intellij.util.Async;

public class SonarLintLocalInspection extends LocalInspectionTool {

  public static final ProblemDescriptor[] NO_PROBLEMS = new ProblemDescriptor[0];
  private final SonarLintGlobalInspection delegate;

  public SonarLintLocalInspection(SonarLintGlobalInspection delegate) {
    this.delegate = delegate;
  }

  @Override
  public final String getShortName() {
    return delegate.getShortName();
  }

  @Nls
  @NotNull
  @Override
  public final String getDisplayName() {
    return delegate.getDisplayName();
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (file.getFileType().isBinary() || !file.isValid() ||
        // In PHPStorm the same PHP file is analyzed twice (once as PHP file and once as HTML file)
        "html".equalsIgnoreCase(file.getFileType().getName())) {
      return NO_PROBLEMS;
    }
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) {
      return NO_PROBLEMS;
    }
    final VirtualFile baseDir = InspectionUtils.getModuleRoot(module);
    if (baseDir == null) {
      throw new IllegalStateException("No basedir for module " + module);
    }
    String baseDirPath = baseDir.getCanonicalPath();
    if (baseDirPath == null) {
      throw new IllegalStateException("No basedir for module " + module);
    }
    VirtualFile vFile = SonarLintGlobalInspection.virtualFile(file, isOnTheFly);
    if (vFile == null) {
      return NO_PROBLEMS;
    }
    // SonarLint need content of the file to be written on disk
    if (isOnTheFly && saveFile(vFile)) {
      // Inspection will be triggered again since we saved the file
      return NO_PROBLEMS;
    }
    final String absolutePath = vFile.getCanonicalPath();
    if (absolutePath == null) {
      return NO_PROBLEMS;
    }
    final String relativePath = StringUtil.trimStart(absolutePath.substring(baseDirPath.length()), "/");
    final Collection<Issue> issues =
        Async.asyncResultOf(new Callable<Collection<Issue>>() {
          @Override
          public Collection<Issue> call() throws Exception {
            return runAnalysis(module, absolutePath, relativePath);
          }
        }, Collections.<Issue>emptyList());

    Collection<ProblemDescriptor> descriptors = new ArrayList<>();
    for (Issue issue : issues) {
      descriptors.add(delegate.getProblemDescriptor(issue, file, manager, true));
    }
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static boolean saveFile(@NotNull final VirtualFile virtualFile) {
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    if (fileDocumentManager.isFileModified(virtualFile)) {
      final Document document = fileDocumentManager.getDocument(virtualFile);
      if (document != null) {
        ApplicationManager.getApplication().invokeLater(
            new Runnable() {
              @Override
              public void run() {
                fileDocumentManager.saveDocument(document);
              }
            }, ModalityState.NON_MODAL
        );
        return true;
      }
    }
    return false;
  }


  @NotNull
  Collection<Issue> runAnalysis(Module module, final String fileAbsolutePath, final String fileRelativePath) {
    final Collection<Issue> issues = new ArrayList<>();
    SonarLintAnalysisConfigurator.analyzeModule(module, Collections.singleton(fileAbsolutePath), new IssueListener() {
      @Override
      public void handle(Issue issue) {
        String path = StringUtils.substringAfterLast(issue.getComponentKey(), ":");
        if (path.equals(fileRelativePath)) {
          issues.add(issue);
        }
      }
    });
    return issues;
  }

}
