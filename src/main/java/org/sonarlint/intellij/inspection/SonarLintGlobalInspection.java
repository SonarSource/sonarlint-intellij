/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;

public class SonarLintGlobalInspection extends GlobalInspectionTool {

  @Nullable
  private static String realFilePath(PsiFile psiFile) {
    if (!existsOnFilesystem(psiFile)
      || documentIsModifiedAndUnsaved(psiFile.getVirtualFile())) {
      return null;
    } else {
      return pathOf(psiFile);
    }
  }

  private static String pathOf(@NotNull final PsiFile file) {
    if (file.getVirtualFile() != null) {
      return file.getVirtualFile().getPath();
    }
    throw new IllegalStateException("PSIFile does not have associated virtual file: " + file);
  }

  private static boolean existsOnFilesystem(@NotNull final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null && LocalFileSystem.getInstance().exists(virtualFile);
  }

  private static boolean documentIsModifiedAndUnsaved(@javax.annotation.Nullable final VirtualFile virtualFile) {
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
          String realFile = realFilePath(psiFile);
          if (realFile != null) {
            filesToAnalyze.put(module, realFile);
          }
        }
      }
    });
    return filesToAnalyze;
  }

  ProblemDescriptor getProblemDescriptor(Issue issue, PsiFile psiFile, @NotNull InspectionManager manager, boolean isLocal) {
    if (issue.getLine() != null) {
      return manager.createProblemDescriptor(psiFile,
        InspectionUtils.getLineRange(psiFile, issue.getLine()),
        InspectionUtils.getProblemMessage(issue, isLocal),
        problemHighlightType(issue, isLocal),
        false);
    } else {
      return manager.createProblemDescriptor(psiFile,
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
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new SonarLintLocalInspection(this);
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

}
