package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceRunConfigurationListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public class CLionInitializer {

  public static void initCLion(Module module, Project project, Collection<VirtualFile> filesToAnalyze, Map<String, String> pluginProps) {
    OCResolveConfiguration configuration = OCWorkspaceRunConfigurationListener.getSelectedResolveConfiguration(module.getProject());
    if (configuration != null) {
      CLionConfiguration.debugAllFilesConfiguration(project.getService(SonarLintConsole.class), module, filesToAnalyze, configuration);
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
      pluginProps.put("sonar.cfamily.build-wrapper-output", buildWrapperDir.getAbsolutePath());
    }
  }

}
