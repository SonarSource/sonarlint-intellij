package org.sonar.ide.intellij.inspection;

import org.junit.Test;
import org.sonar.ide.intellij.model.ISonarIssue;

import static org.mockito.Mockito.*;
import static org.fest.assertions.Assertions.*;

public class InspectionUtilsTest {

  @Test
  public void testProblemMessage() {
    ISonarIssue issue = mock(ISonarIssue.class);
    when(issue.isNew()).thenReturn(false);
    when(issue.message()).thenReturn("My Issue");

    assertThat(InspectionUtils.getProblemMessage(issue)).isEqualTo("My Issue");

    when(issue.isNew()).thenReturn(true);
    assertThat(InspectionUtils.getProblemMessage(issue)).isEqualTo("NEW: My Issue");

  }
}
