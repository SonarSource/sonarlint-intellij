/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultMutableTreeNode;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParamType;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;

public abstract class RulesTreeNode<T> extends DefaultMutableTreeNode {
  protected Boolean activated;

  public Iterable<T> childrenIterable() {
    var children = children();
    return () -> new Iterator<T>() {
      @Override
      public boolean hasNext() {
        return children.hasMoreElements();
      }

      @Override
      public T next() {
        if (!children.hasMoreElements()) {
          throw new NoSuchElementException();
        }
        return (T) children.nextElement();
      }
    };
  }

  public abstract boolean isNonDefault();

  public void setIsActivated(@Nullable Boolean activated) {
    this.activated = activated;
  }

  public Boolean isActivated() {
    return activated;
  }

  public static class Language extends RulesTreeNode<RulesTreeNode.Rule> {
    private final String label;
    private boolean nonDefault;

    public Language(String label) {
      this.label = label;
    }

    @Override
    public boolean isNonDefault() {
      return nonDefault;
    }

    public void setIsNonDefault(boolean nonDefault) {
      this.nonDefault = nonDefault;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public static class Root extends RulesTreeNode<RulesTreeNode.Language> {
    @Override
    public String toString() {
      return "root";
    }

    @Override
    public boolean isNonDefault() {
      return false;
    }
  }

  public static class Rule extends RulesTreeNode {
    private final StandaloneRuleDetails details;
    private final Map<String, String> nonDefaultParams;

    public Rule(StandaloneRuleDetails details, boolean activated, Map<String, String> nonDefaultParams) {
      this.details = details;
      this.activated = activated;
      this.nonDefaultParams = new HashMap<>(nonDefaultParams);
    }

    public String getKey() {
      return details.getKey();
    }

    public String getHtmlDescription() {
      return details.getHtmlDescription();
    }

    public String getName() {
      return details.getName();
    }

    public boolean getDefaultActivation() {
      return details.isActiveByDefault();
    }

    public String severity() {
      return details.getSeverity();
    }

    public String type() {
      return details.getType();
    }

    public org.sonarsource.sonarlint.core.commons.Language language() {
      return details.getLanguage();
    }

    @Override
    public boolean isNonDefault() {
      return details.isActiveByDefault() != activated || (activated && !nonDefaultParams.isEmpty());
    }

    @Override
    public String toString() {
      return getName();
    }

    public boolean hasParameters() {
      return !getParamDetails().isEmpty();
    }

    public List<RuleParam> getParamDetails() {
      return ((StandaloneRule) details).paramDetails()
        .stream()
        .map(RuleParam::new)
        .collect(Collectors.toList());
    }

    public Map<String, String> getCustomParams() {
      return nonDefaultParams;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Rule)) {
        return false;
      }
      var rule = (Rule) o;
      return details.getKey().equals(rule.details.getKey()) && activated == rule.activated && nonDefaultParams.equals(rule.nonDefaultParams);
    }

    @Override
    public int hashCode() {
      return Objects.hash(details.getKey(), activated, nonDefaultParams);
    }
  }
  public static class RuleParam {
    final String key;
    final String name;
    final String description;
    final StandaloneRuleParamType type;
    final boolean isMultiple;
    @CheckForNull
    final String defaultValue;
    final String[] options;

    public RuleParam(StandaloneRuleParam p) {
      this(p.key(), p.name(), p.description(), p.type(), p.multiple(), p.defaultValue(), p.possibleValues().toArray(new String[0]));
    }

    public RuleParam(String key, String name, String description, StandaloneRuleParamType type, boolean isMultiple, @Nullable String defaultValue, String... options) {
      this.key = key;
      this.name = name;
      this.description = description;
      this.type = type;
      this.isMultiple = isMultiple;
      this.defaultValue = defaultValue;
      this.options = options;
    }
  }
}
