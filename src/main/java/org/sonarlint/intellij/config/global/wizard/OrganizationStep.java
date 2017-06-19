package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.Convertor;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;

import static javax.swing.JList.VERTICAL;

public class OrganizationStep extends AbstractStep {
  private final WizardModel model;
  private JList orgList;
  private JPanel panel;

  public OrganizationStep(WizardModel model) {
    super("Organization");
    this.model = model;
  }

  private void save() {
    RemoteOrganization org = (RemoteOrganization) orgList.getSelectedValue();
    if (org != null) {
      model.setOrganization(org.getKey());
    } else {
      model.setOrganization(null);
    }
  }

  public void _init() {
    List<RemoteOrganization> list = model.getOrganizationList();
    int size = list.size();
    orgList.setListData(list.toArray(new RemoteOrganization[size]));
    orgList.addListSelectionListener(e -> fireStateChanged());

    if (model.getOrganization() != null) {
      for (int i = 0; i < orgList.getModel().getSize(); i++) {
        RemoteOrganization org = (RemoteOrganization) orgList.getModel().getElementAt(i);
        if (model.getOrganization().equals(org.getKey())) {
          orgList.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  @NotNull @Override public Object getStepId() {
    return OrganizationStep.class;
  }

  @Nullable @Override public Object getNextStepId() {
    return null;
  }

  @Nullable @Override public Object getPreviousStepId() {
    return AuthStep.class;
  }

  @Override public boolean isComplete() {
    return model.getOrganizationList().size() <= 1 || orgList.getSelectedValue() != null;
  }

  @Override public void commit(CommitType commitType) throws CommitStepException {
    if (commitType == CommitType.Finish || commitType == CommitType.Next) {
      save();
    }
  }

  @Override public JComponent getComponent() {
    return panel;
  }

  @Nullable @Override public JComponent getPreferredFocusedComponent() {
    return null;
  }

  private void createUIComponents() {
    JBList orgList = new JBList();
    orgList.setLayoutOrientation(VERTICAL);
    orgList.setVisibleRowCount(8);
    orgList.setEnabled(true);
    orgList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    orgList.setCellRenderer(new ListRenderer());

    Convertor<Object, String> convertor = o -> {
      RemoteOrganization org = (RemoteOrganization) o;
      return org.getName() + " " + org.getKey();
    };
    new ListSpeedSearch(orgList, convertor);
    this.orgList = orgList;
  }

  private static class ListRenderer extends ColoredListCellRenderer<RemoteOrganization> {
    @Override protected void customizeCellRenderer(JList list, @Nullable RemoteOrganization value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        return;
      }

      append(value.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);
      // it is not working: appendTextPadding
      append(" ");
      if (index >= 0) {
        append("(" + value.getKey() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
      }
    }
  }
}
