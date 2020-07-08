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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.Icon;

public class SonarLintIcons {
  public static final Icon ICON_SONARQUBE = IconLoader.getIcon("/images/SonarQube.png");
  public static final Icon ICON_SONARCLOUD = IconLoader.getIcon("/images/SonarCloud.png");

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

  private static final Map<String, Icon> SEVERITY_ICONS = new HashMap<>();
  private static final Map<String, Icon> SEVERITY_ICONS_12 = new HashMap<>();
  private static final Map<String, Icon> TYPE_ICONS = new HashMap<>();
  private static final Map<String, Icon> TYPE_ICONS_12 = new HashMap<>();

  static {
    SEVERITY_ICONS.put("blocker", IconLoader.getIcon("/images/severity/blocker.png"));
    SEVERITY_ICONS.put("critical", IconLoader.getIcon("/images/severity/critical.png"));
    SEVERITY_ICONS.put("info", IconLoader.getIcon("/images/severity/info.png"));
    SEVERITY_ICONS.put("major", IconLoader.getIcon("/images/severity/major.png"));
    SEVERITY_ICONS.put("minor", IconLoader.getIcon("/images/severity/minor.png"));

    SEVERITY_ICONS_12.put("blocker", IconLoader.getIcon("/images/severity/blocker12.png"));
    SEVERITY_ICONS_12.put("critical", IconLoader.getIcon("/images/severity/critical12.png"));
    SEVERITY_ICONS_12.put("info", IconLoader.getIcon("/images/severity/info12.png"));
    SEVERITY_ICONS_12.put("major", IconLoader.getIcon("/images/severity/major12.png"));
    SEVERITY_ICONS_12.put("minor", IconLoader.getIcon("/images/severity/minor12.png"));

    TYPE_ICONS.put("bug", IconLoader.getIcon("/images/type/bug.png"));
    TYPE_ICONS.put("code_smell", IconLoader.getIcon("/images/type/code_smell.png"));
    TYPE_ICONS.put("vulnerability", IconLoader.getIcon("/images/type/vulnerability.png"));

    TYPE_ICONS_12.put("bug", IconLoader.getIcon("/images/type/bug12.png"));
    TYPE_ICONS_12.put("code_smell", IconLoader.getIcon("/images/type/code_smell12.png"));
    TYPE_ICONS_12.put("vulnerability", IconLoader.getIcon("/images/type/vulnerability12.png"));
  }

  private SonarLintIcons() {
    // only static
  }

  public static Icon severity12(String severity) {
    return SEVERITY_ICONS_12.get(severity.toLowerCase(Locale.ENGLISH));
  }

  public static Icon type12(String type) {
    return TYPE_ICONS_12.get(type.toLowerCase(Locale.ENGLISH));
  }

  public static Icon severity(String severity) {
    return SEVERITY_ICONS.get(severity.toLowerCase(Locale.ENGLISH));
  }

  public static Icon toDisabled(Icon icon) {
    return IconLoader.getDisabledIcon(icon);
  }

  public static Icon type(String type) {
    return TYPE_ICONS.get(type.toLowerCase(Locale.ENGLISH));
  }
}
