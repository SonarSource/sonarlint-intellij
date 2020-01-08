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
package org.sonarlint.intellij.config.global.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class RulesFilterModel {
  private final Runnable onChange;

  private boolean showOnlyChanged;
  private boolean showOnlyEnabled;
  private boolean showOnlyDisabled;
  private String text;
  private List<String> tokenizedText = Collections.emptyList();

  public RulesFilterModel(Runnable onChange) {
    this.onChange = onChange;
  }

  public boolean isShowOnlyChanged() {
    return showOnlyChanged;
  }

  public void setShowOnlyChanged(boolean showOnlyChanged) {
    this.showOnlyChanged = showOnlyChanged;
    this.showOnlyDisabled = false;
    this.showOnlyEnabled = false;
    onChange.run();
  }

  public boolean isShowOnlyEnabled() {
    return showOnlyEnabled;
  }

  public void setShowOnlyEnabled(boolean showOnlyEnabled) {
    this.showOnlyEnabled = showOnlyEnabled;
    this.showOnlyDisabled = false;
    this.showOnlyChanged = false;
    onChange.run();
  }

  public boolean isShowOnlyDisabled() {
    return showOnlyDisabled;
  }

  @CheckForNull
  public String getText() {
    return text;
  }

  public void setText(@Nullable String text) {
    if (text == null || text.trim().isEmpty()) {
      this.text = null;
    } else {
      this.text = text;
    }
    tokenizedText = tokenize(this.text);
    onChange.run();
  }

  public void setShowOnlyDisabled(boolean showOnlyDisabled) {
    this.showOnlyDisabled = showOnlyDisabled;
    this.showOnlyChanged = false;
    this.showOnlyEnabled = false;
    onChange.run();
  }

  public boolean isEmpty() {
    return text == null && !isShowOnlyChanged() && !isShowOnlyDisabled() && !isShowOnlyEnabled();
  }

  public void reset(boolean triggerListener) {
    showOnlyChanged = false;
    showOnlyDisabled = false;
    showOnlyEnabled = false;
    text = null;
    if (triggerListener) {
      onChange.run();
    }
  }

  public boolean filter(RulesTreeNode.Rule rule) {
    if (showOnlyEnabled && !rule.isActivated()) {
      return false;
    }
    if (showOnlyDisabled && rule.isActivated()) {
      return false;
    }
    if (showOnlyChanged && !rule.isChanged()) {
      return false;
    }

    if (tokenizedText.isEmpty()) {
      return true;
    }

    List<String> split = new ArrayList<>();
    split.addAll(tokenize(rule.getName()));
    split.addAll(tokenize(rule.getKey()));
    return Collections.indexOfSubList(split, tokenizedText) != -1;
  }

  private static List<String> tokenize(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return Collections.emptyList();
    }
    String lower = str.toLowerCase(Locale.US);
    return Arrays.asList(lower.split("\\s"));
  }
}
