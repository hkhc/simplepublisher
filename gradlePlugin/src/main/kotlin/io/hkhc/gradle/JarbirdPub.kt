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

package io.hkhc.gradle

import io.hkhc.gradle.pom.Pom
import org.jetbrains.dokka.gradle.AbstractDokkaTask

abstract class JarbirdPub : RepoDeclaration {

    lateinit var pom: Pom

    /**
     * Configure for artifact signing or not
     */
    var signing = true

    /**
     * the publication name, that affect the task names for publishing
     */
    var pubName: String = "lib"

    /**
     * the variant is string that as suffix of pubName to form a final publication name. It is also used
     * to suffix the dokka jar task and source set jar task.
     * It is usually used for building Android artifact
     */
    open val variant: String = ""

    var docSourceSets: Any = "main"

    /**
     * Use if performing signing with external GPG command. false to use Gradle built-in PGP implementation.
     * We will need useGpg=true if we use new keybox (.kbx) format for signing key.
     */
    var useGpg = false

    var dokkaConfig: AbstractDokkaTask.(pub: JarbirdPub) -> Unit = {}

    abstract fun from(source: Any)
//    abstract fun from(component: SoftwareComponent)
//    abstract fun from(sourceSet: SourceSet)

    abstract fun variantWithVersion()
    abstract fun variantWithArtifactId()
    abstract fun variantInvisible()

    abstract fun variantArtifactId(): String?
    abstract fun variantVersion(): String?
    abstract fun getGAV(): String?
    abstract fun pluginCoordinate(): String?

    abstract fun sourceSetNames(vararg names: String): Any
    abstract fun sourceSetNames(names: List<String>): Any
    abstract fun sourceDirs(dirs: Any): Any
}
