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
package org.sonarlint.intellij.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import icons.SonarLintIcons;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class SonarLintColorSettingsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
    new AttributesDescriptor("Major issue", SonarLintTextAttributes.MAJOR),
    new AttributesDescriptor("Minor issue", SonarLintTextAttributes.MINOR),
    new AttributesDescriptor("Critical issue", SonarLintTextAttributes.CRITICAL),
    new AttributesDescriptor("Blocker issue", SonarLintTextAttributes.BLOCKER),
    new AttributesDescriptor("Info issue", SonarLintTextAttributes.INFO),
    new AttributesDescriptor("Selected issue", SonarLintTextAttributes.SELECTED)
  };

  private static class DescriptorComparator implements Comparator<AttributesDescriptor> {
    @Override public int compare(AttributesDescriptor o1, AttributesDescriptor o2) {
      return o1.getDisplayName().compareTo(o2.getDisplayName());
    }
  }

  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS;

  static {
    Arrays.sort(DESCRIPTORS, new DescriptorComparator());
    // sort alphabetically by key
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new TreeMap<>();

    for (AttributesDescriptor desc : DESCRIPTORS) {
      ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put(desc.getDisplayName(), desc.getKey());
    }
  }

  @Nullable @Override public Icon getIcon() {
    return SonarLintIcons.SONARLINT;
  }

  @NotNull @Override public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull @Override public String getDemoText() {
    StringBuilder buffer = new StringBuilder();

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
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }

  @NotNull @Override public AttributesDescriptor[] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @NotNull @Override public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull @Override public String getDisplayName() {
    return "SonarLint";
  }

}
