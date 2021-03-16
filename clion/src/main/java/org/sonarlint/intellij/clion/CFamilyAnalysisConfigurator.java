package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceRunConfigurationListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public class CFamilyAnalysisConfigurator implements AnalysisConfigurator {

  @Override
  public Map<String, String> configure(Module module, Collection<VirtualFile> filesToAnalyze) {
    OCResolveConfiguration configuration = OCWorkspaceRunConfigurationListener.getSelectedResolveConfiguration(module.getProject());
    if (configuration != null) {
      Map<String, String> result = new HashMap<>();
      CLionConfiguration.debugAllFilesConfiguration(SonarLintConsole.get(module.getProject()), module, filesToAnalyze, configuration);
      String buildWrapperJson = CLionConfiguration.BuildWrapperJsonFactory.create(module.getProject(), configuration, filesToAnalyze);

      File buildWrapperDir;
      try {
        buildWrapperDir = FileUtil.createTempDirectory("cfamily", null);
        Files.write(
          new File(buildWrapperDir, "build-wrapper-dump.json").toPath(),
          buildWrapperJson.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      result.put("sonar.cfamily.build-wrapper-output", buildWrapperDir.getAbsolutePath());
      return result;
    }
    return Collections.emptyMap();
  }
}
