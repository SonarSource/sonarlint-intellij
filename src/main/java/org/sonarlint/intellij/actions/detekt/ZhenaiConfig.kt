/*
 * Copyright 1999-2017 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sonarlint.intellij.actions.detekt

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.Locale

/**
 *
 *
 * @author caikang
 * @date 2017/06/19
 */
@State(name = "ZhenaiConfig", storages = [Storage(file = "smartfox/zhenai.xml")])
class ZhenaiConfig : PersistentStateComponent<ZhenaiConfig> {
    override fun loadState(state: ZhenaiConfig) {
        if (state == null) {
            return
        }
        XmlSerializerUtil.copyBean(state, this)
    }

    var astCacheTime = 1000L
    var astCacheEnable = true

    var ruleCacheTime = 1000L
    var ruleCacheEnable = false

    var analysisBeforeCheckin = false

    var locale: String = ""
        get() {
            if (field.isEmpty()) {
                val lang = Locale.getDefault().language
                return if (lang != Locale.ENGLISH.language && lang != Locale.CHINESE.language) {
                    Locale.ENGLISH.language
                } else Locale.getDefault().language
            }

            return field
        }

    fun toggleLanguage() {
        locale = if (localeEn == locale) localeZh else localeEn
    }

    override fun getState(): ZhenaiConfig {
        return this
    }

    companion object {
        val localeEn = Locale.ENGLISH.language!!
        val localeZh = Locale.CHINESE.language!!
    }
}