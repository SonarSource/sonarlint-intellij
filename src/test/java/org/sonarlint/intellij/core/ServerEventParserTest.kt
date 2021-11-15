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
package org.sonarlint.intellij.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ServerEventParserTest {
    @Test
    fun should_parse_heart_beat() {
        val event = ServerEventParser.parse("\r\n")

        assertThat(event).isEqualTo(HeartBeatReceived)
    }

    @Test
    fun should_parse_rule_activated_event() {
        val event = ServerEventParser.parse(
            """data: {
                "project": "com.sonarsource:citytour2019-java",
                "type": "RuleActivated",
                "content": {
                    "params": [],
                    "ruleKey": "S3373"
                }
            }
        """ + "\r\n\r\n"
        )

        assertThat(event).isEqualTo(
            RuleActivated(
                "com.sonarsource:citytour2019-java", "RuleActivated", RuleActivationContent(
                    emptySet(), "S3373"
                )
            )
        )
    }
}
