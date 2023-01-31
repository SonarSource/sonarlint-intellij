/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.finding.tracking;

import org.junit.Test;
import org.sonarlint.intellij.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalFindingTrackableTest {
  @Test
  public void testWrapping() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .setServerFindingKey("key")
      .setLine(10)
      .setChecksum(20)
      .setMessage("msg")
      .setResolved(true)
      .setCreationDate(1000L)
      .setRuleKey("ruleKey")
      .build();

    var trackable = new LocalFindingTrackable(finding);
    assertThat(trackable.getMessage()).isEqualTo("msg");
    assertThat(trackable.getServerFindingKey()).isEqualTo("key");
    assertThat(trackable.getLine()).isEqualTo(10);
    assertThat(trackable.getCreationDate()).isEqualTo(1000L);
    assertThat(trackable.isResolved()).isTrue();
    assertThat(trackable.getLineHash()).isEqualTo(20);
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getRuleKey()).isEqualTo("ruleKey");
  }

  @Test
  public void testNulls() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .build();

    var trackable = new LocalFindingTrackable(finding);
    assertThat(trackable.getServerFindingKey()).isNull();
    assertThat(trackable.getLine()).isNull();
    assertThat(trackable.getCreationDate()).isNull();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void severityNotStored() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .build();

    var trackable = new LocalFindingTrackable(finding);
    trackable.getUserSeverity();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void typeNotStored() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .build();

    var trackable = new LocalFindingTrackable(finding);
    trackable.getType();
  }
}
