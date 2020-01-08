/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package icons;

import com.intellij.openapi.util.IconLoader;
import java.util.Locale;
import javax.swing.Icon;

public class SonarLintIcons {
  public static final Icon ICON_SONARQUBE_16 = IconLoader.getIcon("/images/onde-sonar-16.png");
  public static final Icon ICON_SONARCLOUD_16 = IconLoader.getIcon("/images/sonarcloud-16.png");
  public static final Icon ICON_SONARLINT_13 = IconLoader.getIcon("/images/ico-sonarlint-13.png");

  public static final Icon SONARLINT = IconLoader.getIcon("/images/sonarlint.png");
  public static final Icon SONARLINT_32 = IconLoader.getIcon("/images/sonarlint@2x.png");
  public static final Icon PLAY = IconLoader.getIcon("/images/execute.png");
  public static final Icon CLEAN = IconLoader.getIcon("/images/clean.png");
  public static final Icon TOOLS = IconLoader.getIcon("/images/externalToolsSmall.png");
  public static final Icon SUSPEND = IconLoader.getIcon("/images/suspend.png");
  public static final Icon INFO = IconLoader.getIcon("/images/info.png");
  public static final Icon WARN = IconLoader.getIcon("/images/warn.png");
  public static final Icon SCM = IconLoader.getIcon("/images/toolWindowChanges.png");
  public static final Icon PROJECT = IconLoader.getIcon("/images/ideaProject.png");

  private SonarLintIcons() {
    // only static
  }

  public static Icon severity12(String severity) {
    return severity(severity + "12");
  }

  public static Icon type12(String type) {
    return type(type + "12");
  }

  public static Icon severity(String severity) {
    String name = severity.toLowerCase(Locale.ENGLISH) + ".png";
    return IconLoader.getIcon("/images/severity/" + name);
  }

  public static Icon icon(String name) {
    String nameWithExtension = name + ".png";
    return IconLoader.getIcon("/images/" + nameWithExtension);
  }

  public static Icon toDisabled(Icon icon) {
    return IconLoader.getDisabledIcon(icon);
  }

  public static Icon type(String type) {
    String name = type.toLowerCase(Locale.ENGLISH) + ".png";
    return IconLoader.getIcon("/images/type/" + name);
  }
}
