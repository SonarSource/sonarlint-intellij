package org.sonarlint.intellij.config;

import javax.swing.JComponent;

public interface ConfigurationPanel<T> {
  JComponent getComponent();

  boolean isModified(T settings);

  void save(T settings);

  void load(T settings);
}
