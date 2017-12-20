package org.sonarlint.intellij.config.project;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExclusionItemTest {
  @Test
  public void should_parse() {
    ExclusionItem item = ExclusionItem.parse("File:src/main/java/File.java");
    assertThat(item.item()).isEqualTo("src/main/java/File.java");
    assertThat(item.type()).isEqualTo(ExclusionItem.Type.FILE);
  }

  @Test
  public void return_null_if_fail_to_parse() {
    ExclusionItem item = ExclusionItem.parse("Unknown:src/main/java/File.java");
    assertThat(item).isNull();
  }

  @Test
  public void return_null_if_fail_to_parse2() {
    ExclusionItem item = ExclusionItem.parse("Unknown:");
    assertThat(item).isNull();
  }

  @Test
  public void use_constructor() {
    ExclusionItem item = new ExclusionItem(ExclusionItem.Type.DIRECTORY, "dir");
    assertThat(item.item()).isEqualTo("dir");
    assertThat(item.type()).isEqualTo(ExclusionItem.Type.DIRECTORY);
  }

  @Test
  public void string() {
    ExclusionItem item = new ExclusionItem(ExclusionItem.Type.DIRECTORY, "dir");
    assertThat(item.toStringWithType()).isEqualTo("Directory:dir");
  }
}
