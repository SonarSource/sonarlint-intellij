package org.sonarlint.intellij.analysis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.util.SonarLintUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExclusionsTest {
  /*private Exclusions exclusions = new Exclusions()
  @Test
  public void testShouldAnalyze() {
    assertThat(SonarLintUtils.shouldAnalyze(testFile, module)).isTrue();

    assertThat(SonarLintUtils.shouldAnalyze(testFile, null)).isFalse();

    when(testFile.getFileType()).thenReturn(binary);
    assertThat(SonarLintUtils.shouldAnalyze(testFile, module)).isFalse();
  }

  @Test
  public void testShouldAnalyzeDisposed() {
    Project disposed = mock(Project.class);
    Module module = mock(Module.class);

    when(disposed.isDisposed()).thenReturn(true);
    when(module.getProject()).thenReturn(disposed);

    assertThat(SonarLintUtils.shouldAnalyze(testFile, module)).isFalse();
  }

  @Test
  public void testShouldAnalyzeInvalid() {
    VirtualFile f = mock(VirtualFile.class);
    when(f.isValid()).thenReturn(false);

    assertThat(SonarLintUtils.shouldAnalyze(f, module)).isFalse();
  }*/
}
