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
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RulesTreeNodeTest {
  @Test
  public void getters_rule() {
    RuleDetails details = mock(RuleDetails.class);
    when(details.getName()).thenReturn("name");
    when(details.getKey()).thenReturn("key");
    when(details.getHtmlDescription()).thenReturn("html");
    when(details.isActiveByDefault()).thenReturn(true);
    when(details.getSeverity()).thenReturn("severity");
    when(details.getType()).thenReturn("type");
    when(details.getLanguageKey()).thenReturn("lang");

    RulesTreeNode.Rule node = new RulesTreeNode.Rule(details, false);
    assertThat(node.getKey()).isEqualTo("key");
    assertThat(node.getName()).isEqualTo("name");
    assertThat(node.toString()).isEqualTo("name");
    assertThat(node.getHtmlDescription()).isEqualTo("html");
    assertThat(node.getDefaultActivation()).isTrue();
    assertThat(node.isChanged()).isTrue();
    assertThat(node.severity()).isEqualTo("severity");
    assertThat(node.type()).isEqualTo("type");
    assertThat(node.languageKey()).isEqualTo("lang");
  }

  @Test
  public void getters_root() {
    RulesTreeNode.Root root = new RulesTreeNode.Root();
    assertThat(root.toString()).isEqualTo("root");
    assertThat(root.isChanged()).isFalse();
  }

  @Test
  public void getters_language() {
    RulesTreeNode.Language node = new RulesTreeNode.Language("lang");
    node.setIsChanged(true);
    node.setIsActivated(true);

    assertThat(node.toString()).isEqualTo("lang");
    assertThat(node.isChanged()).isTrue();
    assertThat(node.isActivated()).isTrue();
  }

  @Test
  public void create_iterable_children() {
    RulesTreeNode.Language parent = new RulesTreeNode.Language("lang");
    RulesTreeNode.Rule n1 = new RulesTreeNode.Rule(mock(RuleDetails.class), true);
    RulesTreeNode.Rule n2 = new RulesTreeNode.Rule(mock(RuleDetails.class), true);
    RulesTreeNode.Rule n3 = new RulesTreeNode.Rule(mock(RuleDetails.class), true);

    parent.add(n1);
    parent.add(n2);
    parent.add(n3);

    assertThat(parent.childrenIterable().iterator()).containsExactly(n1, n2, n3);

  }
}
