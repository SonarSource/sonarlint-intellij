package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class CFamilyAnalysisConfigurator implements AnalysisConfigurator {

  @Override
  public Map<String, String> configure(Module module, Collection<VirtualFile> filesToAnalyze) {
    AnalyzerConfiguration analyzerConfiguration = new AnalyzerConfiguration(module.getProject());
    BuildWrapperJsonGenerator buildWrapperJsonGenerator = new BuildWrapperJsonGenerator();
    filesToAnalyze.stream()
      .map(analyzerConfiguration::getCompilerSettings)
      .filter(Objects::nonNull)
      .filter(request -> "clang".equals(request.compiler))
      .forEach(buildWrapperJsonGenerator::addRequest);
    return Collections.singletonMap("sonar.cfamily.build-wrapper-content", buildWrapperJsonGenerator.build());
  }

}
