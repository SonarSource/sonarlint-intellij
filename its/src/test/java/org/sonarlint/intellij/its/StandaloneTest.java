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
package org.sonarlint.intellij.its;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class StandaloneTest {
  private static final Path PLUGINS_FOLDER = Paths.get("../src/main/resources/plugins/");
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  private static StandaloneSonarLintEngine sonarlintEngine;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugins(getPlugins(PLUGINS_FOLDER).toArray(new URL[0]))
      .setSonarLintUserHome(temp.newFolder("sonarlint").toPath())
      .setLogOutput((msg, level) -> System.out.println("[" + level + "]: " + msg))
      .build();
    sonarlintEngine = new StandaloneSonarLintEngineImpl(config);
    baseDir = temp.newFolder();
  }

  private static List<URL> getPlugins(Path folder) throws IOException {
    return Files.list(folder)
      .filter(p -> p.getFileName().toString().endsWith(".jar"))
      .peek(p -> System.out.println("Plugin: " + p.getFileName()))
      .map(p -> {
        try {
          return p.toUri().toURL();
        } catch (MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      })
      .collect(Collectors.toList());
  }

  @Test
  public void simpleJava() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}");
    List<Issue> issues = analyze(inputFile);

    assertThat(issues)
      .extracting("ruleKey", "startLine", "inputFile.path", "severity")
      .containsOnly(
        tuple("java:S106", 4, inputFile.getPath(), "MAJOR"),
        tuple("java:S1220", null, inputFile.getPath(), "MINOR"),
        tuple("java:S1481", 3, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavascript() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.js",
      "for (;;) {}");
    List<Issue> issues = analyze(inputFile);

    assertThat(issues)
      .extracting("ruleKey", "startLine", "inputFile.path", "severity")
      .containsOnly(
        tuple("javascript:S2189", 1, inputFile.getPath(), "BLOCKER"),
        tuple("javascript:S108", 1, inputFile.getPath(), "MAJOR"),
        tuple("javascript:S1264", 1, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simplePython() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.py", "print \"Hello world!\"");
    List<Issue> issues = analyze(inputFile);

    assertThat(issues)
      .extracting("ruleKey", "startLine", "inputFile.path", "severity")
      .containsOnly(
        tuple("python:PrintStatementUsage", 1, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simplePHP() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.php", "<?php"
      + "class PhpUnderControl_Example_Math {}"
      + "?>");
    List<Issue> issues = analyze(inputFile);

    assertThat(issues)
      .extracting("ruleKey", "startLine", "inputFile.path", "severity")
      .containsOnly(
        tuple("php:S101", 1, inputFile.getPath(), "MINOR"));
  }

  private static List<Issue> analyze(ClientInputFile inputFile) throws IOException  {
    List<Issue> issues = new ArrayList<>();
    sonarlintEngine.analyze(StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build(), issues::add, null, null);
    return issues;
  }

  private static ClientInputFile prepareInputFile(String fileName, String content) throws IOException {
    Path testFile = temp.newFile(fileName).toPath();
    System.out.println("Writing: " + testFile);
    Files.write(testFile, content.getBytes(StandardCharsets.UTF_8));
    return new TestClientInputFile(testFile, fileName,false, StandardCharsets.UTF_8);
  }
}
