/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LocalFindingTrackableTests {
  @Test
  void testWrapping() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .setServerFindingKey("key")
      .setLine(10)
      .setChecksum(20)
      .setMessage("msg")
      .setResolved(true)
      .setIntroductionDate(1000L)
      .setRuleKey("ruleKey")
      .build();

    var trackable = new LocalFindingTrackable(finding);
    assertThat(trackable.getMessage()).isEqualTo("msg");
    assertThat(trackable.getServerFindingKey()).isEqualTo("key");
    assertThat(trackable.getLine()).isEqualTo(10);
    assertThat(trackable.getIntroductionDate()).isEqualTo(1000L);
    assertThat(trackable.isResolved()).isTrue();
    assertThat(trackable.getLineHash()).isEqualTo(20);
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getRuleKey()).isEqualTo("ruleKey");
  }

  @Test
  void testNulls() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .build();

    var trackable = new LocalFindingTrackable(finding);
    assertThat(trackable.getServerFindingKey()).isNull();
    assertThat(trackable.getLine()).isNull();
    assertThat(trackable.getIntroductionDate()).isNull();
  }

  @Test
  void severityNotStored() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .build();
    var trackable = new LocalFindingTrackable(finding);

    var throwable = catchThrowable(() -> trackable.getUserSeverity());

    assertThat(throwable).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void typeNotStored() {
    var finding = Sonarlint.Findings.Finding.newBuilder()
      .build();
    var trackable = new LocalFindingTrackable(finding);

    var throwable = catchThrowable(() -> trackable.getType());

    assertThat(throwable).isInstanceOf(UnsupportedOperationException.class);
  }
}
