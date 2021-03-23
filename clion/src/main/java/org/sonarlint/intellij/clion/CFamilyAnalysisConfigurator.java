package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Objects;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

public class CFamilyAnalysisConfigurator implements AnalysisConfigurator {

  @Override
  public AnalysisConfiguration configure(Module module, Collection<VirtualFile> filesToAnalyze) {
    AnalysisConfiguration result = new AnalysisConfiguration();
    AnalyzerConfiguration analyzerConfiguration = new AnalyzerConfiguration(module.getProject());
    BuildWrapperJsonGenerator buildWrapperJsonGenerator = new BuildWrapperJsonGenerator();
    filesToAnalyze.stream()
      .map(analyzerConfiguration::getCompilerSettings)
      .filter(Objects::nonNull)
      .filter(request -> "clang".equals(request.compiler))
      .forEach(buildWrapperJsonGenerator::addRequest);
    result.extraProperties.put("sonar.cfamily.build-wrapper-content", buildWrapperJsonGenerator.build());
    return result;
  }

}
