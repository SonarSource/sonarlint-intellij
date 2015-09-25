package org.sonarlint.intellij.inspection;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
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
import org.sonarlint.intellij.console.SonarLintConsole;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;

public class SonarLintGlobalInspection extends GlobalInspectionTool {

  @Override
  public void runInspection(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager, @NotNull final GlobalInspectionContext globalContext,
    @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final Project project = globalContext.getProject();
    final SonarLintConsole sonarLintConsole = SonarLintConsole.getSonarQubeConsole(project);
    final Multimap<Module, String> filesToAnalyze = HashMultimap.create();
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile psiFile) {
        if (psiFile.getFileType().isBinary() || !psiFile.isValid()) {
          return;
        }
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
      SonarLintAnalysisConfigurator.analyzeModule(m, filesToAnalyze.get(m), new IssueListener() {
        @Override
        public void handle(Issue issue) {
          String path = StringUtils.substringAfterLast(issue.getComponentKey(), ":");
          VirtualFile file = baseDir.findFileByRelativePath(path);
          if (file != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {

              RefElement reference = globalContext.getRefManager().getReference(psiFile);
              problemDescriptionsProcessor.addProblemElement(reference, getProblemDescriptor(issue, psiFile, manager, false));
            }
          }
        }
      });
    }
  }

  ProblemDescriptor getProblemDescriptor(Issue issue, PsiFile psiFile, @NotNull InspectionManager manager, boolean isLocal) {
    PsiElement startElement = InspectionUtils.getStartElementAtLine(psiFile, issue);
    PsiElement endElement = InspectionUtils.getEndElementAtLine(psiFile, issue);
    if (endElement != null) {
      return manager.createProblemDescriptor(startElement != null ? startElement : psiFile,
        endElement,
        InspectionUtils.getProblemMessage(issue, isLocal),
        problemHighlightType(issue, isLocal),
        false);
    } else {
      return manager.createProblemDescriptor(startElement != null ? startElement : psiFile,
        InspectionUtils.getProblemMessage(issue, isLocal),
        new LocalQuickFix[0],
        problemHighlightType(issue, isLocal),
        false,
        false);
    }
  }

  ProblemHighlightType problemHighlightType(Issue issue, boolean isLocal) {
    if (isLocal) {
      return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }
    switch (issue.getSeverity()) {
      case "BLOCKER":
      case "CRITICAL":
        return ProblemHighlightType.ERROR;
      case "MAJOR":
        return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      case "MINOR":
        return ProblemHighlightType.WEAK_WARNING;
      case "INFO":
        return ProblemHighlightType.INFORMATION;
      default:
        return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
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

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new SonarLintLocalInspection(this);
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

}
