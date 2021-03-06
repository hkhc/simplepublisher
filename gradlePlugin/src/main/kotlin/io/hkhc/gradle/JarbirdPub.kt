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

interface JarbirdPub : RepoDeclaration, SigningStrategy, PubVariantStrategy, DocDeclaration {

    /**
     * the publication name, that affect the task names for publishing
     */
    val pubName: String

    /**
     * provide information on how the project is build. The parameter could be instance of
     * - SoftwareComponent
     * - SourceSet
     */
    fun from(source: Any)

    /**
     * get the effective coordinate
     */
    fun getGAV(): String?

    /**
     * get the plugin coordinate
     */
    fun pluginCoordinate(): String?

    fun sourceSetNames(vararg names: String): Any
    fun sourceSetNames(names: List<String>): Any
    fun sourceDirs(dirs: Any): Any
}
