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
package org.sonarlint.intellij.issue.tracking

import org.sonarlint.intellij.util.SonarLintUtils
import java.util.ArrayList
import java.util.HashMap
import java.util.function.Function


class SingleIssueTracker<RAW : Trackable?, BASE : Trackable?> {

  fun track(rawInput: RAW, baseInput: Input<BASE>?): SingleIssueTracking<RAW, BASE> {
    val tracking = SingleIssueTracking(rawInput, baseInput)

    // 1. match issues with same rule, same line and same text range hash, but not necessarily with same message
    match(tracking, LineAndTextRangeHashKeyFactory.INSTANCE)

    // 2. match issues with same rule, same message and same text range hash
    match(tracking, TextRangeHashAndMessageKeyFactory.INSTANCE)

    // 3. match issues with same rule, same line and same message
    match(tracking, LineAndMessageKeyFactory.INSTANCE)

    // 4. match issues with same rule and same text range hash but different line and different message.
    // See SONAR-2812
    match(tracking, TextRangeHashKeyFactory.INSTANCE)

    // 5. match issues with same rule, same line and same line hash
    match(tracking, LineAndLineHashKeyFactory.INSTANCE)

    // 6. match issues with same rule and same same line hash
    match(tracking, LineHashKeyFactory.INSTANCE)

    // 7. match issues with same server issue key
    match(tracking, ServerIssueSearchKeyFactory.INSTANCE)
    return tracking
  }

  private fun match(tracking: SingleIssueTracking<RAW, BASE>, factory: SearchKeyFactory) {
    val baseSearch: MutableMap<SearchKey, MutableList<BASE>> = HashMap()
    for (base in tracking.unmatchedBases) {
      base ?: return
      val searchKey = factory.apply(base)
      if (!baseSearch.containsKey(searchKey)) {
        baseSearch[searchKey] = ArrayList()
      }
      baseSearch[searchKey]!!.add(base)
    }
    val raw = tracking.unmatchedRaw
    raw ?: return
    val rawKey = factory.apply(raw)
    val bases: Collection<BASE>? = baseSearch[rawKey]
    if (bases != null && !bases.isEmpty()) {
      // TODO taking the first one. Could be improved if there are more than 2 issues on the same line.
      // Message could be checked to take the best one.
      val match = bases.iterator().next()
      tracking.match(raw, match)
      baseSearch[rawKey]!!.remove(match)
    }

  }

  private interface SearchKey

  @FunctionalInterface
  private interface SearchKeyFactory : Function<Trackable, SearchKey> {
    override fun apply(trackable: Trackable): SearchKey
  }

  private class LineAndTextRangeHashKey(trackable: Trackable) :
    SearchKey {
    private val ruleKey = trackable.ruleKey
    private val textRangeHash = trackable.textRangeHash
    private val line = trackable.line
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null) {
        return false
      }
      if (this.javaClass != o.javaClass) {
        return false
      }
      val that = o as LineAndTextRangeHashKey
      // start with most discriminant field
      return (line == that.line
        && textRangeHash == that.textRangeHash
        && ruleKey == that.ruleKey)
    }

    override fun hashCode(): Int {
      var result = ruleKey.hashCode()
      result = 31 * result + (textRangeHash?.hashCode() ?: 0)
      result = 31 * result + (line?.hashCode() ?: 0)
      return result
    }

  }

  private enum class LineAndTextRangeHashKeyFactory : SearchKeyFactory {
    INSTANCE {
      override fun apply(trackable: Trackable) = LineAndTextRangeHashKey(trackable)
    }

  }

  private class LineAndLineHashKey(trackable: Trackable) :
    SearchKey {
    private val ruleKey: String
    private val line: Int?
    private val lineHash: Int?
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null) {
        return false
      }
      if (this.javaClass != o.javaClass) {
        return false
      }
      val that = o as LineAndLineHashKey
      // start with most discriminant field
      return (line == that.line
        && lineHash == that.lineHash
        && ruleKey == that.ruleKey)
    }

    override fun hashCode(): Int {
      var result = ruleKey.hashCode()
      result = 31 * result + (lineHash?.hashCode() ?: 0)
      result = 31 * result + (line?.hashCode() ?: 0)
      return result
    }

    init {
      ruleKey = trackable.ruleKey
      line = trackable.line
      lineHash = trackable.lineHash
    }
  }

  private enum class LineAndLineHashKeyFactory : SearchKeyFactory {
    INSTANCE;

    override fun apply(trackable: Trackable): SearchKey {
      return LineAndLineHashKey(trackable)
    }
  }

  private class LineHashKey internal constructor(trackable: Trackable) :
    SearchKey {
    private val ruleKey: String
    private val lineHash: Int?
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null) {
        return false
      }
      if (this.javaClass != o.javaClass) {
        return false
      }
      val that = o as LineHashKey
      // start with most discriminant field
      return (lineHash == that.lineHash
        && ruleKey == that.ruleKey)
    }

    override fun hashCode(): Int {
      var result = ruleKey.hashCode()
      result = 31 * result + (lineHash?.hashCode() ?: 0)
      return result
    }

    init {
      ruleKey = trackable.ruleKey
      lineHash = trackable.lineHash
    }
  }

  private enum class LineHashKeyFactory : SearchKeyFactory {
    INSTANCE;

    override fun apply(t: Trackable): SearchKey {
      return LineHashKey(t)
    }
  }

  private class TextRangeHashAndMessageKey(trackable: Trackable) :
    SearchKey {
    private val ruleKey: String
    private val message: String
    private val textRangeHash: Int?
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null) {
        return false
      }
      if (this.javaClass != o.javaClass) {
        return false
      }
      val that = o as TextRangeHashAndMessageKey
      // start with most discriminant field
      return (textRangeHash == that.textRangeHash
        && message == that.message && ruleKey == that.ruleKey)
    }

    override fun hashCode(): Int {
      var result = ruleKey.hashCode()
      result = 31 * result + message.hashCode()
      result = 31 * result + (textRangeHash?.hashCode() ?: 0)
      return result
    }

    init {
      ruleKey = trackable.ruleKey
      message = trackable.message
      textRangeHash = trackable.textRangeHash
    }
  }

  private enum class TextRangeHashAndMessageKeyFactory : SearchKeyFactory {
    INSTANCE;

    override fun apply(t: Trackable): SearchKey {
      return TextRangeHashAndMessageKey(t)
    }
  }

  private class LineAndMessageKey(trackable: Trackable) :
    SearchKey {
    private val ruleKey: String
    private val message: String
    private val line: Int?
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null) {
        return false
      }
      if (this.javaClass != o.javaClass) {
        return false
      }
      val that = o as LineAndMessageKey
      // start with most discriminant field
      return (line == that.line
        && message == that.message && ruleKey == that.ruleKey)
    }

    override fun hashCode(): Int {
      var result = ruleKey.hashCode()
      result = 31 * result + message.hashCode()
      result = 31 * result + (line?.hashCode() ?: 0)
      return result
    }

    init {
      ruleKey = trackable.ruleKey
      message = trackable.message
      line = trackable.line
    }
  }

  private enum class LineAndMessageKeyFactory : SearchKeyFactory {
    INSTANCE;

    override fun apply(t: Trackable): SearchKey {
      return LineAndMessageKey(t)
    }
  }

  private class TextRangeHashKey internal constructor(trackable: Trackable) :
    SearchKey {
    private val ruleKey: String
    private val textRangeHash: Int?
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null) {
        return false
      }
      if (this.javaClass != o.javaClass) {
        return false
      }
      val that = o as TextRangeHashKey
      // start with most discriminant field
      return (textRangeHash == that.textRangeHash
        && ruleKey == that.ruleKey)
    }

    override fun hashCode(): Int {
      var result = ruleKey.hashCode()
      result = 31 * result + (textRangeHash?.hashCode() ?: 0)
      return result
    }

    init {
      ruleKey = trackable.ruleKey
      textRangeHash = trackable.textRangeHash
    }
  }

  private enum class TextRangeHashKeyFactory : SearchKeyFactory {
    INSTANCE;

    override fun apply(t: Trackable): SearchKey {
      return TextRangeHashKey(t)
    }
  }

  private class ServerIssueSearchKey internal constructor(trackable: Trackable) :
    SearchKey {
    private val serverIssueKey: String?
    override fun equals(o: Any?): Boolean {
      if (this === o) {
        return true
      }
      if (o == null || javaClass != o.javaClass) {
        return false
      }
      val that = o as ServerIssueSearchKey
      return !SonarLintUtils.isBlank(serverIssueKey) && !SonarLintUtils.isBlank(that.serverIssueKey) && serverIssueKey == that.serverIssueKey
    }

    override fun hashCode(): Int {
      return serverIssueKey?.hashCode() ?: 0
    }

    init {
      serverIssueKey = trackable.serverIssueKey
    }
  }

  private enum class ServerIssueSearchKeyFactory : SearchKeyFactory {
    INSTANCE;

    override fun apply(trackable: Trackable): SearchKey {
      return ServerIssueSearchKey(trackable)
    }
  }

}
