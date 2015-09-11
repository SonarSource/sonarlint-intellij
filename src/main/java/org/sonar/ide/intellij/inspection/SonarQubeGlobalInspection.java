package org.sonar.ide.intellij.inspection;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;

import java.io.File;

public class SonarQubeGlobalInspection extends GlobalInspectionTool {

  @Override
  public void runInspection(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager, @NotNull final GlobalInspectionContext globalContext, @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final Project project = globalContext.getProject();
    final SonarQubeConsole sonarQubeConsole = SonarQubeConsole.getSonarQubeConsole(project);
    final Multimap<Module, String> filesToAnalyze = HashMultimap.create();
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile psiFile) {
        final Module module = ModuleUtil.findModuleForPsiElement(psiFile);
        if (module != null) {
          String realFile = realFilePath(module, psiFile);
          if (realFile != null) {
            filesToAnalyze.put(module, realFile);
          }
        }
      }
    });
    for (Module m : filesToAnalyze.keySet()) {
      final VirtualFile baseDir = InspectionUtils.getModuleRoot(m);
      if (baseDir == null) {
        throw new IllegalStateException("No basedir for module " + m);
      }
      SonarRunnerAnalysis.analyzeModule(m, filesToAnalyze.get(m), new IssueListener() {
        @Override
        public void handle(Issue issue) {
          String path = StringUtils.substringAfterLast(issue.getComponentKey(), ":");
          VirtualFile file = baseDir.findFileByRelativePath(path);
          if (file != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
              PsiElement element = InspectionUtils.getStartElementAtLine(psiFile, issue);
              RefElement reference = globalContext.getRefManager().getReference(element != null ? element : psiFile);
              problemDescriptionsProcessor.addProblemElement(reference, manager.createProblemDescriptor(element != null ? element : psiFile,
                  InspectionUtils.getProblemMessage(issue),
                  new LocalQuickFix[0],
                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                  false,
                  false
              ));
            }
          }
        }
      });
    }
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


  @Override
  public boolean isGraphNeeded() {
    return false;
  }

}