package org.sonar.ide.intellij.inspection;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class SonarQubeInspectionExtensionsFactory extends InspectionExtensionsFactory {


  @Override
  public GlobalInspectionContextExtension createGlobalInspectionContextExtension() {
    return new SonarQubeInspectionContext();
  }

  @Nullable
  @Override
  public RefManagerExtension createRefManagerExtension(RefManager refManager) {
    return null;
  }

  @Nullable
  @Override
  public HTMLComposerExtension createHTMLComposerExtension(HTMLComposer composer) {
    return null;
  }

  @Override
  public boolean isToCheckMember(PsiElement element, String id) {
    return false;
  }

  @Nullable
  @Override
  public String getSuppressedInspectionIdsIn(PsiElement element) {
    return null;
  }

  @Override
  public boolean isProjectConfiguredToRunInspections(Project project, boolean online) {
    return true;
  }
}
