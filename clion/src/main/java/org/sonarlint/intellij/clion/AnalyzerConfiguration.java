package org.sonarlint.intellij.clion;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeProfileInfo;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.lang.OCLanguageUtils;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.psi.OCParsedLanguageAndConfiguration;
import com.jetbrains.cidr.lang.psi.OCPsiFile;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import javax.annotation.Nullable;

public class AnalyzerConfiguration {
  private final Project project;
  private final SonarLintConsole console;
  private final CMakeWorkspace cMakeWorkspace;

  public AnalyzerConfiguration(@NotNull Project project) {
    this.project = project;
    console = SonarLintConsole.get(project);
    cMakeWorkspace = CMakeWorkspace.getInstance(project);
  }

  /**
   * Inspired from ShowCompilerInfoForFile and ClangTidyAnnotator
   */
  @Nullable
  public Request getCompilerSettings(VirtualFile file) {
    OCPsiFile psiFile = ApplicationManager.getApplication().<OCPsiFile>runReadAction(() -> OCLanguageUtils.asOCPsiFile(project, file));
    if (psiFile == null || !psiFile.isInProjectSources()) {
      console.debug("skip " + file + ": " + psiFile);
      return null;
    }
    OCResolveConfiguration configuration = null;
    OCParsedLanguageAndConfiguration languageAndConfiguration = psiFile.getParsedLanguageAndConfiguration();
    if (languageAndConfiguration != null) {
      configuration = languageAndConfiguration.getConfiguration();
    }
    if (configuration == null) {
      configuration = ApplicationManager.getApplication().<OCResolveConfiguration>runReadAction(
        () -> OCInclusionContextUtil.getResolveRootAndActiveConfiguration(file, project).getConfiguration());
    }
    if (configuration == null) {
      console.debug("configuration not found for: " + file);
      return null;
    }
    if (usingRemoteToolchain(configuration)) {
      console.debug("remote toolchain detected, skip: " + file);
      return null;
    }
    OCCompilerSettings compilerSettings = configuration.getCompilerSettings(psiFile.getKind(), file);
    OCCompilerKind compilerKind = compilerSettings.getCompilerKind();
    String compiler = ((compilerKind instanceof GCCCompilerKind) || (compilerKind instanceof ClangCompilerKind)) ? "clang" : "unknown";
    return new Request(file, compilerSettings, compiler, psiFile.isHeader());
  }

  private boolean usingRemoteToolchain(OCResolveConfiguration configuration) {
    CMakeConfiguration cMakeConfiguration = cMakeWorkspace.getCMakeConfigurationFor(configuration);
    if (cMakeConfiguration == null) {
      // remote toolchains are supported only for CMake projects
      return false;
    }
    CMakeProfileInfo profileInfo;
    try {
      profileInfo = cMakeWorkspace.getProfileInfoFor(cMakeConfiguration);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
    CPPEnvironment environment = profileInfo.getEnvironment();
    return environment != null && environment.getToolSet().isRemote();
  }

  public static class Request {
    final VirtualFile virtualFile;
    final OCCompilerSettings compilerSettings;
    final String compiler;
    final boolean isHeaderFile;

    public Request(VirtualFile virtualFile, OCCompilerSettings compilerSettings, String compiler, boolean isHeaderFile) {
      this.virtualFile = virtualFile;
      this.compilerSettings = compilerSettings;
      this.compiler = compiler;
      this.isHeaderFile = isHeaderFile;
    }
  }
}
