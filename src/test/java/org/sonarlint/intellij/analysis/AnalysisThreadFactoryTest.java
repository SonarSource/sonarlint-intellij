package org.sonarlint.intellij.analysis;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnalysisThreadFactoryTest {
  private AnalysisThreadFactory factory;

  @Before
  public void setUp() {
    factory = new AnalysisThreadFactory();
  }

  @Test
  public void check_props() {
    Thread thread = factory.newThread(() -> {});

    assertThat(thread.isDaemon()).isTrue();
    assertThat(thread.getName()).startsWith("SonarLintAnalysis");
    assertThat(thread.getPriority()).isEqualTo(Thread.MIN_PRIORITY);
  }
}
