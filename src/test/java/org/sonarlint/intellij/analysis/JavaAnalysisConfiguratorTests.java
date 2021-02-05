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
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
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
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

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

  private JavaAnalysisConfigurator underTest = new JavaAnalysisConfigurator();
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
          compilerOutputDirFile = tempDir.newFolder("compilerOutputDir");
          compilerTestOutputDirFile = tempDir.newFolder("compilerTestOutputDir");
          // Set compiler outputs
          setCompilerOutputs(model, compilerOutputDirFile, compilerTestOutputDirFile);
          // Add some libraries
          // Production lib
          guavaLibFile = tempDir.newFile(GUAVA_LIB_JAR);
          Library guavaLib = PsiTestUtil.addLibrary(model, "guava", guavaLibFile.getParent(), guavaLibFile.getName());
          final LibraryOrderEntry guavaLibOrderEntry = model.findLibraryOrderEntry(guavaLib);
          guavaLibOrderEntry.setScope(DependencyScope.COMPILE);
          // Test lib
          junitLibFile = tempDir.newFile(JUNIT_LIB_JAR);
          Library junitLib = PsiTestUtil.addLibrary(model, "junit", junitLibFile.getParent(), junitLibFile.getName());
          final LibraryOrderEntry junitLibOrderEntry = model.findLibraryOrderEntry(junitLib);
          junitLibOrderEntry.setScope(DependencyScope.TEST);

          // Dependent module with compile scope
          Module dependentModule = createModule(module.getProject(), FileUtil.join(FileUtil.getTempDirectory(), "dependent.iml"));
          final ModuleOrderEntry moduleOrderEntry = model.addModuleOrderEntry(dependentModule);
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
            Library myExportedLib = PsiTestUtil.addLibrary(dependentModel, "myExportedLib", exportedLibInDependentModuleFile.getParent(),
              exportedLibInDependentModuleFile.getName());
            final LibraryOrderEntry libraryOrderEntry = dependentModel.findLibraryOrderEntry(myExportedLib);
            libraryOrderEntry.setExported(true);
          });

          // Dependent module with test scope
          Module testDependentModule = createModule(module.getProject(), FileUtil.join(FileUtil.getTempDirectory(), "testDependent.iml"));
          final ModuleOrderEntry testModuleOrderEntry = model.addModuleOrderEntry(testDependentModule);
          testModuleOrderEntry.setScope(DependencyScope.TEST);
          exportedLibInTestDependentModuleFile = tempDir.newFile(MY_EXPORTED_TEST_LIB_JAR);

          ModuleRootModificationUtil.updateModel(testDependentModule, dependentModel -> {
            try {
              testDependentModCompilerOutputDirFile = tempDir.newFolder("testDepCompilerOutputDir");
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
            setCompilerOutputs(dependentModel, testDependentModCompilerOutputDirFile, null);

            Library myExportedTestLib = PsiTestUtil.addLibrary(dependentModel, "myExportedTestLib", exportedLibInTestDependentModuleFile.getParent(),
              exportedLibInTestDependentModuleFile.getName());
            final LibraryOrderEntry libraryOrderEntry = dependentModel.findLibraryOrderEntry(myExportedTestLib);
            libraryOrderEntry.setExported(true);
          });
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }

    };
  }

  private static void setCompilerOutputs(@NotNull ModifiableRootModel model, File compilerOutputDirFile, @Nullable File compilerTestOutputDirFile) {
    final VirtualFile compilerOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(compilerOutputDirFile.getAbsolutePath());
    model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(compilerOutputDir);
    if (compilerTestOutputDirFile != null) {
      final VirtualFile compilerTestOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(compilerTestOutputDirFile.getAbsolutePath());
      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPathForTests(compilerTestOutputDir);
    }
    model.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
    model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
  }

  @Test
  public void testSourceAndTarget_with_default_target() {
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), null);

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    assertThat(underTest.configure(getModule())).contains(entry("sonar.java.source", "8"), entry("sonar.java.target", "8"));

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_9);
    assertThat(underTest.configure(getModule())).contains(entry("sonar.java.source", "9"), entry("sonar.java.target", "9"));
  }

  @Test
  public void testSourceAndTarget_with_different_target() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), "7");
    assertThat(underTest.configure(getModule())).contains(entry("sonar.java.source", "8"), entry("sonar.java.target", "7"));
  }

  @Test
  public void testClasspath() {
    final Map<String, String> props = underTest.configure(getModule());
    assertThat(Stream.of(props.get("sonar.java.binaries").split(",")).map(Paths::get))
      .containsExactly(compilerOutputDirFile.toPath());
    assertThat(Stream.of(props.get("sonar.java.libraries").split(",")).map(Paths::get))
      .containsExactly(
        guavaLibFile.toPath(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/rt.jar"),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/another.jar"),
        dependentModCompilerOutputDirFile.toPath(),
        exportedLibInDependentModuleFile.toPath());
    assertThat(Stream.of(props.get("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsExactly(compilerTestOutputDirFile.toPath());
    assertThat(Stream.of(props.get("sonar.java.test.libraries").split(",")).map(Paths::get))
      .containsExactly(
        compilerOutputDirFile.toPath(),
        junitLibFile.toPath(),
        guavaLibFile.toPath(),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/rt.jar"),
        FAKE_JDK_ROOT_PATH.resolve("jdk1.8/lib/another.jar"),
        dependentModCompilerOutputDirFile.toPath(),
        exportedLibInDependentModuleFile.toPath(),
        testDependentModCompilerOutputDirFile.toPath(),
        exportedLibInTestDependentModuleFile.toPath());
    assertThat(Paths.get(props.get("sonar.java.jdkHome"))).isEqualTo(FAKE_JDK_ROOT_PATH.resolve("jdk1.8"));
  }

  private static Sdk addRtJarTo(@NotNull Sdk jdk) {
    try {
      jdk = (Sdk) jdk.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.setHomePath(FAKE_JDK_ROOT_PATH.resolve("jdk1.8").toString());
    sdkModificator.addRoot(findJar("jdk1.8/lib/rt.jar"), OrderRootType.CLASSES);
    sdkModificator.addRoot(findJar("jdk1.8/lib/another.jar"), OrderRootType.CLASSES);
    sdkModificator.commitChanges();
    return jdk;
  }

  @NotNull
  private static VirtualFile findJar(@NotNull String name) {
    Path path = FAKE_JDK_ROOT_PATH.resolve(name);
    VirtualFile file = VfsTestUtil.findFileByCaseSensitivePath(path.toString());
    VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assert jar != null : "no .jar for: " + path;
    return jar;
  }
}
