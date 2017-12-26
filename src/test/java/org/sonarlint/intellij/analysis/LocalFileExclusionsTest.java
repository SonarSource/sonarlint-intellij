package org.sonarlint.intellij.analysis;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFileExclusionsTest extends SonarTest {
  private SonarLintGlobalSettings globalSettings = new SonarLintGlobalSettings();
  private SonarLintProjectSettings projectSettings = new SonarLintProjectSettings();

  private LocalFileExclusions exclusions;

  private ModuleRootManager moduleRootManager = mock(ModuleRootManager.class);
  private FileType type = mock(FileType.class);
  private VirtualFile testFile = mock(VirtualFile.class);
  private Supplier<Boolean> powerModeCheck = mock(Supplier.class);

  @Before
  public void prepare() {
    exclusions = new LocalFileExclusions(project, globalSettings, projectSettings, powerModeCheck);
    when(powerModeCheck.get()).thenReturn(false);
    when(type.isBinary()).thenReturn(false);
    when(testFile.getParent()).thenReturn(mock(VirtualFile.class));
    when(testFile.getFileType()).thenReturn(type);
    when(testFile.isInLocalFileSystem()).thenReturn(true);
    when(testFile.isValid()).thenReturn(true);

    super.register(module, ModuleRootManager.class, moduleRootManager);
  }

  @Test
  public void should_not_analyze_automatically_if_module_is_null() {
    LocalFileExclusions.Result result = exclusions.checkExclusionAutomaticAnalysis(testFile, null);
    assertThat(result.isExcluded()).isTrue();
  }

  @Test
  public void should_analyze_file() {
    assertThat(exclusions.canAnalyze(testFile, module)).isTrue();
  }

  @Test
  public void should_not_analyze_if_file_is_binary() {
    when(type.isBinary()).thenReturn(true);
    assertThat(exclusions.canAnalyze(testFile, module)).isFalse();
  }

  @Test
  public void should_not_analyze_if_module_is_null() {
    assertThat(exclusions.canAnalyze(testFile, null)).isFalse();
  }

  @Test
  public void should_not_analyze_if_project_is_disposed() {
    when(project.isDisposed()).thenReturn(true);
    Module module = mock(Module.class);

    when(project.isDisposed()).thenReturn(true);
    when(module.getProject()).thenReturn(project);

    assertThat(exclusions.canAnalyze(testFile, module)).isFalse();
  }

  @Test
  public void should_not_analyze_if_file_is_invalid() {
    VirtualFile f = mock(VirtualFile.class);
    when(f.isValid()).thenReturn(false);

    assertThat(exclusions.canAnalyze(f, module)).isFalse();
  }
}
