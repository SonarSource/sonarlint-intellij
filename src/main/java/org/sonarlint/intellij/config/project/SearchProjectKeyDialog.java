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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;

public class SearchProjectKeyDialog extends DialogWrapper {
  private final Map<String, RemoteProject> projectsByKey;

  private String lastSelectedProjectKey;
  private JPanel contentPane;
  private JBScrollPane scrollPane;
  private JBList<RemoteProject> projectList;

  public SearchProjectKeyDialog(Component parent, String selectedProjectKey, Map<String, RemoteProject> projectsByKey) {
    super(parent, false);
    this.lastSelectedProjectKey = selectedProjectKey;
    this.projectsByKey = projectsByKey;
    setTitle("Search Project in SonarQube Server");
    init();
  }

  private void createUIComponents() {
    createProjectList();
    setProjectsInList(projectsByKey.values());
  }

  private boolean updateOk() {
    boolean valid = getSelectedProjectKey() != null;
    myOKAction.setEnabled(valid);
    return valid;
  }

  @CheckForNull
  public String getSelectedProjectKey() {
    RemoteProject project = projectList.getSelectedValue();
    return project == null ? null : project.getKey();
  }

  private void createProjectList() {
    projectList = new JBList<>();
    projectList.setEmptyText("No projects found in the selected SonarQube Server");
    projectList.setCellRenderer(new ProjectListRenderer());
    projectList.addListSelectionListener(new ProjectItemListener());
    projectList.addMouseListener(new ProjectMouseListener());
    projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    projectList.setVisibleRowCount(10);
    projectList.setBorder(IdeBorderFactory.createBorder());
    new ListSpeedSearch<>(projectList, (Function<RemoteProject, String>) o -> o.getName() + " " + o.getKey());

    scrollPane = new JBScrollPane(projectList);

  }

  private void setProjectsInList(Collection<RemoteProject> projects) {
    Comparator<RemoteProject> projectComparator = (o1, o2) -> {
      int c1 = o1.getName().compareToIgnoreCase(o2.getName());
      if (c1 != 0) {
        return c1;
      }
      return o1.getKey().compareToIgnoreCase(o2.getKey());
    };

    List<RemoteProject> sortedProjects = projects.stream()
      .sorted(projectComparator)
      .collect(Collectors.toList());

    RemoteProject selected = null;
    if (lastSelectedProjectKey != null) {
      selected = sortedProjects.stream()
        .filter(project -> lastSelectedProjectKey.equals(project.getKey()))
        .findAny().orElse(null);
    }
    CollectionListModel<RemoteProject> projectListModel = new CollectionListModel<>(sortedProjects);

    projectList.setModel(projectListModel);
    projectList.setCellRenderer(new ProjectListRenderer());
    setSelectedProject(selected);
  }

  private void setSelectedProject(@Nullable RemoteProject selected) {
    if (selected != null) {
      projectList.setSelectedValue(selected, true);
    } else if (!projectList.isEmpty() && lastSelectedProjectKey == null) {
      projectList.setSelectedIndex(0);
    } else {
      projectList.setSelectedValue(null, true);
    }
    updateOk();
  }

  @org.jetbrains.annotations.Nullable @Override protected JComponent createCenterPanel() {
    return contentPane;
  }

  /**
   * Render projects in combo box
   */
  private static class ProjectListRenderer extends ColoredListCellRenderer<RemoteProject> {
    @Override protected void customizeCellRenderer(JList list, @Nullable RemoteProject value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        // should never happen
        return;
      }
      SimpleTextAttributes attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      append(value.getName(), attrs, true);
      // it is not working: appendTextPadding
      append(" ");
      if (index >= 0) {
        append("(" + value.getKey() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
      }
    }
  }

  private class ProjectItemListener implements ListSelectionListener {
    @Override public void valueChanged(ListSelectionEvent event) {
      updateOk();
    }
  }

  private class ProjectMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2 && updateOk()) {
        SearchProjectKeyDialog.super.doOKAction();
      }
    }
  }

}
