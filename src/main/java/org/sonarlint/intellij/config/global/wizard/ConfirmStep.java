package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.CommitStepException;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfirmStep extends AbstractStep {
  private final WizardModel model;
  private JPanel panel;

  public ConfirmStep(WizardModel model) {
    super("Done");
    this.model = model;
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @NotNull @Override public Object getStepId() {
    return ConfirmStep.class;
  }

  @Nullable @Override public Object getNextStepId() {
    return null;
  }

  @Nullable @Override public Object getPreviousStepId() {
    if (model.getOrganizationList().size() > 1) {
      return OrganizationStep.class;
    } else {
      return AuthStep.class;
    }
  }

  @Override public boolean isComplete() {
    return true;
  }

  @Override public void commit(CommitType commitType) throws CommitStepException {
  }

  @Nullable @Override public JComponent getPreferredFocusedComponent() {
    return null;
  }
}
