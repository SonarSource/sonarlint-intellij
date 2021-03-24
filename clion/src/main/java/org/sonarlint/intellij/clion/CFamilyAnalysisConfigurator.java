package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

import java.util.Collection;
import java.util.Objects;

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
      .forEach(configuration -> {
        buildWrapperJsonGenerator.addRequest(configuration);
        if (configuration.sonarLanguage != null) {
          result.forcedLanguages.put(configuration.virtualFile, configuration.sonarLanguage);
        }
      });
    result.extraProperties.put("sonar.cfamily.build-wrapper-content", buildWrapperJsonGenerator.build());
    return result;
  }

}
