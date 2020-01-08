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
package org.sonarlint.intellij.editor;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarlint.intellij.SonarLintTestUtils.createIssue;

public class AccumulatorIssueListenerTest {
  private AccumulatorIssueListener listener;

  @Before
  public void setUp() {
    listener = new AccumulatorIssueListener();
  }

  @Test
  public void test_issue_listener() {
    for (int i = 0; i < 5; i++) {
      listener.handle(createIssue(i));
    }

    assertThat(listener.getIssues()).hasSize(5);
    assertThat(listener.getIssues().get(0).getRuleKey()).isEqualTo("0");
  }
}
