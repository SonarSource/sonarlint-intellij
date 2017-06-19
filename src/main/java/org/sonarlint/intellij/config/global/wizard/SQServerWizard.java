package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.AbstractWizardEx;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.sonarlint.intellij.config.global.SonarQubeServer;

public class SQServerWizard {
  private final WizardModel model;
  private AbstractWizardEx wizard;

  public SQServerWizard(SonarQubeServer serverToEdit) {
    model = new WizardModel(serverToEdit);
    init(model, true, Collections.emptySet());
  }

  public SQServerWizard(Set<String> existingNames) {
    model = new WizardModel();
    init(model, false, existingNames);
  }

  private void init(WizardModel model, boolean editing, Set<String> existingNames) {
    List<AbstractWizardStepEx> steps = createSteps(model, editing, existingNames);
    wizard = new SonarQubeWizard(steps);
  }

  private List<AbstractWizardStepEx> createSteps(WizardModel model, boolean editing, Set<String> existingNames) {
    List<AbstractWizardStepEx> steps = new LinkedList<>();
    steps.add(new ServerStep(model, editing, existingNames));
    steps.add(new AuthStep(model));
    steps.add(new OrganizationStep(model));
    steps.add(new ConfirmStep(model));
    return steps;
  }

  public boolean showAndGet() {
    return wizard.showAndGet();
  }

  public SonarQubeServer getServer() {
    return model.createServer();
  }

  private static class SonarQubeWizard extends AbstractWizardEx {
    public SonarQubeWizard(List<AbstractWizardStepEx> steps) {
      super("New SonarQube Server Configuration", null, steps);
      this.setHorizontalStretch(1.25f);
      this.setVerticalStretch(1.25f);
    }

    protected String getDimensionServiceKey() {
      return this.getClass().getName();
    }
  }
}
