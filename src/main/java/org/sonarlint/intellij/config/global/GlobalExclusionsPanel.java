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
  private JBLabel helpLabel;
  private EditableList<String> list;

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

    Function<String, String> onEdit = (value) -> {
      String s = Messages.showInputDialog(panel, "Modify exclusion pattern", "Edit File Exclusion", null, value,
        validator);
      return StringUtils.stripToNull(s);
    };

    list = new EditableList<>(EMPTY_LABEL, onAdd, onEdit);
    patternList = list.getComponent();
  }

  private static final InputValidator validator = new  InputValidator() {
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
