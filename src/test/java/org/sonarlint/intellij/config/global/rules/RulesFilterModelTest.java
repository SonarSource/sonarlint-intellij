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
package org.sonarlint.intellij.config.global.rules;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RulesFilterModelTest {
  private Runnable changed = mock(Runnable.class);
  private RulesFilterModel model = new RulesFilterModel(changed);

  @Test
  public void testCallback() {
    model.setShowOnlyChanged(true);
    model.setShowOnlyDisabled(true);
    model.setShowOnlyEnabled(true);
    model.setText("asd");

    verify(changed, times(4)).run();
  }

  @Test
  public void testGetters() {
    model.setShowOnlyDisabled(true);
    assertThat(model.isShowOnlyDisabled()).isTrue();
    assertThat(model.isShowOnlyChanged()).isFalse();
    assertThat(model.isShowOnlyEnabled()).isFalse();

    model.setShowOnlyChanged(true);
    assertThat(model.isShowOnlyChanged()).isTrue();
    assertThat(model.isShowOnlyEnabled()).isFalse();
    assertThat(model.isShowOnlyDisabled()).isFalse();

    model.setShowOnlyEnabled(true);
    assertThat(model.isShowOnlyEnabled()).isTrue();
    assertThat(model.isShowOnlyDisabled()).isFalse();
    assertThat(model.isShowOnlyChanged()).isFalse();

    model.setText("asd");
    assertThat(model.getText()).isEqualTo("asd");
  }

  @Test
  public void should_be_empty_if_nothing_is_set() {
    assertThat(model.isEmpty()).isTrue();
    model.setShowOnlyEnabled(true);
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  public void default_should_be_empty() {
    assertEmpty();
  }

  @Test
  public void should_reset() {
    model.setShowOnlyChanged(true);
    model.setShowOnlyDisabled(true);
    model.setShowOnlyEnabled(true);
    model.setText("asd");

    model.reset(false);

    verify(changed, times(4)).run();
    assertThat(model.isShowOnlyEnabled()).isFalse();
    assertThat(model.isShowOnlyDisabled()).isFalse();
    assertThat(model.isShowOnlyChanged()).isFalse();
    assertThat(model.getText()).isNull();
  }

  @Test
  public void should_apply_filter() {
    RulesTreeNode.Rule rule = mock(RulesTreeNode.Rule.class);
    when(rule.getName()).thenReturn("my rule");
    assertThat(model.filter(rule)).isTrue();

    model.setText("my filter");

    assertThat(model.filter(rule)).isFalse();

    when(rule.getName()).thenReturn("my filter");
    assertThat(model.filter(rule)).isTrue();

    when(rule.getName()).thenReturn("some text my filter and more text");
    assertThat(model.filter(rule)).isTrue();

    model.setShowOnlyEnabled(true);
    assertThat(model.filter(rule)).isFalse();

    when(rule.isActivated()).thenReturn(true);
    assertThat(model.filter(rule)).isTrue();
  }

  private void assertEmpty() {
    assertThat(model.isShowOnlyEnabled()).isFalse();
    assertThat(model.isShowOnlyDisabled()).isFalse();
    assertThat(model.isShowOnlyChanged()).isFalse();
    assertThat(model.getText()).isNull();
    assertThat(model.isEmpty()).isTrue();
  }
}
