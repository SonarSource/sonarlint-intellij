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
package org.sonarlint.intellij.clion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompiler;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public class CLionConfiguration {

  private CLionConfiguration() {
  }

  @Nullable
  private static OCLanguageKind getDeclaredLanguageKind(Project project, VirtualFile sourceOrHeaderFile) {
    String fileName = sourceOrHeaderFile.getName();
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(project, sourceOrHeaderFile);
    }

    if (OCFileTypeHelpers.isHeaderFile(fileName)) {
      return getLanguageKind(project, getSourceFileForHeaderFile(project, sourceOrHeaderFile));
    }

    return null;
  }

  private static OCLanguageKind getLanguageKind(Project project, @Nullable VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : CLanguageKind.CPP;
  }

  @Nullable
  private static VirtualFile getSourceFileForHeaderFile(Project project, VirtualFile headerFile) {
    List<VirtualFile> roots = new ArrayList<>(OCImportGraph.getInstance(project).getAllHeaderRoots(headerFile));

    final String headerNameWithoutExtension = headerFile.getNameWithoutExtension();
    for (VirtualFile root : roots) {
      if (root.getNameWithoutExtension().equals(headerNameWithoutExtension)) {
        return root;
      }
    }
    return null;
  }

  static void debugAllFilesConfiguration(SonarLintConsole console, Module module, Collection<VirtualFile> filesToAnalyze, OCResolveConfiguration configuration) {
    filesToAnalyze.forEach(virtualFile -> {
      console.debug("##### begin");
      console.debug("virtualFile: " + virtualFile.getPath());
      OCLanguageKind language = configuration.getDeclaredLanguageKind(virtualFile);
      console.debug("language1: " + language);

      if (language == null) {
        language = getDeclaredLanguageKind(module.getProject(), virtualFile);
        console.debug("language2: " + language);
      }

      if (language != null) {
        console.debug(language.getDisplayName());
        OCCompilerSettings compilerSettings = configuration.getCompilerSettings(language, virtualFile);
        debugAllSettings(console, language, compilerSettings);
      }
      console.debug("##### end");
    });
  }

  private static void debugAllSettings(SonarLintConsole console, OCLanguageKind language, OCCompilerSettings compilerSettings) {
    if (language != null) {
      console.debug("language: " + language.getDisplayName());
    }
    File compilerExecutable = compilerSettings.getCompilerExecutable();
    console.debug("compilerExecutable: " + compilerExecutable);
    OCCompilerKind compilerKind = compilerSettings.getCompilerKind();
    if (compilerKind != null) {
      console.debug("compilerKind: " + compilerKind.getDisplayName());
    }
    CidrCompilerSwitches switches = compilerSettings.getCompilerSwitches();
    if (switches != null) {
      console.debug("switches:");
      for (String s : switches.getList(CidrCompilerSwitches.Format.RAW)) {
        console.debug(s);
      }
    }
    if (language != null) {
      console.debug("GCCCompiler.getLanguageOption: " + GCCCompiler.getLanguageOption(language));
    }
    console.debug("headerSearchRoots:");
    compilerSettings.getHeadersSearchRoots().getAllRoots().forEach(headersSearchRoot -> console.debug(headersSearchRoot.toString()));

    console.debug("getHeadersSearchPaths:");
    compilerSettings.getHeadersSearchPaths().forEach(headersSearchPath -> console.debug(headersSearchPath.getPath()));

    console.debug("macros:");
    compilerSettings.getPreprocessorDefines().forEach(console::debug);

    console.debug("implicitIncludes: ");
    compilerSettings.getImplicitIncludes().forEach(virtualFile1 -> console.debug(virtualFile1.getCanonicalPath()));
  }

  static class BuildWrapperJsonFactory {
    private static final String COMPILER = "clang";

    static String create(Project project, OCResolveConfiguration configuration, Collection<VirtualFile> files) {
      StringBuilder builder = new StringBuilder();
      builder.append("{"
        + "\"version\":0,"
        + "\"captures\":[");

      boolean first = true;
      for (VirtualFile virtualFile : files) {
        OCLanguageKind language = getDeclaredLanguageKind(project, virtualFile);
        if (language == null) {
          continue;
        }
        if (first) {
          first = false;
        } else {
          builder.append(",");
        }
        OCCompilerSettings compilerSettings = configuration.getCompilerSettings(language, virtualFile);
        writeFile(builder, compilerSettings, virtualFile);
      }


      builder.append("]}");
      return builder.toString();
    }

    private static void writeFile(StringBuilder builder, OCCompilerSettings compilerSettings, VirtualFile virtualFile) {
      String quotedCompilerExecutable = quote(compilerSettings.getCompilerExecutable().getAbsolutePath());
      builder.append("{")
        .append("\"compiler\":\"" + COMPILER + "\",")
        .append("\"cwd\":" + quote(compilerSettings.getCompilerWorkingDir().getAbsolutePath()) + ",")
        .append("\"executable\":" + quotedCompilerExecutable + ",")
        .append("\"env\":[");
      boolean first = true;
      for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
        if (first) {
          first = false;
        } else {
          builder.append(",");
        }

        builder.append(quote(entry.getKey() + "=" + entry.getValue()));
      }
      builder.append("],")
        .append("\"cmd\":[")
        .append(quotedCompilerExecutable)
        .append("," + quote(virtualFile.getCanonicalPath()) + "");
      compilerSettings.getCompilerSwitches().getList(CidrCompilerSwitches.Format.RAW).forEach(s -> builder.append(",").append(quote(s)));
      builder.append("]}");
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
}
