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

import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.table.AbstractTableModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RulesTreeTableModelTest {
  private RulesTreeNode.Root root = new RulesTreeNode.Root();
  private RulesTreeNode.Language lang = new RulesTreeNode.Language("lang");
  private RuleDetails ruleDetails = mock(RuleDetails.class);
  private RulesTreeNode.Rule rule = new RulesTreeNode.Rule(ruleDetails, true);
  private AbstractTableModel tableModel = mock(AbstractTableModel.class);
  private RulesTreeTableModel model = new RulesTreeTableModel(root);

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    when(ruleDetails.getType()).thenReturn("BUG");
    when(ruleDetails.getSeverity()).thenReturn("MAJOR");
    when(ruleDetails.getKey()).thenReturn("key");
    when(ruleDetails.isActiveByDefault()).thenReturn(false);
    root.add(lang);
    lang.add(rule);

    // just to not give NPE when firing changes
    TreeTableTree treeTableTree = mock(TreeTableTree.class);
    TreeTable treeTable = mock(TreeTable.class);
    when(treeTableTree.getTreeTable()).thenReturn(treeTable);
    when(treeTable.getModel()).thenReturn(tableModel);
    model.setTree(treeTableTree);
  }

  @Test
  public void getters() {
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
    assertThat(lang.isChanged()).isFalse();

    // not set yet
    assertThat(model.getValueAt(lang, 2)).isNull();

    assertThat(model.getColumnName(0)).isNull();
  }

  @Test
  public void throw_exception_if_get_value_from_invalid_column() {
    exception.expect(IllegalArgumentException.class);
    model.getValueAt(rule, 3);
  }

  @Test
  public void throw_exception_if_get_class_from_invalid_column() {
    exception.expect(IllegalArgumentException.class);
    model.getColumnClass(4);
  }

  @Test
  public void can_only_edit_activation() {
    assertThat(model.isCellEditable(rule, 0)).isFalse();
    assertThat(model.isCellEditable(rule, 1)).isFalse();
    assertThat(model.isCellEditable(rule, 2)).isTrue();

    assertThat(model.isCellEditable(lang, 0)).isFalse();
    assertThat(model.isCellEditable(lang, 1)).isFalse();
    assertThat(model.isCellEditable(lang, 2)).isTrue();
  }

  @Test
  public void get_current_rule_activation() {
    Map<String, Boolean> ruleActivation = new HashMap<>();
    model.saveCurrentRuleActivation(ruleActivation);
    assertThat(ruleActivation).containsExactly(entry("key", true));
  }

  @Test
  public void should_set_value_in_rule() {
    model.setValueAt(false, rule, 2);
    assertThat(rule.isActivated()).isFalse();
    assertThat(lang.isActivated()).isFalse();
  }

  @Test
  public void should_set_value_in_lang() {
    model.setValueAt(false, lang, 2);
    assertThat(rule.isActivated()).isFalse();
    assertThat(lang.isActivated()).isFalse();
  }

  @Test
  public void should_calculate_language_activation_and_changed() {
    model.refreshLanguageActivation(lang);
    assertThat(lang.isActivated()).isTrue();
    assertThat(lang.isChanged()).isTrue();
    assertThat(model.getValueAt(lang, 2)).isEqualTo(true);
  }

  @Test
  public void swap_value_lang() {
    model.refreshLanguageActivation(lang);

    model.swapAndRefresh(lang);
    assertActivationIsFalse();
  }

  @Test
  public void swap_value_rule() {
    model.swapAndRefresh(rule);
    assertActivationIsFalse();
  }

  @Test
  public void should_restore_defaults() {
    model.restoreDefaults();
    assertActivationIsFalse();
  }

  private void assertActivationIsFalse() {
    assertThat(rule.isActivated()).isFalse();
    assertThat(rule.isChanged()).isFalse();

    assertThat(lang.isActivated()).isFalse();
    assertThat(lang.isChanged()).isFalse();

    verify(tableModel).fireTableDataChanged();
  }
}
