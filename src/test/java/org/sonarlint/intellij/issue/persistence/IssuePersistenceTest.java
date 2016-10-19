package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarlint.intellij.issue.LocalIssueTrackable;
import org.sonarlint.intellij.issue.tracking.Trackable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssuePersistenceTest {
  private Project project;
  private IssuePersistence persistence;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() {
    project = mock(Project.class);
    VirtualFile baseDir = mock(VirtualFile.class);

    when(project.getBaseDir()).thenReturn(baseDir);
    when(baseDir.getPath()).thenReturn(temp.getRoot().getAbsolutePath());
    when(baseDir.findFileByRelativePath(anyString())).thenReturn(baseDir);

    persistence = new IssuePersistence(project);
  }

  @Test
  public void should_store_read_empty() throws IOException {
    persistence.save("key", Collections.emptyList());
    assertThat(persistence.read("key")).isEmpty();
  }

  @Test
  public void should_clear() throws IOException {
    persistence.save("key", Collections.singleton(testTrackable));
    persistence.clear();
    assertThat(persistence.read("key")).isNull();
  }

  @Test
  public void should_not_read_if_not_stored() throws IOException {
    assertThat(persistence.read("key")).isNull();
  }

  @Test
  public void should_store_read() throws IOException {
    persistence.save("key", Collections.singleton(testTrackable));

    Collection<LocalIssueTrackable> issues = persistence.read("key");
    assertThat(issues).hasSize(1);

    LocalIssueTrackable issue = issues.iterator().next();
    assertThat(issue.getAssignee()).isEmpty();
    assertThat(issue.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issue.getMessage()).isEqualTo("msg");
    assertThat(issue.getLine()).isEqualTo(5);
    assertThat(issue.getTextRangeHash()).isNull();
    assertThat(issue.getLineHash()).isEqualTo(3);

  }

  private Trackable testTrackable = new Trackable() {
    @Override public Integer getLine() {
      return 5;
    }

    @Override public String getMessage() {
      return "msg";
    }

    @Override public Integer getTextRangeHash() {
      return 4;
    }

    @Override public Integer getLineHash() {
      return 3;
    }

    @Override public String getRuleKey() {
      return "ruleKey";
    }

    @Override public String getServerIssueKey() {
      return null;
    }

    @Override public Long getCreationDate() {
      return null;
    }

    @Override public boolean isResolved() {
      return false;
    }

    @Override public String getAssignee() {
      return null;
    }
  };
}
