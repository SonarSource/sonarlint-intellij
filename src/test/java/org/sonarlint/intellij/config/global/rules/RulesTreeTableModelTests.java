/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import java.util.HashMap;
import java.util.List;
import javax.swing.Icon;
import javax.swing.table.AbstractTableModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulesTreeTableModelTests {
  private final RulesTreeNode.Root root = new RulesTreeNode.Root();
  private final RulesTreeNode.LanguageNode lang = new RulesTreeNode.LanguageNode("lang");
  private final RuleDefinitionDto ruleDetails = mock(RuleDefinitionDto.class);
  private final AbstractTableModel tableModel = mock(AbstractTableModel.class);
  private final RulesTreeTableModel model = spy(new RulesTreeTableModel(root));
  private RulesTreeNode.Rule rule;

  @BeforeEach
  void setUp() {
    when(ruleDetails.getCleanCodeAttribute()).thenReturn(CleanCodeAttribute.CONVENTIONAL);
    when(ruleDetails.getSoftwareImpacts()).thenReturn(List.of(new ImpactDto(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.MEDIUM)));
    when(ruleDetails.getKey()).thenReturn("key");
    when(ruleDetails.isActiveByDefault()).thenReturn(false);
    root.add(lang);
    rule = new RulesTreeNode.Rule(ruleDetails, true, new HashMap<>());
    lang.add(rule);

    // just to not give NPE when firing changes
    var treeTableTree = mock(TreeTableTree.class);
    var treeTable = mock(TreeTable.class);
    when(treeTableTree.getTreeTable()).thenReturn(treeTable);
    when(treeTable.getModel()).thenReturn(tableModel);
    doNothing().when(model).nodeChanged(any());
    model.setTree(treeTableTree);
  }

  @Test
  void getters() {
    assertThat(model.getColumnCount()).isEqualTo(3);
    assertThat(model.getRoot()).isEqualTo(root);

    assertThat(model.getColumnClass(0)).isEqualTo(TreeTableModel.class);
    assertThat(model.getColumnClass(1)).isEqualTo(Icon.class);
    assertThat(model.getColumnClass(2)).isEqualTo(Boolean.class);

    assertThat(model.getValueAt(rule, 0)).isNull();
    assertThat(model.getValueAt(rule, 1)).isNotNull();
    assertThat(model.getValueAt(rule, 2)).isEqualTo(true);

    assertThat(model.getValueAt(lang, 0)).isNull();
    assertThat(model.getValueAt(lang, 1)).isNull();
    assertThat(lang.isNonDefault()).isFalse();

    // not set yet
    assertThat(model.getValueAt(lang, 2)).isNull();

    assertThat(model.getColumnName(0)).isNull();
  }

  @Test
  void throw_exception_if_get_value_from_invalid_column() {
    var throwable = catchThrowable(() -> model.getValueAt(rule, 3));

    assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throw_exception_if_get_class_from_invalid_column() {
    var throwable = catchThrowable(() -> model.getColumnClass(4));

    assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void can_only_edit_activation() {
    assertThat(model.isCellEditable(rule, 0)).isFalse();
    assertThat(model.isCellEditable(rule, 1)).isFalse();
    assertThat(model.isCellEditable(rule, 2)).isTrue();

    assertThat(model.isCellEditable(lang, 0)).isFalse();
    assertThat(model.isCellEditable(lang, 1)).isFalse();
    assertThat(model.isCellEditable(lang, 2)).isTrue();
  }

  @Test
  void should_set_value_in_rule() {
    model.setValueAt(false, rule, 2);
    assertThat(rule.isActivated()).isFalse();
    assertThat(lang.isActivated()).isFalse();
  }

  @Test
  void should_set_value_in_lang() {
    model.setValueAt(false, lang, 2);
    assertThat(rule.isActivated()).isFalse();
    assertThat(lang.isActivated()).isFalse();
  }

  @Test
  void should_calculate_language_activation_and_changed() {
    model.refreshLanguageActivation(lang);
    assertThat(lang.isActivated()).isTrue();
    assertThat(lang.isNonDefault()).isTrue();
    assertThat(model.getValueAt(lang, 2)).isEqualTo(true);
  }

  @Test
  void swap_value_lang() {
    model.refreshLanguageActivation(lang);

    model.swapAndRefresh(lang);
    assertActivationIsFalse();
  }

  @Test
  void swap_value_rule() {
    model.swapAndRefresh(rule);
    assertActivationIsFalse();
  }

  private void assertActivationIsFalse() {
    assertThat(rule.isActivated()).isFalse();
    assertThat(rule.isNonDefault()).isFalse();

    assertThat(lang.isActivated()).isFalse();
    assertThat(lang.isNonDefault()).isFalse();

    verify(model).nodeChanged(any(RulesTreeNode.class));
  }
}
