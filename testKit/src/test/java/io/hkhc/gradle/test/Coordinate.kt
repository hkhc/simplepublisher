/*
 * Copyright (c) 2020. Herman Cheung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package io.hkhc.gradle.test

import io.hkhc.utils.Path.Companion.relativePath

/**
 * This class is not expected for business logic of the program. It is solely for capturing the unit test parameter.
 */
data class Coordinate(
    val group: String,
    val artifactId: String,
    val version: String,
    val pluginId: String? = null,
    val artifactIdWithVariant: String = artifactId,
    val versionWithVariant: String = version
) {
    fun getPath() = relativePath(
        group.replace('.', '/'),
        artifactIdWithVariant,
        versionWithVariant
    ).toString()
    fun getPluginPath() = pluginId?.let { pid ->
        relativePath(
            pid.replace('.', '/'),
            "$pid.gradle.plugin",
            versionWithVariant
        ).toString()
    }
    fun getFilenameBase() = "$artifactIdWithVariant-$versionWithVariant"
}
