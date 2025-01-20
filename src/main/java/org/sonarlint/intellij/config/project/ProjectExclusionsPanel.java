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
package org.sonarlint.intellij.config.project;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.documentation.SonarLintDocumentation;

public class ProjectExclusionsPanel implements ConfigurationPanel<SonarLintProjectSettings> {
  private static final String EMPTY_LABEL = "No exclusions configured";
  private final Project project;

  private ExclusionTable table;
  private JPanel tablePanel;
  private JPanel panel;
  private JEditorPane editorPane1;
  private JEditorPane editorPane2;

  public ProjectExclusionsPanel(Project project) {
    this.project = project;

    var projectSettings = Settings.getSettingsFor(project);
    String message;
    if (projectSettings.isBound()) {
      message = "<b>Your project is currently bound</b>, exclusions defined in the server's General Settings <a href=\"" + SonarLintDocumentation.Intellij.FILE_EXCLUSION_LINK
        + "\">override</a> your locally defined exclusions.";
      editorPane2.setVisible(false);
    } else {
      message = "When a project is connected to SonarQube (Server, Cloud), exclusions defined in its General Settings " +
        "<a href=\"" + SonarLintDocumentation.Intellij.FILE_EXCLUSION_LINK + "\">override</a> your locally defined exclusions.";
      editorPane2.setVisible(true);
    }

    initHtmlPane(editorPane1);
    SwingHelper.setHtml(editorPane1, message, UIUtil.getLabelForeground());
    editorPane1.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        BrowserUtil.browse(SonarLintDocumentation.Intellij.FILE_EXCLUSION_LINK);
      }
    });

    initHtmlPane(editorPane2);
    SwingHelper.setHtml(editorPane2, "The exclusions will not apply to manually triggered analysis.", UIUtil.getLabelForeground());
  }

  private void createUIComponents() {
    Supplier<ExclusionItem> onAdd = () -> {
      AddEditExclusionDialog dialog = new AddEditExclusionDialog(project);
      if (dialog.showAndGet() && dialog.getExclusion() != null) {
        return dialog.getExclusion();
      }
      return null;
    };

    UnaryOperator<ExclusionItem> onEdit = value -> {
      var dialog = new AddEditExclusionDialog(project);
      dialog.setExclusion(value);
      if (dialog.showAndGet() && dialog.getExclusion() != null) {
        return dialog.getExclusion();
      }
      return null;
    };

    table = new ExclusionTable(EMPTY_LABEL, onAdd, onEdit);
    tablePanel = table.getComponent();
  }

  @Override public JComponent getComponent() {
    return panel;
  }

  @Override public boolean isModified(SonarLintProjectSettings settings) {
    return !Objects.equals(settings.getFileExclusions(), tableToString());
  }

  @Override
  public void load(SonarLintProjectSettings settings) {
    List<ExclusionItem> list = settings.getFileExclusions().stream()
      .map(ExclusionItem::parse)
      .filter(Objects::nonNull)
      .toList();
    table.set(list);
  }

  private List<String> tableToString() {
    return table.get().stream()
      .map(ExclusionItem::toStringWithType)
      .toList();
  }

  @Override
  public void save(SonarLintProjectSettings settings) {
    settings.setFileExclusions(tableToString());
  }

}
