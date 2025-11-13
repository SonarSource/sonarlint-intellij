/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.fixtures

import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.reflect.Method

@ExtendWith(RunInEdtInterceptor::class)
abstract class AbstractHeavyTests : HeavyPlatformTestCase() {

    @BeforeEach
    fun beforeEachHeavyTest(testInfo: TestInfo) {
        // explicitly call TestCase.setName as IntelliJ relies on it for the setup
        name = testInfo.testMethod.map(Method::getName).orElseGet { "test" }
        super.setUp()
    }

    @AfterEach
    fun afterEachHeavyTest() {
        super.tearDown()
    }
}

