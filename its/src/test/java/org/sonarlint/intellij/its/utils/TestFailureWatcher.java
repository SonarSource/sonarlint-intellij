/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.its.utils;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Java bridge for {@link TestWatcher#testFailed}.
 * CLion 2024.2's ITS compile classpath presents {@code cause} as a non-null {@code Throwable}
 * (IDE test-framework JUnit 5 on the same classpath as locked junit-jupiter-api 6.1.1),
 * so a Kotlin {@code Throwable?} override does not match. Java does not encode that
 * nullability difference.
 */
public abstract class TestFailureWatcher implements TestWatcher {
    @Override
    public final void testFailed(ExtensionContext context, Throwable cause) {
        onTestFailed(context, cause);
    }

    protected abstract void onTestFailed(ExtensionContext context, Throwable cause);
}
