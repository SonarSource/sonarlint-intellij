/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.util;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;
import javax.swing.Icon;
import javax.swing.ImageIcon;

@ThreadSafe
public class ResourceLoader {
  private static Map<String, Icon> iconCache = new ConcurrentHashMap<>();

  private ResourceLoader() {
    // only static
  }

  public static Icon getIcon(String name) throws IOException {
    // keep it lock free even if we might load it initially several times
    Icon icon = iconCache.get(name);

    if (icon != null) {
      return icon;
    }

    String resource = "/images/" + name;
    InputStream stream = ResourceLoader.class.getResourceAsStream(resource);
    if (stream == null) {
      throw new IOException("Couldn't find resource: " + resource);
    }
    icon = new ImageIcon(ByteStreams.toByteArray(stream));
    iconCache.put(name, icon);
    return icon;
  }

  public static Icon getSeverityIcon(String severity) throws IOException {
    Preconditions.checkNotNull(severity);
    return getIcon("severity/" + severity.toLowerCase() + ".png");
  }
}
