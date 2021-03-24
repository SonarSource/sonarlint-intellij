package org.sonarlint.intellij.clion;

import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;

import javax.annotation.Nullable;

public class BuildWrapperJsonGenerator {
  private final StringBuilder builder;
  private boolean first = true;

  public BuildWrapperJsonGenerator() {
    builder = new StringBuilder()
      .append("{"
        + "\"version\":0,"
        + "\"captures\":[");
  }

  public BuildWrapperJsonGenerator addRequest(AnalyzerConfiguration.Request request) {
    if (first) {
      first = false;
    } else {
      builder.append(",");
    }
    appendEntry(request);
    return this;
  }

  private void appendEntry(AnalyzerConfiguration.Request request) {
    OCCompilerSettings compilerSettings = request.compilerSettings;
    String quotedCompilerExecutable = quote(compilerSettings.getCompilerExecutable().getAbsolutePath());
    builder.append("{")
      .append("\"compiler\":\"")
      .append(request.compiler)
      .append("\",")
      .append("\"cwd\":" + quote(compilerSettings.getCompilerWorkingDir().getAbsolutePath()) + ",")
      .append("\"executable\":")
      .append(quotedCompilerExecutable)
      .append(",\"cmd\":[")
      .append(quotedCompilerExecutable)
      .append("," + quote(request.virtualFile.getCanonicalPath()));
    compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW).forEach(s -> builder.append(",").append(quote(s)));
    builder.append("]}");
  }

  public String build() {
    return builder.append("]}").toString();
  }

  private static String quote(@Nullable String string) {
    if (string == null || string.length() == 0) {
      return "\"\"";
    }

    char c;
    int i;
    int len = string.length();
    StringBuilder sb = new StringBuilder(len + 4);
    String t;

    sb.append('"');
    for (i = 0; i < len; i += 1) {
      c = string.charAt(i);
      switch (c) {
        case '\\':
        case '"':
          sb.append('\\');
          sb.append(c);
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\r':
          sb.append("\\r");
          break;
        default:
          if (c < ' ') {
            t = "000" + Integer.toHexString(c);
            sb.append("\\u" + t.substring(t.length() - 4));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
