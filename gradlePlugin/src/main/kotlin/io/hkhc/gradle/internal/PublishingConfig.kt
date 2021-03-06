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

import io.hkhc.gradle.JarbirdExtension
import io.hkhc.gradle.JarbirdPub
import io.hkhc.gradle.internal.dokka.DokkaConfig
import io.hkhc.gradle.internal.maven.MavenPomAdapter
import io.hkhc.gradle.internal.repo.MavenRepoSpec
import io.hkhc.gradle.internal.utils.Version
import io.hkhc.gradle.internal.utils.detailMessageWarning
import io.hkhc.gradle.internal.utils.findExtension
import io.hkhc.gradle.pom.Pom
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.w3c.dom.Document

internal class PublishingConfig(
    private val project: Project,
    private val extension: JarbirdExtension,
    private val pubs: List<JarbirdPub>,
    private val sourceResolver: SourceResolver
) {
    private val extensions = (project as ExtensionAware).extensions
    // TODO we shall have one separate dokka task per pub
//    private var dokka = pubs.map { it.dokka }.find { it != null }
    // private var dokka = project.tasks.named("dokkaHtml").get()

    companion object {
        private fun printHelpForMissingDokka(project: Project) {
            detailMessageWarning(
                project.logger,
                "No dokka task is found in project '${project.name}'. Maven publishing cannot be done.",
                """
                    We cannot find a dokka task in project '${project.name}'.
                    You may need to apply the dokka plugin like this
                        id("org.jetbrains.dokka") version "0.10.1"

                    It create a default dokka task that jarbird recognize 
                    If you defined a dokka with different name, you may specify it in $SP_EXT_NAME block:
                        $SP_EXT_NAME {
                            ...
                            dokka = myDokkaTask
                            ...
                        }
                """.trimIndent()
            )
        }
    }

    fun config() {

        project.logger.debug("$LOG_PREFIX Configure Publishing extension")

        requireNotNull(project.findExtension(PublishingExtension::class.java)) {
            "\"publishing\" extension is not found. Maybe \"org.gradle.maven-publish\" is not applied?"
        }.config()
    }

    private fun PublishingExtension.config() {

        publications {
            createPublication()
        }

        if (pubs.needReposWithType<MavenRepoSpec>()) {
            repositories {
                createRepository()
            }
        }
    }

    private fun registerSourceSetCompileTask(pub: JarbirdPubImpl): TaskProvider<Jar>? {
        // TODO Handle multiple source sets
        return pub.mSourceSet?.let { sourceSet ->

            if (sourceSet.name != "main") {
                ClassesJarTaskInfo(pub).register(project.tasks, Jar::class.java) {
                    archiveBaseName.set(pub.variantArtifactId())
                    archiveVersion.set(pub.variantVersion())
                    from(project.configurations["${sourceSet.name}Compile"])
                    dependsOn(
                        project.tasks.named("${sourceSet.name}Classes")
                    )
                }
            } else {
                project.tasks.named<Jar>("jar").apply {
                    configure {
                        archiveBaseName.set(pub.variantArtifactId())
                        archiveVersion.set(pub.variantVersion())
                    }
                }
            }
        }
    }

    private fun PublicationContainer.createPublication() {

        pubs.forEach { pub0 ->

            val pub = pub0 as JarbirdPubImpl
            project.logger.debug(
                "$LOG_PREFIX CreatePublication variant=${pub.variant} component=${pub.component}"
            )

            val pom = pub.pom

            val publishJarTask = registerSourceSetCompileTask(pub)

            register(pub.pubNameWithVariant(), MavenPublication::class.java) {

                groupId = pom.group

                artifactId = pub.variantArtifactId()

                // version is gotten from an external plugin
                //            version = project.versioning.info.display
                version = pub.variantVersion()

                if (publishJarTask == null) {
                    requireNotNull(pub.component) {
                        "Software component is not set."
                    }.let { from(it) }
                } else {
                    artifactCompat(publishJarTask)
                }

                // We are adding documentation artifact

                /*
                 https://github.com/gradle/gradle/pull/13505
                 artifact supports TaskProvider from Gradle 6.6
                 */

                project.afterEvaluate {
                    if (pub.needsGenDoc()) {
                        artifactCompat(DokkaConfig(project, extension, sourceResolver).setupDokkaJar(pub))
                    }
                    requireNotNull(pub.sourceSetModel()) {
                        "sourceSet model is unexpectedly null. Probably a bug."
                    }.let { sourceSetModel ->
                        artifactCompat(
                            SourceConfig(project, sourceResolver).configSourceJarTask(pub, sourceSetModel)
                        )
                    }
                }

                pom { MavenPomAdapter().fill(this, pom) }
            }

            if (pub.pom.isGradlePlugin()) {
                register(pub.markerPubName, MavenPublication::class.java) {

                    (this as MavenPublicationInternal).isAlias = true

                    groupId = requireNotNull(pub.pom.plugin) {
                        "No plugin information is provided in POM"
                    }.id

                    artifactId = pub.pluginMarkerArtifactIdWithVariant()

                    version = pub.variantVersion()

                    pom {
                        MavenPomAdapter().fill(this, pom)
                        withXml { pomAddDependency(asElement().ownerDocument, pom) }
                    }
                }
            }
        }
    }

    private fun pomAddDependency(doc: Document, pom: Pom) {
        val root = doc.documentElement
        val dependencies = root.appendChild(doc.createElement("dependencies"))
        val dependency = dependencies.appendChild(doc.createElement("dependency"))
        val groupId = dependency.appendChild(doc.createElement("groupId"))
        groupId.textContent = pom.group
        val artifactId = dependency.appendChild(doc.createElement("artifactId"))
        artifactId.textContent = pom.artifactId
        val version = dependency.appendChild(doc.createElement("version"))
        version.textContent = pom.version
    }

    /**
     * A backward compatible version of artifact function for Gradle
     */
    private fun MavenPublication.artifactCompat(source: Any) {
        if (Version(project.gradle.gradleVersion) >= Version("6.6")) {
            artifact(source)
        } else {
            if (source is TaskProvider<*>) {
                artifact(source.get())
            } else {
                artifact(source)
            }
        }
    }

    private fun RepositoryHandler.createRepository() {

        val releaseEndpoints = pubs
            .filter { it.pom.isRelease() }
            .flatMap { it.getRepos() }
            .filterIsInstance<MavenRepoSpec>()
            .toSet()
        val snapshotEndpoint = pubs
            .filter { it.pom.isSnapshot() }
            .flatMap { it.getRepos() }
            .filterIsInstance<MavenRepoSpec>()
            .toSet()

        releaseEndpoints.forEach {

            maven {
                val repo = this
                with(it) {
                    val ep = this
                    name = ep.id
                    url = project.uri(ep.releaseUrl)
                    credentials {
                        username = ep.username
                        password = ep.password
                    }
                    repo.isAllowInsecureProtocol = it.isAllowInsecureProtocol
                }
            }
        }

        snapshotEndpoint.forEach {

            maven {
                val repo = this
                with(it) {
                    val ep = this
                    name = ep.id
                    url = project.uri(ep.snapshotUrl)
                    credentials {
                        username = ep.username
                        password = ep.password
                    }
                    repo.isAllowInsecureProtocol = it.isAllowInsecureProtocol
                }
            }
        }
    }
}
