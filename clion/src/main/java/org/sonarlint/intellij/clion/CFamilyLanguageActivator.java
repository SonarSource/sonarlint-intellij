package org.sonarlint.intellij.clion;

import java.util.Set;
import org.sonarlint.intellij.common.LanguageActivator;
import org.sonarsource.sonarlint.core.client.api.common.Language;

public class CFamilyLanguageActivator implements LanguageActivator {
  @Override
  public void amendLanguages(Set<Language> enabledLanguages) {
    // Only C/C++ for now in CLion
    enabledLanguages.clear();
    enabledLanguages.add(Language.C);
    enabledLanguages.add(Language.CPP);
  }
}
