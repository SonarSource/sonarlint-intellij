/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.commons.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RulesTreeNodeTest {
  @Test
  public void getters_rule() {
    var details = mock(StandaloneRuleDetails.class);
    when(details.getName()).thenReturn("name");
    when(details.getKey()).thenReturn("key");
    when(details.getHtmlDescription()).thenReturn("html");
    when(details.isActiveByDefault()).thenReturn(true);
    when(details.getSeverity()).thenReturn("severity");
    when(details.getType()).thenReturn("type");
    when(details.getLanguage()).thenReturn(Language.JAVA);

    var node = new RulesTreeNode.Rule(details, false, new HashMap<>());
    assertThat(node.getKey()).isEqualTo("key");
    assertThat(node.getName()).isEqualTo("name");
    assertThat(node).hasToString("name");
    assertThat(node.getHtmlDescription()).isEqualTo("html");
    assertThat(node.getDefaultActivation()).isTrue();
    assertThat(node.isNonDefault()).isTrue();
    assertThat(node.severity()).isEqualTo("severity");
    assertThat(node.type()).isEqualTo("type");
    assertThat(node.language()).isEqualTo(Language.JAVA);
  }

  @Test
  public void getters_root() {
    var root = new RulesTreeNode.Root();
    assertThat(root).hasToString("root");
    assertThat(root.isNonDefault()).isFalse();
  }

  @Test
  public void getters_language() {
    var node = new RulesTreeNode.Language("lang");
    node.setIsNonDefault(true);
    node.setIsActivated(true);

    assertThat(node).hasToString("lang");
    assertThat(node.isNonDefault()).isTrue();
    assertThat(node.isActivated()).isTrue();
  }

  @Test
  public void create_iterable_children() {
    var parent = new RulesTreeNode.Language("lang");
    RulesTreeNode.Rule n1 = new RulesTreeNode.Rule(mockRuleDetails("r1"), true, new HashMap<>());
    RulesTreeNode.Rule n2 = new RulesTreeNode.Rule(mockRuleDetails("r2"), true, new HashMap<>());
    RulesTreeNode.Rule n3 = new RulesTreeNode.Rule(mockRuleDetails("r3"), true, new HashMap<>());

    parent.add(n1);
    parent.add(n2);
    parent.add(n3);

    assertThat(parent.childrenIterable()).containsExactly(n1, n2, n3);

  }

  @NotNull
  private StandaloneRuleDetails mockRuleDetails(String key) {
    final var r1 = mock(StandaloneRuleDetails.class);
    when(r1.getKey()).thenReturn(key);
    return r1;
  }
}
