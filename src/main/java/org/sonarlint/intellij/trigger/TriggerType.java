package org.sonarlint.intellij.trigger;

public enum TriggerType {
  EDITOR_OPEN("Editor open"),
  ACTION("Action"),
  COMPILATION("Compilation"),
  EDITOR_CHANGE("Editor change");

  private final String name;

  TriggerType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
