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
package org.sonarlint.intellij.ui.ruledescription;

import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;

import java.awt.Image;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import javax.swing.Icon;
import javax.swing.text.Element;
import javax.swing.text.html.ImageView;

import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.util.SonarLintUtils;

public class RuleDescriptionImageView extends ImageView {
  public RuleDescriptionImageView(Element elem) {
    super(elem);
    cacheImage(getImageURL());
  }

  private void cacheImage(@Nullable URL url) {
    if (url == null) {
      return;
    }
    String path = url.getPath();
    if (path == null) {
      return;
    }

    Icon icon;
    if (path.startsWith("/severity/")) {
      String severity = url.getPath().substring(10);
      icon = SonarLintIcons.severity(severity);
    } else if (path.startsWith("/type/")) {
      String type = url.getPath().substring(6);
      icon = SonarLintIcons.type(type);
    } else {
      return;
    }

    // in presentation mode we don't want huge icons
    if (JBUI.isUsrHiDPI()) {
      icon = IconUtil.scale(icon, null, 0.5f);
    }
    Dictionary<URL, Image> cache = (Dictionary<URL, Image>) getDocument().getProperty("imageCache");
    if (cache == null) {
      cache = new Hashtable<>();
    }
    cache.put(url, SonarLintUtils.iconToImage(icon));
    getDocument().putProperty("imageCache", cache);
  }
}
