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
package org.sonarlint.intellij.analysis;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.rules.TempDirectory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.java.JavaAnalysisConfigurator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class JavaAnalysisConfiguratorTests extends AbstractSonarLintLightTests {

  private static final Path FAKE_JDK_ROOT_PATH = Paths.get("src/test/resources/fake_jdk/").toAbsolutePath();
  private static final String MY_EXPORTED_LIB_JAR = "myExportedLib.jar";
  private static final String MY_EXPORTED_TEST_LIB_JAR = "myExportedTestLib.jar";
  private static final String MY_NON_EXPORTED_LIB_JAR = "myNonExportedLib.jar";
  private static final String GUAVA_LIB_JAR = "guava.jar";
  private static final String JUNIT_LIB_JAR = "junit.jar";

  @Rule
  public TempDirectory tempDir = new TempDirectory();

  private final JavaAnalysisConfigurator underTest = new JavaAnalysisConfigurator();
  private File exportedLibInDependentModuleFile;
  private File nonExportedLibFile;
  private File guavaLibFile;
  private File junitLibFile;
  private File compilerOutputDirFile;
  private File compilerTestOutputDirFile;
  private File dependentModCompilerOutputDirFile;
  private File dependentModCompilerTestOutputDirFile;
  private File exportedLibInTestDependentModuleFile;
  private File testDependentModCompilerOutputDirFile;

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return addRtJarTo(IdeaTestUtil.getMockJdk18());
      }

      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        try {
          compilerOutputDirFile = tempDir.newFolder("compiler,OutputDir");
          compilerTestOutputDirFile = tempDir.newFolder("compilerTestOutputDir");
          // Set compiler outputs
          setCompilerOutputs(model, compilerOutputDirFile, compilerTestOutputDirFile);
          // Add some libraries
          // Production lib
          guavaLibFile = tempDir.newFile(GUAVA_LIB_JAR);
          var guavaLib = PsiTestUtil.addLibrary(model, "guava", guavaLibFile.getParent(), guavaLibFile.getName());
          final var guavaLibOrderEntry = model.findLibraryOrderEntry(guavaLib);
          guavaLibOrderEntry.setScope(DependencyScope.COMPILE);
          // Test lib
          junitLibFile = tempDir.newFile(JUNIT_LIB_JAR);
          var junitLib = PsiTestUtil.addLibrary(model, "junit", junitLibFile.getParent(), junitLibFile.getName());
          final var junitLibOrderEntry = model.findLibraryOrderEntry(junitLib);
          junitLibOrderEntry.setScope(DependencyScope.TEST);

          // Dependent module with compile scope
          var dependentModule = createModule(module.getProject(), FileUtil.join(FileUtil.getTempDirectory(), "dependent.iml"));
          final var moduleOrderEntry = model.addModuleOrderEntry(dependentModule);
          moduleOrderEntry.setScope(DependencyScope.COMPILE);
          exportedLibInDependentModuleFile = tempDir.newFile(MY_EXPORTED_LIB_JAR);
          nonExportedLibFile = tempDir.newFile(MY_NON_EXPORTED_LIB_JAR);

          ModuleRootModificationUtil.updateModel(dependentModule, dependentModel -> {
            try {
              dependentModCompilerOutputDirFile = tempDir.newFolder("depCompilerOutputDir");
              dependentModCompilerTestOutputDirFile = tempDir.newFolder("depCompilerTestOutputDir");
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
            setCompilerOutputs(dependentModel, dependentModCompilerOutputDirFile, dependentModCompilerTestOutputDirFile);

            PsiTestUtil.addLibrary(dependentModel, "myNonExportedLib", nonExportedLibFile.getParent(), nonExportedLibFile.getName());
            var myExportedLib = PsiTestUtil.addLibrary(dependentModel, "myExportedLib", exportedLibInDependentModuleFile.getParent(),
              exportedLibInDependentModuleFile.getName());
            final var libraryOrderEntry = dependentModel.findLibraryOrderEntry(myExportedLib);
            libraryOrderEntry.setExported(true);
          });

          // Dependent module with test scope
          var testDependentModule = createModule(module.getProject(), FileUtil.join(FileUtil.getTempDirectory(), "testDependent.iml"));
          final var testModuleOrderEntry = model.addModuleOrderEntry(testDependentModule);
          testModuleOrderEntry.setScope(DependencyScope.TEST);
          exportedLibInTestDependentModuleFile = tempDir.newFile(MY_EXPORTED_TEST_LIB_JAR);

          ModuleRootModificationUtil.updateModel(testDependentModule, dependentModel -> {
            try {
              testDependentModCompilerOutputDirFile = tempDir.newFolder("testDepCompilerOutputDir");
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
            setCompilerOutputs(dependentModel, testDependentModCompilerOutputDirFile, null);

            var myExportedTestLib = PsiTestUtil.addLibrary(dependentModel, "myExportedTestLib", exportedLibInTestDependentModuleFile.getParent(),
              exportedLibInTestDependentModuleFile.getName());
            final var libraryOrderEntry = dependentModel.findLibraryOrderEntry(myExportedTestLib);
            libraryOrderEntry.setExported(true);
          });
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }

    };
  }

  private static void setCompilerOutputs(@NotNull ModifiableRootModel model, File compilerOutputDirFile, @Nullable File compilerTestOutputDirFile) {
    final var compilerOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(compilerOutputDirFile.getAbsolutePath());
    model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(compilerOutputDir);
    if (compilerTestOutputDirFile != null) {
      final var compilerTestOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(compilerTestOutputDirFile.getAbsolutePath());
      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPathForTests(compilerTestOutputDir);
    }
    model.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
    model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
  }

  @Test
  public void testSourceAndTarget_with_default_target() {
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), null);

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties).contains(entry("sonar.java.source", "8"), entry("sonar.java.target", "8"));

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_9);
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties).contains(entry("sonar.java.source", "9"), entry("sonar.java.target", "9"));
  }

  @Test
  public void testSourceAndTarget_with_different_target() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), "7");
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties).contains(entry("sonar.java.source", "8"), entry("sonar.java.target", "7"));
  }

  @Test
  public void testClasspath() {
    final var props = underTest.configure(getModule(), Collections.emptyList()).extraProperties;
    assertThat(props)
      .containsEntry("sonar.java.binaries", "\"" + compilerOutputDirFile.toPath() + "\"")
      .containsEntry("sonar.java.libraries", String.join(",",
        guavaLibFile.toPath().toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/rt.jar").toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/another.jar").toString(),
        dependentModCompilerOutputDirFile.toPath().toString(),
        exportedLibInDependentModuleFile.toPath().toString()))
      .containsEntry("sonar.java.test.binaries", compilerTestOutputDirFile.toPath().toString())
      .containsEntry("sonar.java.test.libraries", String.join(",",
        "\"" + compilerOutputDirFile.toPath() + "\"",
        junitLibFile.toPath().toString(),
        guavaLibFile.toPath().toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/rt.jar").toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/another.jar").toString(),
        dependentModCompilerOutputDirFile.toPath().toString(),
        exportedLibInDependentModuleFile.toPath().toString(),
        testDependentModCompilerOutputDirFile.toPath().toString(),
        exportedLibInTestDependentModuleFile.toPath().toString()))
      .containsEntry("sonar.java.jdkHome", FAKE_JDK_ROOT_PATH.resolve("jdk1.8").toString());
  }

  private static Sdk addRtJarTo(@NotNull Sdk jdk) {
    try {
      jdk = (Sdk) jdk.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    var sdkModificator = jdk.getSdkModificator();
    sdkModificator.setHomePath(FAKE_JDK_ROOT_PATH.resolve("jdk1.8").toString());
    sdkModificator.addRoot(findJar("jdk1.8/lib/rt.jar"), OrderRootType.CLASSES);
    sdkModificator.addRoot(findJar("jdk1.8/lib/another.jar"), OrderRootType.CLASSES);
    sdkModificator.commitChanges();
    return jdk;
  }

  @NotNull
  private static VirtualFile findJar(@NotNull String name) {
    var path = FAKE_JDK_ROOT_PATH.resolve(name);
    var file = VfsTestUtil.findFileByCaseSensitivePath(path.toString());
    var jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assert jar != null : "no .jar for: " + path;
    return jar;
  }
}
