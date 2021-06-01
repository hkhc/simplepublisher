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

package io.hkhc.gradle.internal

import io.hkhc.gradle.JarbirdPub
import io.hkhc.gradle.RepoSpec
import io.hkhc.gradle.SigningStrategy
import io.hkhc.gradle.internal.repo.GradlePortalSpec
import io.hkhc.gradle.internal.repo.MavenCentralRepoSpec
import io.hkhc.gradle.internal.repo.MavenLocalRepoSpec
import io.hkhc.gradle.internal.repo.MavenRepoSpecImpl
import io.hkhc.gradle.internal.repo.PropertyRepoSpecBuilder
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.get
import org.jetbrains.dokka.gradle.AbstractDokkaTask

open class JarbirdPubImpl(
    protected val project: Project,
    private val ext: JarbirdExtensionImpl,
    projectProperty: ProjectProperty,
    variant: String = "",
    signingStrategy: SigningStrategy = SigningStrategyImpl(),
    variantStrategy: VariantStrategy = VariantStrategyImpl(variant)
) : JarbirdPub,
    SigningStrategy by signingStrategy,
    VariantStrategy by variantStrategy {

    private val repos = mutableSetOf<RepoSpec>()
    private val repoSpecBuilder = PropertyRepoSpecBuilder(projectProperty)

    override var pubName: String = "lib"
    override var docSourceSets: Any = "main"

    protected var component: SoftwareComponent? = null
    var sourceSet: SourceSet? = null
    var dokkaConfig: AbstractDokkaTask.(pub: JarbirdPub) -> Unit = {}

    fun effectiveComponent(): SoftwareComponent = component ?: project.components["java"]

    override fun configureDokka(block: AbstractDokkaTask.(pub: JarbirdPub) -> Unit) {
        dokkaConfig = block
    }

    override fun getGAV(): String {
        return "${pom.group}:${variantArtifactId()}:${variantVersion()}"
    }

    override fun pluginCoordinate(): String {
        return if (pom.isGradlePlugin()) "${pom.plugin?.id}" else "NOT-A-PLUGIN"
    }

    override fun from(source: Any) {
        when (source) {
            is SoftwareComponent -> this.component = source
            is SourceSet -> {
                this.sourceSet = source
                this.docSourceSets = source
            }
        }
    }

    override fun sourceSetNames(vararg names: String): Any = SourceSetNames(project, names)
    override fun sourceSetNames(names: List<String>): Any = SourceSetNames(project, names.toTypedArray())
    override fun sourceDirs(dirs: Any): Any = SourceDirs(dirs)

    override fun mavenCentral(): RepoSpec {
        return repos.find { it is MavenCentralRepoSpec } ?: repoSpecBuilder.buildMavenCentral(project).also {
            repos.add(it)
        }
    }

    override fun mavenRepo(key: String): RepoSpec {
        val repo = repoSpecBuilder.buildMavenRepo(key)
        val existingRepo = repos.filterIsInstance(MavenRepoSpecImpl::class.java).find { it.id == repo.id }
        return if (existingRepo == null) {
            repos.add(repo)
            repo
        } else {
            existingRepo
        }
    }

    override fun mavenLocal(): RepoSpec {
        return repos.find { it is MavenLocalRepoSpec } ?: repoSpecBuilder.buildMavenLocalRepo().also {
            repos.add(it)
        }
    }

    override fun gradlePortal(): RepoSpec {
        return repos.find { it is GradlePortalSpec } ?: repoSpecBuilder.buildGradlePluginRepo().also {
            repos.add(it)
        }
    }

    override fun artifactory(): RepoSpec {
        val repo = repoSpecBuilder.buildArtifactoryRepo()
        repos.add(repo)
        return repo
    }

    override fun getRepos(): Set<RepoSpec> {
        return (
            repos +
                ext.getRepos() +
                (ext.getParentExt()?.getRepos() ?: setOf())
            )
    }

    fun finalizeRepos() {

        val err = if ((pom.group ?: "").isEmpty()) {
            """
                Group is missed in POM for pub($variant). 
                May be the variant name or POM file is not correct.
            """.trimIndent()
        } else if ((pom.artifactId ?: "").isEmpty()) {
            """
                ArtifactID is missed in POM for pub($variant).
                May be the variant name or POM file is not correct.
            """.trimIndent()
        } else if ((pom.version ?: "").isEmpty()) {
            """
                Version is missed in POM for pub($variant).
                May be the variant name or POM file is not correct.
            """.trimIndent()
        } else {
            null
        }

        if (err != null) throw GradleException(err)

        if (pom.isGradlePlugin()) {
            gradlePortal()
        }
    }
}
