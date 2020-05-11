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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultMutableTreeNode;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleParamType;

public abstract class RulesTreeNode<T> extends DefaultMutableTreeNode {
  protected Boolean activated;

  public Iterable<T> childrenIterable() {
    Enumeration children = children();
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

  public abstract boolean isChanged();

  public void setIsActivated(@Nullable Boolean activated) {
    this.activated = activated;
  }

  public Boolean isActivated() {
    return activated;
  }

  public static class Language extends RulesTreeNode<RulesTreeNode.Rule> {
    private final String label;
    private boolean changed;

    public Language(String label) {
      this.label = label;
    }

    @Override
    public boolean isChanged() {
      return changed;
    }

    public void setIsChanged(boolean changed) {
      this.changed = changed;
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
    public boolean isChanged() {
      return false;
    }
  }

  public static class Rule extends RulesTreeNode {
    private final RuleDetails details;
    private final Map<String, String> params;

    public Rule(RuleDetails details, boolean activated, Map<String, String> params) {
      this.details = details;
      this.activated = activated;
      this.params = new HashMap<>(params);
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

    public String languageKey() {
      return details.getLanguageKey();
    }

    @Override
    public boolean isChanged() {
      return details.isActiveByDefault() != activated;
    }

    @Override
    public String toString() {
      return getName();
    }

    public boolean hasParameters() {
      return !getParamDetails().isEmpty();
    }

    public List<RuleParam> getParamDetails() {
      return ((StandaloneRule) details).params()
              .stream()
              .map(p -> (StandaloneRuleParam) p)
              .map(RuleParam::new)
              .collect(Collectors.toList());
    }

    public Map<String, String> getCustomParams() {
      return params;
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
