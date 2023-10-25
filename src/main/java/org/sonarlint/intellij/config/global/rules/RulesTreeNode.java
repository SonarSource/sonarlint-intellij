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
package org.sonarlint.intellij.config.global.rules;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultMutableTreeNode;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

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
    private final RuleDefinitionDto details;
    private final Map<String, String> nonDefaultParams;
    private final SoftwareQuality highestQuality;
    private final ImpactSeverity highestImpact;

    public Rule(RuleDefinitionDto details, boolean activated, Map<String, String> nonDefaultParams) {
      this.details = details;
      this.activated = activated;
      this.nonDefaultParams = new HashMap<>(nonDefaultParams);
      var highestQualityImpact = details.getDefaultImpacts().entrySet().stream().max(Map.Entry.comparingByValue());
      this.highestQuality = highestQualityImpact.map(Map.Entry::getKey).orElse(null);
      this.highestImpact = highestQualityImpact.map(Map.Entry::getValue).orElse(null);
    }

    public String getKey() {
      return details.getKey();
    }

    public String getName() {
      return details.getName();
    }

    public boolean getDefaultActivation() {
      return details.isActiveByDefault();
    }

    public CleanCodeAttribute attribute() {
      return details.getCleanCodeAttribute().orElse(null);
    }

    public Map<SoftwareQuality, ImpactSeverity> impacts() {
      return details.getDefaultImpacts();
    }

    public IssueSeverity severity() {
      return details.getSeverity();
    }

    public RuleType type() {
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

    public Map<String, String> getCustomParams() {
      return nonDefaultParams;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof Rule rule) {
        return details.getKey().equals(rule.details.getKey()) && activated == rule.activated && nonDefaultParams.equals(rule.nonDefaultParams);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(details.getKey(), activated, nonDefaultParams);
    }

    public SoftwareQuality getHighestQuality() {
      return highestQuality;
    }

    public ImpactSeverity getHighestImpact() {
      return highestImpact;
    }
  }

}
