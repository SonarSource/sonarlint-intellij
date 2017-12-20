package org.sonarlint.intellij.config.project;

import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;

import static org.sonarlint.intellij.config.project.ExclusionItem.Type.DIRECTORY;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.FILE;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.GLOB;

public class ExclusionItem {
  private Type type;
  private String item;

  public enum Type {
    FILE, DIRECTORY, GLOB
  }

  public ExclusionItem(Type type, String item) {
    this.type = type;
    this.item = item;
  }

  @CheckForNull
  public static ExclusionItem parse(String text) {
    int i = text.indexOf(':');
    if (i < 0) {
      return null;
    }
    String item = text.substring(i+1);
    if (StringUtils.trimToNull(item) == null) {
      return null;
    }
    switch (text.substring(0, i).toUpperCase()) {
      case "FILE":
        return new ExclusionItem(FILE, item);
      case "DIRECTORY":
        return new ExclusionItem(DIRECTORY, item);
      case "GLOB":
        return new ExclusionItem(GLOB, item);
      default:
        return null;
    }
  }

  public String item() {
    return item;
  }

  public Type type() {
    return type;
  }

  public String toStringWithType() {
    return StringUtils.capitalize(type.name().toLowerCase()) + ":" + item;
  }
}
