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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;

public class SonarLintProjectAnalyzersPanel extends JBPanel<SonarLintProjectAnalyzersPanel> {

  private static final ColumnInfo<PluginDetails, String> PLUGIN_NAME = new ColumnInfo<PluginDetails, String>("Code Analyzer") {
    @Override
    public String valueOf(PluginDetails plugin) {
      return StringUtils.capitalize(plugin.name());
    }
  };

  private static final ColumnInfo<PluginDetails, String> PLUGIN_VERSION = new ColumnInfo<PluginDetails, String>("Version") {
    @Override
    public String valueOf(PluginDetails plugin) {
      return plugin.version();
    }
  };

  public SonarLintProjectAnalyzersPanel(Project project) {
    super(new BorderLayout());
    ProjectBindingManager bindingManager = SonarLintUtils.getService(project, ProjectBindingManager.class);
    List<PluginDetails> loadedAnalyzers;
    try {
      loadedAnalyzers = bindingManager.getFacade().getPluginDetails()
        .stream()
        .filter(pluginDetails -> !pluginDetails.skipReason().isPresent())
        .collect(Collectors.toList());
    } catch (Exception e) {
      SonarLintConsole.get(project).error("Unable to get plugin details", e);
      loadedAnalyzers = Collections.emptyList();
    }
    TableView<PluginDetails> tableView = new TableView<>(new ListTableModel<>(new ColumnInfo[] {PLUGIN_NAME, PLUGIN_VERSION}, loadedAnalyzers));
    tableView.setShowGrid(false);
    tableView.setIntercellSpacing(JBUI.emptySize());
    tableView.setDragEnabled(false);
    tableView.setShowVerticalLines(false);
    tableView.getTableHeader().setReorderingAllowed(false);
    tableView.enableInputMethods(false);
    tableView.setDefaultRenderer(String.class, new NoFocusCellRenderer(new DefaultTableCellRenderer()));
    this.add(ScrollPaneFactory.createScrollPane(tableView), BorderLayout.CENTER);
    this.setMinimumSize(new JBDimension(300, 50));
    this.setPreferredSize(new JBDimension(600, 200));
  }

  private static class NoFocusCellRenderer extends DefaultTableCellRenderer {
    private final TableCellRenderer delegate;

    public NoFocusCellRenderer(TableCellRenderer delegate) {
      this.delegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return delegate.getTableCellRendererComponent(table, value, isSelected, false, row, column);
    }
  }

}
