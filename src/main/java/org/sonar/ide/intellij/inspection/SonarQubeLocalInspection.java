package org.sonar.ide.intellij.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SonarQubeLocalInspection extends LocalInspectionTool {

  private final SonarQubeGlobalInspection delegate;

  public SonarQubeLocalInspection(SonarQubeGlobalInspection delegate) {
    this.delegate = delegate;
  }

  @NotNull
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
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (file.getFileType().isBinary() || !file.isValid()) {
      return new ProblemDescriptor[0];
    }
    final Project project = file.getProject();
    final SonarQubeConsole sonarQubeConsole = SonarQubeConsole.getSonarQubeConsole(project);
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) {
      return new ProblemDescriptor[0];
    }
      final VirtualFile baseDir = InspectionUtils.getModuleRoot(module);
      if (baseDir == null) {
        throw new IllegalStateException("No basedir for module " + module);
      }
    String filePath = realFilePath(module, file);
    if (filePath == null) {
      return new ProblemDescriptor[0];
    }
    final Collection<ProblemDescriptor> problems = new ArrayList<>();
    SonarRunnerAnalysis.analyzeModule(module, Collections.singleton(filePath), new IssueListener() {
        @Override
        public void handle(Issue issue) {
          String path = StringUtils.substringAfterLast(issue.getComponentKey(), ":");
          VirtualFile file = baseDir.findFileByRelativePath(path);
          if (file != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
              PsiElement element = InspectionUtils.getStartElementAtLine(psiFile, issue);
              problems.add(manager.createProblemDescriptor(
                  element != null ? element : psiFile,
                  InspectionUtils.getProblemMessage(issue),
                  null,
                  delegate.problemHighlightType(issue),
                  isOnTheFly,
                  false));
            }
          }
        }
      });

    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Nullable
  private String realFilePath(Module module, PsiFile psiFile) {
    if (!existsOnFilesystem(psiFile)
        || documentIsModifiedAndUnsaved(psiFile.getVirtualFile())) {
      return null;
    } else {
      return pathOf(psiFile);
    }
  }

  private String pathOf(@NotNull final PsiFile file) {
    if (file.getVirtualFile() != null) {
      return file.getVirtualFile().getPath();
    }
    throw new IllegalStateException("PSIFile does not have associated virtual file: " + file);
  }

  private boolean existsOnFilesystem(@NotNull final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null && LocalFileSystem.getInstance().exists(virtualFile);
  }

  private boolean documentIsModifiedAndUnsaved(final VirtualFile virtualFile) {
    if (virtualFile == null) {
      return false;
    }
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    if (fileDocumentManager.isFileModified(virtualFile)) {
      final Document document = fileDocumentManager.getDocument(virtualFile);
      if (document != null) {
        return fileDocumentManager.isDocumentUnsaved(document);
      }
    }
    return false;
  }

}