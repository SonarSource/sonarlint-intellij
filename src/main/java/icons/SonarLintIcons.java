/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;

public class SonarLintIcons {
  public static final Icon ICON_SONARQUBE = getIcon("/images/SonarQube.png");
  public static final Icon ICON_SONARCLOUD = getIcon("/images/SonarCloud.png");

  public static final Icon ICON_SONARQUBE_16 = getIcon("/images/onde-sonar-16.png");
  public static final Icon ICON_SONARCLOUD_16 = getIcon("/images/sonarcloud-16.png");

  public static final Icon SONARLINT_TOOLWINDOW = getIcon("/images/sonarlintToolWindow.svg");
  public static final Icon SONARLINT_ACTION = getIcon("/images/sonarlintAction.svg");
  public static final Icon SONARLINT_TOOLWINDOW_EMPTY = getIcon("/images/sonarlintToolWindowEmpty.svg");

  public static final Icon SONARLINT = getIcon("/images/sonarlint.png");
  public static final Icon SONARLINT_32 = getIcon("/images/sonarlint@2x.png");
  public static final Icon PLAY = getIcon("/images/execute.png");
  public static final Icon CLEAN = getIcon("/images/clean.png");
  public static final Icon TOOLS = getIcon("/images/externalToolsSmall.png");
  public static final Icon SUSPEND = getIcon("/images/suspend.png");
  public static final Icon INFO = getIcon("/images/info.png");
  public static final Icon WARN = getIcon("/images/warn.png");
  public static final Icon SCM = getIcon("/images/toolWindowChanges.png");
  public static final Icon PROJECT = getIcon("/images/ideaProject.png");
  public static final Icon NOT_CONNECTED = getIcon("/images/not_connected.svg");
  public static final Icon CONNECTED = getIcon("/images/connected.svg");
  public static final Icon CONNECTION_ERROR = getIcon("/images/io_error.svg");

  private static final Map<String, Icon> SEVERITY_ICONS = new HashMap<>();
  private static final Map<String, Icon> SEVERITY_ICONS_12 = new HashMap<>();
  private static final Map<String, Icon> TYPE_ICONS = new HashMap<>();
  private static final Map<String, Icon> TYPE_ICONS_12 = new HashMap<>();

  static {
    SEVERITY_ICONS.put("blocker", getIcon("/images/severity/blocker.png"));
    SEVERITY_ICONS.put("critical", getIcon("/images/severity/critical.png"));
    SEVERITY_ICONS.put("info", getIcon("/images/severity/info.png"));
    SEVERITY_ICONS.put("major", getIcon("/images/severity/major.png"));
    SEVERITY_ICONS.put("minor", getIcon("/images/severity/minor.png"));

    SEVERITY_ICONS_12.put("blocker", getIcon("/images/severity/blocker12.png"));
    SEVERITY_ICONS_12.put("critical", getIcon("/images/severity/critical12.png"));
    SEVERITY_ICONS_12.put("info", getIcon("/images/severity/info12.png"));
    SEVERITY_ICONS_12.put("major", getIcon("/images/severity/major12.png"));
    SEVERITY_ICONS_12.put("minor", getIcon("/images/severity/minor12.png"));

    TYPE_ICONS.put("bug", getIcon("/images/type/bug.png"));
    TYPE_ICONS.put("code_smell", getIcon("/images/type/code_smell.png"));
    TYPE_ICONS.put("vulnerability", getIcon("/images/type/vulnerability.png"));

    TYPE_ICONS_12.put("bug", getIcon("/images/type/bug12.png"));
    TYPE_ICONS_12.put("code_smell", getIcon("/images/type/code_smell12.png"));
    TYPE_ICONS_12.put("vulnerability", getIcon("/images/type/vulnerability12.png"));
  }

  private static Icon getIcon(String path) {
    return IconLoader.getIcon(path, SonarLintIcons.class);
  }

  private SonarLintIcons() {
    // only static
  }

  public static Icon severity12(IssueSeverity severity) {
    return SEVERITY_ICONS_12.get(severity.toString().toLowerCase(Locale.ENGLISH));
  }

  public static Icon type12(RuleType type) {
    return TYPE_ICONS_12.get(type.toString().toLowerCase(Locale.ENGLISH));
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
