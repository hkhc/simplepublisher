/*
 * Copyright (c) 2021. Herman Cheung
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

package io.hkhc.gradle.internal.dokka

import io.hkhc.gradle.internal.SourceDirs
import io.hkhc.gradle.internal.SourceSetNames
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

open class DefaultSourceResolver(private val project: Project) : SourceResolver {

    override fun getSourceJarSource(source: Any): Array<out Any> {
        return when (source) {
            is SourceSet -> source.allSource.srcDirs.toTypedArray()
            is String -> SourceSetNames(project, arrayOf(source)).getDirs()
            is SourceSetNames -> source.getDirs()
            is SourceDirs -> arrayOf(source.getDirs())
            // TODO is SourceSetContainer -> ...
            else -> arrayOf(source)
        }
    }
}
