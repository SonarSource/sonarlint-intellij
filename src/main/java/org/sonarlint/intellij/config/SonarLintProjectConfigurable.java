package org.sonarlint.intellij.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.EditableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class SonarLintProjectConfigurable implements Configurable {

  private PropertiesTableModel model;
  private Project project;
  private SonarLintProjectSettings projectSettings;

  public SonarLintProjectConfigurable(Project p) {
    this.project = p;
    this.projectSettings = project.getComponent(SonarLintProjectSettings.class);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "SonarLint";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    model = new PropertiesTableModel();
    model.setOptions(projectSettings.getAdditionalProperties());
    return new SonarLintPropertiesForm(model).getRootPane();
  }

  private static class PropertiesTableModel extends AbstractTableModel implements EditableModel {
    private final java.util.List<KeyValuePair> myRows = new ArrayList<KeyValuePair>();

    public String getColumnName(int column) {
      switch (column) {
        case 0: return "Property Name";
        case 1: return "Value";
      }
      return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    public int getRowCount() {
      return myRows.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 || columnIndex == 1;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case 0: return myRows.get(rowIndex).key;
        case 1: return myRows.get(rowIndex).value;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        switch (columnIndex) {
          case 0:
            myRows.get(rowIndex).key = (String)aValue;
            break;
          case 1:
            myRows.get(rowIndex).value = (String)aValue;
            break;
        }
      }
    }

    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    public void addRow() {
      myRows.add(new KeyValuePair());
      final int index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void setOptions(Map<String, String> options) {
      clear();
      if (!options.isEmpty()) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
          myRows.add(new KeyValuePair(entry.getKey(), entry.getValue()));
        }
        Collections.sort(myRows, new Comparator<KeyValuePair>() {
          @Override
          public int compare(KeyValuePair o1, KeyValuePair o2) {
            return o1.key.compareToIgnoreCase(o2.key);
          }
        });
        fireTableRowsInserted(0, options.size()-1);
      }
    }

    public void clear() {
      final int count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count-1);
      }
    }

    public Map<String, String> getOptions() {
      final Map<String, String> map = new java.util.HashMap<String, String>();
      for (KeyValuePair pair : myRows) {
        map.put(pair.key.trim(), pair.value.trim());
      }
      map.remove("");
      return map;
    }

    private static final class KeyValuePair {
      String key;
      String value;

      KeyValuePair() {
        this("", "");
      }

      KeyValuePair(String key, String value) {
        this.key = key;
        this.value = value;
      }
    }
  }

  @Override
  public boolean isModified() {
    return !model.getOptions().equals(projectSettings.getAdditionalProperties());
  }

  @Override
  public void apply() throws ConfigurationException {
    projectSettings.setAdditionalProperties(model.getOptions());
  }

  @Override
  public void reset() {
    model.setOptions(projectSettings.getAdditionalProperties());
  }

  @Override
  public void disposeUIResources() {

  }
}
