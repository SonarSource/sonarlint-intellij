/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintProjectSettingsTests {
  
  @Test
  void test_verbose_enabled() {
    var settings = new SonarLintProjectSettings();
    
    assertThat(settings.isVerboseEnabled()).isFalse();
    
    settings.setVerboseEnabled(true);
    assertThat(settings.isVerboseEnabled()).isTrue();
    
    settings.setVerboseEnabled(false);
    assertThat(settings.isVerboseEnabled()).isFalse();
  }

  @Test
  void test_verbose_enabled_with_system_property() {
    var settings = new SonarLintProjectSettings();
    
    try {
      System.setProperty("sonarlint.logs.verbose", "true");
      assertThat(settings.isVerboseEnabled()).isTrue();
      
      System.setProperty("sonarlint.logs.verbose", "false");
      assertThat(settings.isVerboseEnabled()).isFalse();
      
      System.setProperty("sonarlint.logs.verbose", "invalid");
      assertThat(settings.isVerboseEnabled()).isFalse();
    } finally {
      System.clearProperty("sonarlint.logs.verbose");
    }
  }

  @Test
  void test_binding_settings() {
    var settings = new SonarLintProjectSettings();
    
    assertThat(settings.isBindingEnabled()).isFalse();
    assertThat(settings.getProjectKey()).isNull();
    assertThat(settings.getConnectionName()).isNull();
    
    settings.setBindingEnabled(true);
    assertThat(settings.isBindingEnabled()).isTrue();
    
    settings.setProjectKey("test-project");
    assertThat(settings.getProjectKey()).isEqualTo("test-project");
    
    settings.setConnectionName("test-connection");
    assertThat(settings.getConnectionName()).isEqualTo("test-connection");
  }

  @Test
  void test_binding_suggestions() {
    var settings = new SonarLintProjectSettings();
    
    assertThat(settings.isBindingSuggestionsEnabled()).isTrue();
    
    settings.setBindingSuggestionsEnabled(false);
    assertThat(settings.isBindingSuggestionsEnabled()).isFalse();
    
    settings.setBindingSuggestionsEnabled(true);
    assertThat(settings.isBindingSuggestionsEnabled()).isTrue();
  }

  @Test
  void test_additional_properties() {
    var settings = new SonarLintProjectSettings();
    
    assertThat(settings.getAdditionalProperties()).isEmpty();
    
    var properties = Map.of("key1", "value1", "key2", "value2");
    settings.setAdditionalProperties(properties);
    assertThat(settings.getAdditionalProperties()).containsExactlyEntriesOf(properties);
    
    properties = Map.of("key3", "value3");
    settings.setAdditionalProperties(properties);
    assertThat(settings.getAdditionalProperties()).containsExactlyEntriesOf(properties);
  }

  @Test
  void test_file_exclusions() {
    var settings = new SonarLintProjectSettings();
    
    assertThat(settings.getFileExclusions()).isEmpty();
    
    var exclusions = List.of("*.log", "*.tmp");
    settings.setFileExclusions(exclusions);
    assertThat(settings.getFileExclusions()).containsExactlyElementsOf(exclusions);
    
    exclusions = List.of("*.bak");
    settings.setFileExclusions(exclusions);
    assertThat(settings.getFileExclusions()).containsExactlyElementsOf(exclusions);
  }

  @Test
  void test_is_bound() {
    var settings = new SonarLintProjectSettings();
    
    assertThat(settings.isBound()).isFalse();
    
    settings.setBindingEnabled(true);
    assertThat(settings.isBound()).isFalse();
    
    settings.setProjectKey("test-project");
    assertThat(settings.isBound()).isFalse();
    
    settings.setProjectKey(null);
    settings.setConnectionName("test-connection");
    assertThat(settings.isBound()).isFalse();
    
    settings.setProjectKey("test-project");
    assertThat(settings.isBound()).isTrue();
  }

}
