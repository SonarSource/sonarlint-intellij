package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.AbstractWizardStepEx;

public abstract class AbstractStep extends AbstractWizardStepEx {
  AbstractStep(String title) {
    super(title);
  }
}
