package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceRunConfigurationListener;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

public class CFamilyAnalysisConfigurator implements AnalysisConfigurator {

  @Override
  public Map<String, String> configure(Module module, Collection<VirtualFile> filesToAnalyze) {
    OCResolveConfiguration configuration = OCWorkspaceRunConfigurationListener.getSelectedResolveConfiguration(module.getProject());
    if (configuration != null) {
      Map<String, String> result = new HashMap<>();
      String buildWrapperJson = CLionConfiguration.BuildWrapperJsonFactory.create(module.getProject(), configuration, filesToAnalyze);

      result.put("sonar.cfamily.build-wrapper-content", buildWrapperJson);
      return result;
    }
    return Collections.emptyMap();
  }
}
