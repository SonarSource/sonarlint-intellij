/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.config.project;

import java.util.Locale;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;

import static org.sonarlint.intellij.config.project.ExclusionItem.Type.DIRECTORY;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.FILE;
import static org.sonarlint.intellij.config.project.ExclusionItem.Type.GLOB;

public record ExclusionItem(Type type, String item) {
  public enum Type {
    FILE, DIRECTORY, GLOB
  }

  @CheckForNull
  public static ExclusionItem parse(String text) {
    var i = text.indexOf(':');
    if (i < 0) {
      return null;
    }
    var item = text.substring(i + 1);
    if (StringUtils.trimToNull(item) == null) {
      return null;
    }
    return switch (text.substring(0, i).toUpperCase(Locale.ENGLISH)) {
      case "FILE" -> new ExclusionItem(FILE, item);
      case "DIRECTORY" -> new ExclusionItem(DIRECTORY, item);
      case "GLOB" -> new ExclusionItem(GLOB, item);
      default -> null;
    };
  }

  public String toStringWithType() {
    return type.name() + ":" + item;
  }
}
