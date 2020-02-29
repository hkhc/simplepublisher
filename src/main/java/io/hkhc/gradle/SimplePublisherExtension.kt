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

package io.hkhc.gradle

import org.gradle.api.Project

// Gradle plugin extensions must be open class so that Gradle system can "decorate" it.
open class SimplePublisherExtension(private val project: Project) {

    // TODO limitation shall not be global, but on per-publication-name basis
    private var publisherHasSetup = false

    /**
     * Configure for bintray publishing or not
     */
    var bintray = true

    /**
     * Configure for OSS JFrog Snapshot publishing or not
     */
    var ossArtifactory = true

    /**
     * Configure for gradle plugin publishing or not (reserve for future use)
     */
    var gradlePlugin = false

    /**
     * Configure for artifact signing or not
     */
    var signing = true

    val pom = PomFactory().resolvePom(project)

    @Suppress("unused")
    fun publish(block: (PublishParam.() -> (Unit))? = null) {

        if (publisherHasSetup) {
            throw SImplyPublisherException("Publisher can be setup once only.")
        }

        val param = PublishParam()
        block?.invoke(param)
        val builder = PublicationBuilder(
            this,
            project,
            param,
            pom
        )
        builder.build()
        publisherHasSetup = true
    }
}
