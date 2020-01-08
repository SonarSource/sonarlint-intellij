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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.component.EditableList;

public class GlobalExclusionsPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String EMPTY_LABEL = "No exclusions configured";
  private static final String BORDER_TITLE = "File Exclusions";

  private JPanel panel;
  private JPanel patternList;
  private EditableList<String> list;
  private JBLabel helpLabel;

  public GlobalExclusionsPanel() {
    Border b = IdeBorderFactory.createTitledBorder(BORDER_TITLE);
    panel.setBorder(b);
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @Override public boolean isModified(SonarLintGlobalSettings settings) {
    return !Objects.equals(settings.getFileExclusions(), list.get());
  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    list.set(settings.getFileExclusions());
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    settings.setFileExclusions(list.get());
  }

  private void createUIComponents() {
    Supplier<String> onAdd = () -> {
      String s = Messages.showInputDialog(panel, "Enter new exclusion pattern", "Add File Exclusion",
        null, null, validator);
      return StringUtils.stripToNull(s);
    };

    Function<String, String> onEdit = value -> {
      String s = Messages.showInputDialog(panel, "Modify exclusion pattern", "Edit File Exclusion", null, value,
        validator);
      return StringUtils.stripToNull(s);
    };

    list = new EditableList<>(EMPTY_LABEL, onAdd, onEdit);
    patternList = list.getComponent();
  }

  private static final InputValidator validator = new InputValidator() {
    @Override public boolean checkInput(String inputString) {
      if (inputString.isEmpty()) {
        return false;
      }
      FileSystem fs = FileSystems.getDefault();
      try {
        fs.getPathMatcher("glob:" + inputString);
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    @Override public boolean canClose(String inputString) {
      return checkInput(inputString);
    }
  };
}
