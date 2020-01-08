/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.config.project;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExclusionItemTest {
  @Test
  public void should_parse_file() {
    ExclusionItem item = ExclusionItem.parse("FILE:src/main/java/File.java");
    assertThat(item.item()).isEqualTo("src/main/java/File.java");
    assertThat(item.type()).isEqualTo(ExclusionItem.Type.FILE);
  }

  @Test
  public void should_parse_dir() {
    ExclusionItem item = ExclusionItem.parse("DIRECTORY:src/main/java");
    assertThat(item.item()).isEqualTo("src/main/java");
    assertThat(item.type()).isEqualTo(ExclusionItem.Type.DIRECTORY);
  }

  @Test
  public void should_parse_regex() {
    ExclusionItem item = ExclusionItem.parse("GLOB:*.java");
    assertThat(item.item()).isEqualTo("*.java");
    assertThat(item.type()).isEqualTo(ExclusionItem.Type.GLOB);
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
  public void return_null_if_fail_to_parse3() {
    ExclusionItem item = ExclusionItem.parse("Unknown");
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
    assertThat(item.toStringWithType()).isEqualTo("DIRECTORY:dir");
  }
}
