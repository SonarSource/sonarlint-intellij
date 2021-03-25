package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

import java.util.Collection;

public class CFamilyAnalysisConfigurator implements AnalysisConfigurator {

  @Override
  public AnalysisConfiguration configure(Module module, Collection<VirtualFile> filesToAnalyze) {
    AnalysisConfiguration result = new AnalysisConfiguration();
    AnalyzerConfiguration analyzerConfiguration = new AnalyzerConfiguration(module.getProject());
    BuildWrapperJsonGenerator buildWrapperJsonGenerator = new BuildWrapperJsonGenerator();
    filesToAnalyze.stream()
      .map(analyzerConfiguration::getConfiguration)
      .filter(AnalyzerConfiguration.ConfigurationResult::hasConfiguration)
      .map(AnalyzerConfiguration.ConfigurationResult::getConfiguration)
      .forEach(configuration -> {
        buildWrapperJsonGenerator.add(configuration);
        if (configuration.sonarLanguage != null) {
          result.forcedLanguages.put(configuration.virtualFile, configuration.sonarLanguage);
        }
      });
    result.extraProperties.put("sonar.cfamily.build-wrapper-content", buildWrapperJsonGenerator.build());
    return result;
  }

}
