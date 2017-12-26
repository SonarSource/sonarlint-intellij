package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExcludeActionTest extends SonarTest {
  private VirtualFile file1 = mock(VirtualFile.class);
  private SonarLintProjectSettings settings = new SonarLintProjectSettings();

  private ExcludeAction action = new ExcludeAction();

  @Before
  public void setup() {
    super.register(project, SonarLintProjectSettings.class, settings);
  }

  @Test
  public void add_exclusion() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getProject()).thenReturn(project);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).containsOnly("File:file1");
  }

  @Test
  public void dont_add_exclusion_if_already_exists() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getProject()).thenReturn(project);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");
    settings.setFileExclusions(Collections.singletonList("File:file1"));

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).containsOnly("File:file1");
  }

  @Test
  public void do_nothing_if_disposed() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getProject()).thenReturn(project);
    when(project.isDisposed()).thenReturn(true);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).isEmpty();
  }

  @Test
  public void do_nothing_if_there_are_no_files() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getProject()).thenReturn(project);
    when(project.isDisposed()).thenReturn(true);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).isEmpty();
  }
}
