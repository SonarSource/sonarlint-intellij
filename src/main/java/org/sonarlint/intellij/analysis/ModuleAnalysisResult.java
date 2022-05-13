package org.sonarlint.intellij.analysis;

import com.intellij.openapi.module.Module;
import java.util.Collection;
import java.util.List;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

public class ModuleAnalysisResult {
  private final AnalysisResults result;
  private final Module module;
  private final List<ClientInputFile> inputFiles;

  public ModuleAnalysisResult(AnalysisResults result, Module module, List<ClientInputFile> inputFiles) {
    this.result = result;
    this.module = module;
    this.inputFiles = inputFiles;
  }

  public AnalysisResults getResult() {
    return result;
  }

  public Module getModule() {
    return module;
  }

  public List<ClientInputFile> getInputFiles() {
    return inputFiles;
  }

  public Collection<ClientInputFile> failedAnalysisFiles() {
    return result.failedAnalysisFiles();
  }
}
