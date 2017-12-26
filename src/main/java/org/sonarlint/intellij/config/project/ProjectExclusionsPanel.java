package org.sonarlint.intellij.config.project;

import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.sonarlint.intellij.config.ConfigurationPanel;

public class ProjectExclusionsPanel implements ConfigurationPanel<SonarLintProjectSettings> {
  private static final String EMPTY_LABEL = "No exclusions configured";
  private final Project project;

  private ExclusionTable table;
  private JPanel tablePanel;
  private JPanel panel;

  public ProjectExclusionsPanel(Project project) {

    this.project = project;
  }

  private void createUIComponents() {
    Supplier<ExclusionItem> onAdd = () -> {
      AddEditExclusionDialog dialog = new AddEditExclusionDialog(project);
      if (dialog.showAndGet() && dialog.getExclusion() != null) {
        return dialog.getExclusion();
      }
      return null;
    };

    Function<ExclusionItem, ExclusionItem> onEdit = value -> {
      AddEditExclusionDialog dialog = new AddEditExclusionDialog(project);
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
      .collect(Collectors.toList());
    table.set(list);
  }

  private List<String> tableToString() {
    return table.get().stream()
      .map(ExclusionItem::toStringWithType)
      .collect(Collectors.toList());
  }

  @Override
  public void save(SonarLintProjectSettings settings) {
    settings.setFileExclusions(tableToString());
  }

}
