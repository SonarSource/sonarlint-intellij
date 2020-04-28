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

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultMutableTreeNode;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

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

    public Rule(RuleDetails details, boolean activated) {
      this.details = details;
      this.activated = activated;
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
      // FIXME
      if (languageKey().equals("java")) {
        return Arrays.asList(new RuleParam("myBool", "A Boolean", "Very long description of a boolean\nwith\nnew\nlines", RuleParamType.BOOLEAN, false, "false"));
      } else if (languageKey().equals("php")) {
        return Arrays.asList(new RuleParam("myInt", "A Int", "Very long description of a int\nwith\nnew\nlines", RuleParamType.INT, false, "12"));
      } else if (languageKey().equals("html")) {
        return Arrays.asList(new RuleParam("myText", "A text", "Very long description of a text\nwith\nnew\nlines", RuleParamType.TEXT, false, "Bla bla\nbla bla\nfoo foo"));
      } else if (languageKey().equals("kotlin")) {
        return Arrays.asList(new RuleParam("myString", "A string", "Very long description of a string\nwith\nnew\nlines", RuleParamType.STRING, false, "Bla bla foo bar"));
      } else if (languageKey().equals("py")) {
        return Arrays.asList();
      } else {
        return Arrays.asList(new RuleParam("myBool", "A Boolean", "Very long description of a boolean\nwith\nnew\nlines", RuleParamType.BOOLEAN, false, "false"),
          new RuleParam("myInt", "A Int", "Very long description of a int\nwith\nnew\nlines", RuleParamType.INT, false, "12"),
          new RuleParam("myFloat", "A float param", "Very long description of a float\nwith\nnew\nlines", RuleParamType.FLOAT, false, "1.25"),
          new RuleParam("myText", "A text", "Very long description of a text\nwith\nnew\nlines", RuleParamType.TEXT, false, "Bla bla\nbla bla\nfoo foo"),
          new RuleParam("myString", "A string", "Very long description of a string\nwith\nnew\nlines", RuleParamType.STRING, false, "Bla bla foo bar"));
      }
    }

    public Map<String, String> getCustomParams() {
      // FIXME
      return new HashMap<>();
    }
  }
  public static class RuleParam {
    final String key;
    final String name;
    final String description;
    final RuleParamType type;
    final boolean isMultiple;
    final String defaultValue;
    final String[] options;

    public RuleParam(String key, String name, String description, RuleParamType type, boolean isMultiple, String defaultValue, String... options) {
      this.key = key;
      this.name = name;
      this.description = description;
      this.type = type;
      this.isMultiple = isMultiple;
      this.defaultValue = defaultValue;
      this.options = options;
    }
  }

  public enum RuleParamType {
    STRING,
    TEXT,
    BOOLEAN,
    INT,
    FLOAT
  }


}
