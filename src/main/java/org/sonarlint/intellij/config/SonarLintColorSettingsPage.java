/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarLintIcons;

public class SonarLintColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
    new AttributesDescriptor("Blocker issue", SonarLintTextAttributes.BLOCKER),
    new AttributesDescriptor("High impact issue", SonarLintTextAttributes.HIGH),
    new AttributesDescriptor("Medium impact issue", SonarLintTextAttributes.MEDIUM),
    new AttributesDescriptor("Low impact issue", SonarLintTextAttributes.LOW),
    new AttributesDescriptor("Info issue", SonarLintTextAttributes.INFO),
    new AttributesDescriptor("Old issue", SonarLintTextAttributes.OLD_CODE),
    new AttributesDescriptor("Selected issue", SonarLintTextAttributes.SELECTED)
  };

  private static class DescriptorComparator implements Comparator<AttributesDescriptor> {
    @Override public int compare(AttributesDescriptor o1, AttributesDescriptor o2) {
      return o1.getDisplayName().compareTo(o2.getDisplayName());
    }
  }

  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new TreeMap<>();

  @Nullable @Override public Icon getIcon() {
    return SonarLintIcons.SONARQUBE_FOR_INTELLIJ;
  }

  @NotNull @Override public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull @Override public String getDemoText() {
    var buffer = new StringBuilder();

    for (AttributesDescriptor desc : DESCRIPTORS) {
      buffer
        .append("  <")
        .append(desc.getDisplayName())
        .append(">")
        .append(desc.getDisplayName())
        .append("</")
        .append(desc.getDisplayName())
        .append(">")
        .append("\n");
    }
    buffer.setLength(buffer.length() - 1);
    return buffer.toString();
  }

  @Nullable @Override public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    if (ADDITIONAL_HIGHLIGHT_DESCRIPTORS.isEmpty()) {
      // sort alphabetically by key
      Arrays.sort(DESCRIPTORS, new DescriptorComparator());
      for (AttributesDescriptor desc : DESCRIPTORS) {
        ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put(desc.getDisplayName(), desc.getKey());
      }
    }
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }

  @NotNull
  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull @Override public String getDisplayName() {
    return "SonarQube for IntelliJ";
  }

}
