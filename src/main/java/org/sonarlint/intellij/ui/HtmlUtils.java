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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import java.util.regex.Pattern;

public class HtmlUtils {
  private static final Pattern SPACES_BEGINNING_LINE = Pattern.compile("\n(\\p{Blank}*)");

  private HtmlUtils() {
    // utility class
  }

  /**
   * Unfortunately it looks like the default html editor kit doesn't support CSS related to white-space. Therefore,
   * all text within the pre tags doesn't wrap.
   * So we replace all 'pre' tags by 'div' tags with font monospace.
   * In the preformatted text, we replace '\n' by the 'br' tag, and all the spaces in the beginning of each line by
   * the non-breaking space 'nbsp'.
   */
  public static String fixPreformattedText(String htmlBody) {
    var builder = new StringBuilder();
    var current = 0;

    while (true) {
      var start = htmlBody.indexOf("<pre>", current);
      if (start < 0) {
        break;
      }

      var end = htmlBody.indexOf("</pre>", start);

      if (end < 0) {
        break;
      }

      builder.append(htmlBody.substring(current, start));
      builder.append("<div style=\"font-family: monospace\">");
      var preformated = htmlBody.substring(start + 5, end);

      var m = SPACES_BEGINNING_LINE.matcher(preformated);
      var previous = 0;
      while (m.find()) {
        var replacement = "<br/>" + StringUtil.repeat("&nbsp;", m.group().length());
        builder.append(preformated.substring(previous, m.start()));
        builder.append(replacement);
        previous = m.end();
      }
      builder.append(preformated.substring(previous));
      builder.append("</div>");
      current = end + 6;
    }

    builder.append(htmlBody.substring(current));
    return builder.toString();
  }
}
