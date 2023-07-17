/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.java.JavaAnalysisConfigurator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class JavaAnalysisConfiguratorTests extends AbstractSonarLintLightTests {

  private static final Path FAKE_JDK_ROOT_PATH = Paths.get("src/test/resources/fake_jdk/").toAbsolutePath();
  private static final String MY_EXPORTED_LIB_JAR = "myExportedLib.jar";
  private static final String MY_EXPORTED_TEST_LIB_JAR = "myExportedTestLib.jar";
  private static final String MY_NON_EXPORTED_LIB_JAR = "myNonExportedLib.jar";
  private static final String GUAVA_LIB_JAR = "guava.jar";
  private static final String JUNIT_LIB_JAR = "junit.jar";

  @TempDir
  Path tempDirPath;

  private final JavaAnalysisConfigurator underTest = new JavaAnalysisConfigurator();
  private Path exportedLibInDependentModulePath;
  private Path nonExportedLibPath;
  private Path guavaLibPath;
  private Path junitLibPath;
  private Path compilerOutputDirPath;
  private Path compilerTestOutputDirPath;
  private Path dependentModCompilerOutputDirPath;
  private Path dependentModCompilerTestOutputDirPath;
  private Path exportedLibInTestDependentModulePath;
  private Path testDependentModCompilerOutputDirPath;

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
        compilerOutputDirPath = createDirectory(tempDirPath.resolve("compiler,OutputDir"));
        compilerTestOutputDirPath = createDirectory(tempDirPath.resolve("compilerTestOutputDir"));
        // Set compiler outputs
        setCompilerOutputs(model, compilerOutputDirPath, compilerTestOutputDirPath);
        // Add some libraries
        // Production lib
        guavaLibPath = createFile(tempDirPath.resolve(GUAVA_LIB_JAR));
        addLibrary(guavaLibPath, "guava", model, DependencyScope.COMPILE, false);
        // Test lib
        junitLibPath = createFile(tempDirPath.resolve(JUNIT_LIB_JAR));
        addLibrary(junitLibPath, "junit", model, DependencyScope.TEST, false);

        // Dependent module with compile scope
        var dependentModule = createModule(module.getProject(), FileUtil.join(FileUtil.getTempDirectory(), "dependent.iml"));
        final var moduleOrderEntry = model.addModuleOrderEntry(dependentModule);
        moduleOrderEntry.setScope(DependencyScope.COMPILE);
        exportedLibInDependentModulePath = createFile(tempDirPath.resolve(MY_EXPORTED_LIB_JAR));
        nonExportedLibPath = createFile(tempDirPath.resolve(MY_NON_EXPORTED_LIB_JAR));

        ModuleRootModificationUtil.updateModel(dependentModule, dependentModel -> {
          dependentModCompilerOutputDirPath = createDirectory(tempDirPath.resolve("depCompilerOutputDir"));
          dependentModCompilerTestOutputDirPath = createDirectory(tempDirPath.resolve("depCompilerTestOutputDir"));
          setCompilerOutputs(dependentModel, dependentModCompilerOutputDirPath, dependentModCompilerTestOutputDirPath);

          addLibrary(nonExportedLibPath, "myNonExportedLib", dependentModel, DependencyScope.COMPILE, false);
          addLibrary(exportedLibInDependentModulePath, "myExportedLib", dependentModel, DependencyScope.COMPILE, true);
        });

        // Dependent module with test scope
        var testDependentModule = createModule(module.getProject(), FileUtil.join(FileUtil.getTempDirectory(), "testDependent.iml"));
        final var testModuleOrderEntry = model.addModuleOrderEntry(testDependentModule);
        testModuleOrderEntry.setScope(DependencyScope.TEST);
        exportedLibInTestDependentModulePath = createFile(tempDirPath.resolve(MY_EXPORTED_TEST_LIB_JAR));

        ModuleRootModificationUtil.updateModel(testDependentModule, dependentModel -> {
          testDependentModCompilerOutputDirPath = createDirectory(tempDirPath.resolve("testDepCompilerOutputDir"));
          setCompilerOutputs(dependentModel, testDependentModCompilerOutputDirPath, null);

          addLibrary(exportedLibInTestDependentModulePath, "myExportedTestLib", dependentModel, DependencyScope.COMPILE, true);
        });
      }

    };
  }

  private static Path createDirectory(Path path) {
    try {
      return Files.createDirectories(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Path createFile(Path path) {
    try {
      Files.createDirectories(path.getParent());
      return Files.createFile(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void addLibrary(Path libraryPath, String libraryName, ModifiableRootModel model, DependencyScope scope, boolean exported) {
    var lib = PsiTestUtil.addLibrary(model, libraryName, libraryPath.getParent().toString(), libraryPath.getFileName().toString());
    // workaround as ModifiableRootModel#findLibraryOrderEntry does not work
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        var libraryEntry = (LibraryOrderEntry) entry;
        if (libraryName.equals(libraryEntry.getLibraryName())) {
          libraryEntry.setScope(scope);
          libraryEntry.setExported(exported);
        }
      }
    }
  }

  private static void setCompilerOutputs(@NotNull ModifiableRootModel model, Path compilerOutputDirPath, @Nullable Path compilerTestOutputDirPath) {
    final var compilerOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(compilerOutputDirPath.toString());
    model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(compilerOutputDir);
    if (compilerTestOutputDirPath != null) {
      final var compilerTestOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(compilerTestOutputDirPath.toString());
      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPathForTests(compilerTestOutputDir);
    }
    model.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
    model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
  }

  @Test
  void testSourceAndTarget_with_default_target() {
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), null);

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties).contains(entry("sonar.java.source", "8"), entry("sonar.java.target", "8"));

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_9);
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties).contains(entry("sonar.java.source", "9"), entry("sonar.java.target", "9"));
  }

  /** SLI-936: Property "sonar.java.enablePreview" not set automatically anymore but based on module configuration */
  @Test
  void test_enablePreview() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_17);
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties)
            .contains(entry("sonar.java.enablePreview", "false"));

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_17_PREVIEW);
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties)
            .contains(entry("sonar.java.enablePreview", "true"));
  }

  @Test
  void testSourceAndTarget_with_different_target() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), "7");
    assertThat(underTest.configure(getModule(), Collections.emptyList()).extraProperties).contains(entry("sonar.java.source", "8"), entry("sonar.java.target", "7"));
  }

  @Test
  void testClasspath() throws IOException {
    final var props = underTest.configure(getModule(), Collections.emptyList()).extraProperties;
    assertThat(props)
      .containsEntry("sonar.java.binaries", "\"" + compilerOutputDirPath.toRealPath() + "\"")
      .containsEntry("sonar.java.libraries", String.join(",",
        guavaLibPath.toRealPath().toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/rt.jar").toRealPath().toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/another.jar").toRealPath().toString(),
        dependentModCompilerOutputDirPath.toRealPath().toString(),
        exportedLibInDependentModulePath.toRealPath().toString()))
      .containsEntry("sonar.java.test.binaries", compilerTestOutputDirPath.toRealPath().toString())
      .containsEntry("sonar.java.test.libraries", String.join(",",
        "\"" + compilerOutputDirPath.toRealPath() + "\"",
        junitLibPath.toRealPath().toString(),
        guavaLibPath.toRealPath().toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/rt.jar").toRealPath().toString(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/another.jar").toRealPath().toString(),
        dependentModCompilerOutputDirPath.toRealPath().toString(),
        exportedLibInDependentModulePath.toRealPath().toString(),
        testDependentModCompilerOutputDirPath.toRealPath().toString(),
        exportedLibInTestDependentModulePath.toRealPath().toString()))
      .containsEntry("sonar.java.jdkHome", FAKE_JDK_ROOT_PATH.resolve("jdk1.8").toRealPath().toString());
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
