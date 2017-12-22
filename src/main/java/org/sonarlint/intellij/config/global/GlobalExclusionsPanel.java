package org.sonarlint.intellij.config.global;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.IdeBorderFactory;
import java.awt.BorderLayout;
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
  private static final String BORDER_TITLE = "Exclusions";

  private JComponent panel;
  private EditableList<String> list;

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

  public GlobalExclusionsPanel() {
    create();
  }

  public void create() {
    Border b = IdeBorderFactory.createTitledBorder(BORDER_TITLE);
    panel = new JPanel(new BorderLayout());
    panel.setBorder(b);
    Supplier<String> onAdd = () -> {
      String s = Messages.showInputDialog(panel, "Enter new exclusion pattern", "Add File Exclusion", null, null, null);
      return StringUtils.stripToNull(s);
    };

    Function<String, String> onEdit = (value) -> {
      String s = Messages.showInputDialog(panel, "Modify exclusion pattern", "Edit File Exclusion", null, value, null);
      return StringUtils.stripToNull(s);
    };

    list = new EditableList<>(EMPTY_LABEL, onAdd, onEdit);
    panel.add(list.getComponent());
  }
}
