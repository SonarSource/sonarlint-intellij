package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.common.analysis.FileExclusionContributor;

public class CFamilyFileExclusionContributor implements FileExclusionContributor {

  @Override
  public ExcludeResult shouldExclude(Module module, VirtualFile fileToAnalyze) {
    AnalyzerConfiguration.ConfigurationResult configurationResult = new AnalyzerConfiguration(module.getProject()).getConfiguration(fileToAnalyze);
    if (configurationResult.hasConfiguration()) {
      return ExcludeResult.notExcluded();
    }
    return ExcludeResult.excluded(configurationResult.getSkipReason());
  }
}
