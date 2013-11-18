/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.inspection;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.wsclient.jsonsimple.JSONObject;

import java.util.Map;

public class SonarIssueFromJsonReport implements ISonarIssue {

  private JSONObject jsonIssue;
  private Map<String, String> ruleByKey;

  public SonarIssueFromJsonReport(JSONObject jsonIssue, Map<String, String> ruleByKey) {
    this.jsonIssue = jsonIssue;
    this.ruleByKey = ruleByKey;
  }

  @Override
  public String key() {
    return ObjectUtils.toString(jsonIssue.get("key")); //$NON-NLS-1$
  }

  @Override
  public String resourceKey() {
    return ObjectUtils.toString(jsonIssue.get("component")); //$NON-NLS-1$
  }

  @Override
  public boolean resolved() {
    return StringUtils.isNotBlank(ObjectUtils.toString(jsonIssue.get("resolution"))); //$NON-NLS-1$
  }

  @Override
  public Integer line() {
    Long line = (Long) jsonIssue.get("line");//$NON-NLS-1$
    return line != null ? line.intValue() : null;
  }

  @Override
  public String severity() {
    return ObjectUtils.toString(jsonIssue.get("severity"));//$NON-NLS-1$
  }

  @Override
  public String message() {
    return ObjectUtils.toString(jsonIssue.get("message"));//$NON-NLS-1$
  }

  @Override
  public String ruleKey() {
    return ObjectUtils.toString(jsonIssue.get("rule"));//$NON-NLS-1$
  }

  @Override
  public String ruleName() {
    return ObjectUtils.toString(ruleByKey.get(ruleKey()));
  }

  @Override
  public String assignee() {
    return ObjectUtils.toString(jsonIssue.get("assignee"));//$NON-NLS-1$
  }

}