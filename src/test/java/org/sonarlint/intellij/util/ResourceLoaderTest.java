/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.util;

import java.io.IOException;
import javax.swing.Icon;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ResourceLoaderTest {
  @Test
  public void severityIcons() throws IOException {
    String[] severities = {"MAJOR", "MINOR", "INFO", "CRITICAL", "BLOCKER"};

    for (String s : severities) {
      assertThat(ResourceLoader.getSeverityIcon(s)).isNotNull();
    }
  }

  @Test
  public void loadIcon() throws IOException {
    Icon icon = ResourceLoader.getIcon("clean.png");
    assertThat(icon).isNotNull();

    // second time from cache
    assertThat(ResourceLoader.getIcon("clean.png")).isEqualTo(icon);
  }

  @Test
  public void cantFind() {
    try {
      ResourceLoader.getIcon("doesnt-exist.png");
      fail("expected exception");
    } catch (IOException e) {
      assertThat(e.getMessage()).contains("Couldn't find resource");
    }
  }
}
