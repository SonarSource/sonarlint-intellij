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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;

public class SonarLintGlobalInspection extends GlobalInspectionTool {

  @Override
  public void runInspection(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager, @NotNull final GlobalInspectionContext globalContext,
                            @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final Project project = globalContext.getProject();
    final Multimap<Module, String> filesToAnalyzeByModule = findFilesToAnalyze(scope);
    for (Module m : filesToAnalyzeByModule.keySet()) {
      runAnalysis(manager, globalContext, problemDescriptionsProcessor, project, filesToAnalyzeByModule, m);
    }
  }

  void runAnalysis(@NotNull final InspectionManager manager, @NotNull final GlobalInspectionContext globalContext,
                   @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor, final Project project,
                   Multimap<Module, String> filesToAnalyzeByModule, Module m) {
    final VirtualFile baseDir = InspectionUtils.getModuleRoot(m);
    if (baseDir == null) {
      throw new IllegalStateException("No basedir for module " + m);
    }
    SonarLintAnalysisConfigurator.analyzeModule(m, filesToAnalyzeByModule.get(m), new IssueListener() {
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

  @NotNull
  Multimap<Module, String> findFilesToAnalyze(@NotNull AnalysisScope scope) {
    final Multimap<Module, String> filesToAnalyze = HashMultimap.create();
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile psiFile) {
        if (psiFile.getFileType().isBinary() || !psiFile.isValid()) {
          return;
        }
        final Module module = ModuleUtil.findModuleForPsiElement(psiFile);
        if (module != null) {
          VirtualFile realFile = virtualFile(psiFile, false);
          if (realFile != null) {
            filesToAnalyze.put(module, pathOf(realFile));
          }
        }
      }
    });
    return filesToAnalyze;
  }


  @Nullable
  static VirtualFile virtualFile(PsiFile psiFile, boolean onTheFly) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null || !existsOnFilesystem(virtualFile)) {
      // File only in memory
      return null;
    }
    if (onTheFly) {
      // SonarLint need content of the file to be written on disk
      saveFile(virtualFile);
    }
    return virtualFile;
  }

  private static String pathOf(@NotNull final VirtualFile virtualFile) {
    return virtualFile.getPath();
  }

  private static boolean existsOnFilesystem(@NotNull VirtualFile virtualFile) {
    return LocalFileSystem.getInstance().exists(virtualFile);
  }

  private static void saveFile(@NotNull final VirtualFile virtualFile) {
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
      }
    }
  }

  ProblemDescriptor getProblemDescriptor(Issue issue, PsiFile psiFile, @NotNull InspectionManager manager, boolean isLocal) {
    Integer startLine = issue.getStartLine();
    if (startLine == null) {
      // No line, global issue
      return createProblemDescriptorFileLevel(issue, psiFile, manager, isLocal);
    }
    Integer startLineOffset = issue.getStartLineOffset();
    if (startLineOffset == null) {
      // No offset, either old SQ version or language plugin doesn't provide data. Fallback to text range
      return createProblemDescriptorPreciseTextRange(issue, psiFile, manager, isLocal, InspectionUtils.getLineRange(psiFile, startLine));
    }
    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document == null) {
      // I'm not sure when this can occurs. Create a global issue
      return createProblemDescriptorFileLevel(issue, psiFile, manager, isLocal);
    }
    return tryCreateProblemDescriptorPrecisePsiElement(issue, psiFile, manager, isLocal, startLine, startLineOffset, document);
  }

  @NotNull
  private ProblemDescriptor tryCreateProblemDescriptorPrecisePsiElement(Issue issue, PsiFile psiFile, @NotNull InspectionManager manager, boolean isLocal, Integer startLine, Integer startLineOffset, Document document) {
    Integer endLine = issue.getEndLine();
    Integer endLineOffset = issue.getEndLineOffset();
    int ijStartLine = startLine - 1;
    int ijEndLine = endLine - 1;
    PsiElement startElement;
    PsiElement endElement;
    int lineStartOffset = document.getLineStartOffset(ijStartLine);
    final int startOffset = lineStartOffset + startLineOffset;
    final int endOffset = (ijEndLine == ijStartLine) ? (lineStartOffset + endLineOffset) : (document.getLineStartOffset(ijEndLine) + endLineOffset);
    startElement = psiFile.getViewProvider().findElementAt(startOffset);
    if (startElement == null || startElement.getTextRange().getStartOffset() != startOffset) {
      // Start element is not exactly the good one, fallback on text range
      return createProblemDescriptorPreciseTextRange(issue, psiFile, manager, isLocal, new TextRange(startOffset, endOffset));
    }
    endElement = psiFile.getViewProvider().findElementAt(endOffset - 1);
    if (endElement == null || endElement.getTextRange().getEndOffset() != endOffset) {
      // End element is not exactly the good one, fallback on text range
      return createProblemDescriptorPreciseTextRange(issue, psiFile, manager, isLocal, new TextRange(startOffset, endOffset));
    }
    return manager.createProblemDescriptor(startElement, endElement,
        InspectionUtils.getProblemMessage(issue, isLocal),
        problemHighlightType(issue, isLocal),
        false);
  }

  private ProblemDescriptor createProblemDescriptorPreciseTextRange(Issue issue, PsiFile psiFile, @NotNull InspectionManager manager, boolean isLocal, TextRange rangeInElement) {
    return manager.createProblemDescriptor(psiFile,
        rangeInElement,
        InspectionUtils.getProblemMessage(issue, isLocal),
        problemHighlightType(issue, isLocal),
        false);
  }

  private ProblemDescriptor createProblemDescriptorFileLevel(Issue issue, PsiFile psiFile, @NotNull InspectionManager manager, boolean isLocal) {
    return manager.createProblemDescriptor(psiFile,
        InspectionUtils.getProblemMessage(issue, isLocal),
        new LocalQuickFix[0],
        problemHighlightType(issue, isLocal),
        false,
        false);
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
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new SonarLintLocalInspection(this);
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

}
