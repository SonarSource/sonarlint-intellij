/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.errorsubmitter;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class BlameSonarSourceTests {

    private BlameSonarSource underTest = new BlameSonarSource();

    @Test
    void testShortDescription() {
        var url = underTest.getReportWithBodyUrl("a");
        assertThat(url).hasSizeLessThanOrEqualTo(BlameSonarSource.MAX_URI_LENGTH).isEqualTo("https://community.sonarsource.com/new-topic?title=Error+in+SonarLint+for+IntelliJ&category_id=6&tags=sonarlint,intellij&body=a");
    }

    @Test
    void emptyBodyIfSingleLineTooLong() {
        var url = underTest.getReportWithBodyUrl(StringUtil.repeat("a", 5000));
        assertThat(url).hasSizeLessThanOrEqualTo(BlameSonarSource.MAX_URI_LENGTH).isEqualTo("https://community.sonarsource.com/new-topic?title=Error+in+SonarLint+for+IntelliJ&category_id=6&tags=sonarlint,intellij&body=");
    }

    @Test
    void truncateOnNewLineIfDescriptionTooLong() {
        var url = underTest.getReportWithBodyUrl(StringUtil.repeat("1234567\n", 188));
        assertThat(url).hasSizeLessThanOrEqualTo(BlameSonarSource.MAX_URI_LENGTH).isEqualTo("https://community.sonarsource.com/new-topic?title=Error+in+SonarLint+for+IntelliJ&category_id=6&tags=sonarlint,intellij&body=" + StringUtil.repeat("1234567%0A", 186) + "1234567");
    }

    @Test
    void testAbbreviateStackTrace() {
        var throwableText = "java.lang.Throwable: class com.intellij.openapi.roots.ProjectRootManager it is a service, use getService instead of getComponent\n" +
                "\tat com.intellij.openapi.diagnostic.Logger.error(Logger.java:182)\n" +
                "\tat com.intellij.serviceContainer.ComponentManagerImpl.getComponent(ComponentManagerImpl.kt:549)\n" +
                "\tat org.sonarsource.sonarlint.core.container.module.SonarLintModuleFileSystem.files(SonarLintModuleFileSystem.java:39)\n" +
                "\tat org.sonar.plugins.python.indexer.SonarLintPythonIndexer.getInputFiles(SonarLintPythonIndexer.java:82)\n" +
                "\tat org.sonarlint.intellij.util.SonarLintUtils.get(SonarLintUtils.java:83)\n" +
                "\tat org.sonarlint.intellij.actions.AbstractSonarAction.update(AbstractSonarAction.java:54)\n" +
                "\tat com.intellij.openapi.actionSystem.ex.ActionUtil.lambda$performDumbAwareUpdate$0(ActionUtil.java:130)\n" +
                "\tat com.intellij.openapi.actionSystem.impl.ActionUpdater.lambda$callAction$9(ActionUpdater.java:182)\n" +
                "\tat com.intellij.openapi.progress.ProgressManager.lambda$runProcess$0(ProgressManager.java:57)\n" +
                "\tat com.intellij.openapi.progress.impl.CoreProgressManager.lambda$runProcess$2(CoreProgressManager.java:183)\n" +
                "\tat com.intellij.openapi.progress.ProgressManager.runProcess(ProgressManager.java:57)\n" +
                "\tat com.intellij.openapi.actionSystem.impl.ActionUpdater.lambda$callAction$10(ActionUpdater.java:180)\n" +
                "\tat com.intellij.openapi.actionSystem.impl.ActionUpdateEdtExecutor.lambda$computeOnEdt$0(ActionUpdateEdtExecutor.java:45)\n" +
                "\tat com.intellij.openapi.application.TransactionGuardImpl$2.run(TransactionGuardImpl.java:199)\n" +
                "\tat com.intellij.openapi.application.impl.ApplicationImpl.runIntendedWriteActionOnCurrentThread(ApplicationImpl.java:794)\n" +
                "\tat com.intellij.openapi.application.impl.FlushQueue$FlushNow.run(FlushQueue.java:189)\n" +
                "\tat java.desktop/java.awt.EventQueue.dispatchEventImpl(EventQueue.java:776)\n" +
                "\tat java.desktop/java.awt.EventQueue$4.run(EventQueue.java:721)\n" +
                "\tat java.base/java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(ProtectionDomain.java:85)\n" +
                "\tat java.desktop/java.awt.EventQueue.dispatchEvent(EventQueue.java:746)";

        var result = BlameSonarSource.abbreviate(throwableText);

        assertThat(throwableText).hasSize(2036);
        assertThat(result).hasSize(1813).isEqualTo("java.lang.Throwable: class c.ij.oa.roots.ProjectRootManager it is a service, use getService instead of getComponent\n" +
                "\tat c.ij.oa.diagnostic.Logger.error(Logger.java:182)\n" +
                "\tat c.ij.serviceContainer.ComponentManagerImpl.getComponent(ComponentManagerImpl.kt:549)\n" +
                "\tat o.ss.sl.core.container.module.SonarLintModuleFileSystem.files(SonarLintModuleFileSystem.java:39)\n" +
                "\tat o.s.pl.python.indexer.SonarLintPythonIndexer.getInputFiles(SonarLintPythonIndexer.java:82)\n" +
                "\tat o.sl.ij.util.SonarLintUtils.get(SonarLintUtils.java:83)\n" +
                "\tat o.sl.ij.actions.AbstractSonarAction.update(AbstractSonarAction.java:54)\n" +
                "\tat c.ij.oa.actionSystem.ex.ActionUtil.lambda$performDumbAwareUpdate$0(ActionUtil.java:130)\n" +
                "\tat c.ij.oa.actionSystem.impl.ActionUpdater.lambda$callAction$9(ActionUpdater.java:182)\n" +
                "\tat c.ij.oa.progress.ProgressManager.lambda$runProcess$0(ProgressManager.java:57)\n" +
                "\tat c.ij.oa.progress.impl.CoreProgressManager.lambda$runProcess$2(CoreProgressManager.java:183)\n" +
                "\tat c.ij.oa.progress.ProgressManager.runProcess(ProgressManager.java:57)\n" +
                "\tat c.ij.oa.actionSystem.impl.ActionUpdater.lambda$callAction$10(ActionUpdater.java:180)\n" +
                "\tat c.ij.oa.actionSystem.impl.ActionUpdateEdtExecutor.lambda$computeOnEdt$0(ActionUpdateEdtExecutor.java:45)\n" +
                "\tat c.ij.oa.application.TransactionGuardImpl$2.run(TransactionGuardImpl.java:199)\n" +
                "\tat c.ij.oa.application.impl.ApplicationImpl.runIntendedWriteActionOnCurrentThread(ApplicationImpl.java:794)\n" +
                "\tat c.ij.oa.application.impl.FlushQueue$FlushNow.run(FlushQueue.java:189)\n" +
                "\tat java.desktop/java.awt.EventQueue.dispatchEventImpl(EventQueue.java:776)\n" +
                "\tat java.desktop/java.awt.EventQueue$4.run(EventQueue.java:721)\n" +
                "\tat java.base/java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(ProtectionDomain.java:85)\n" +
                "\tat java.desktop/java.awt.EventQueue.dispatchEvent(EventQueue.java:746)");
    }
}
