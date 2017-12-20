package org.sonarlint.intellij.config.project;

import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.component.EditableList;

public class ExclusionsPanel  implements ConfigurationPanel<SonarLintProjectSettings> {
  private static final String EMPTY_LABEL = "No exclusions configured";
  private static final String BORDER_TITLE = "Exclusions";
  private final Project project;

  private JComponent panel;
  private EditableList<String> list;

  public ExclusionsPanel(Project project) {
    this.project = project;
    create();
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @Override public boolean isModified(SonarLintProjectSettings settings) {
    return !Objects.equals(settings.getFileExclusions(), list.get());
  }

  @Override
  public void load(SonarLintProjectSettings settings) {
    list.set(settings.getFileExclusions());
  }

  @Override
  public void save(SonarLintProjectSettings settings) {
    settings.setFileExclusions(list.get());
  }

  public void create() {
    Border b = IdeBorderFactory.createTitledBorder(BORDER_TITLE);
    panel = new JPanel(new BorderLayout());
    panel.setBorder(b);
    Supplier<String> onAdd = () -> {
      AddEditExclusionDialog dialog = new AddEditExclusionDialog(project);
      if (dialog.showAndGet() && dialog.getExclusion() != null) {
        return dialog.getExclusion().toStringWithType();
      }
      return null;
    };

    Function<String, String> onEdit = value -> {
      AddEditExclusionDialog dialog = new AddEditExclusionDialog(project);
      dialog.setExclusion(ExclusionItem.parse(value));
      if (dialog.showAndGet() && dialog.getExclusion() != null) {
        return dialog.getExclusion().toStringWithType();
      }
      return null;
    };

    list = new EditableList<>(EMPTY_LABEL, onAdd, onEdit);
    panel.add(list.getComponent());
  }
}
