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

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.RecordingCopyTask
import groovy.lang.GroovyObject
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getPluginByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig
import org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class PublicationBuilder(
    private val extension: SimplePublisherExtension,
    private val project: Project,
    param: PublishParam
) {

    private val pubConfig = PublishConfig(project)
    private val variantCap = param.variant.capitalize()
    private val pubName = "${param.pubName}$variantCap"
    private val dokka = param.dokka ?: project.tasks.named("dokka")
    private val pubComponent = param.pubComponent
    private val sourceSetName = param.sourceSetName
    private val ext = (project as ExtensionAware).extensions

    @Suppress("unused")
    fun build() {

        /*
            Create a publication tasks
                "publish${pubName}${variantCap}ToMaven${pubName}${variantCap}Repository"
                "publish${pubName}${variantCap}ToMavenLocal"
         */

        // Without this sign will fail. may be gradle 6.2 will fix this?
        // https://discuss.gradle.org/t/unable-to-publish-artifact-to-mavencentral/33727/3
        project.tasks.withType<GenerateModuleMetadata> {
            enabled = false
        }

        ext.findByType(PublishingExtension::class.java)?.config(pubComponent)
        if (extension.signing) {
            ext.findByType(SigningExtension::class.java)?.config()
        }
        if (extension.bintray) {
            ext.findByType(BintrayExtension::class.java)?.config()
        }
        if (extension.ossArtifactory) {
            project.convention.getPluginByName<ArtifactoryPluginConvention>("artifactory").config()
        }

    }

    private fun PublishingExtension.config(
        pubComponent: String = "java"
    ) {

        publications {
            createPublication(pubComponent = pubComponent)
        }

        repositories {
            createRepository()
        }
    }

    private fun SigningExtension.config() {

        val publishingExtension = ext.findByType(PublishingExtension::class.java)

        isRequired = !pubConfig.effectiveArtifactVersion.endsWith("-SNAPSHOT")
        publishingExtension?.let { sign(it.publications[pubName]) }


    }

    @Suppress("unused")
    private fun BintrayExtension.config() {

        val labelList = pubConfig.bintrayLabels?.split(',')?.toTypedArray() ?: arrayOf()

        override = true
        dryRun = false
        publish = true

        user = pubConfig.bintrayUser
        key = pubConfig.bintrayApiKey
        setPublications(pubName)

        pkg.apply {
            repo = "maven"
            name = pubConfig.effectiveArtifactId
            desc = pubConfig.pomDescription!!
            setLicenses(pubConfig.licenseName!!)
            websiteUrl = pubConfig.scmUrl!!
            vcsUrl = pubConfig.scmUrl!!
            githubRepo = pubConfig.scmGithubRepo!!
            issueTrackerUrl = pubConfig.issuesUrl!!
            version.apply {
                name = pubConfig.effectiveArtifactVersion
                desc = pubConfig.pomDescription!!
                released = currentZonedDateTime()
                vcsTag = pubConfig.effectiveArtifactVersion
            }
            setLabels(*labelList)
        }

        // Bintray requires our private key in order to sign archives for us. I don't want to share
        // the key and hence specify the signature files manually and upload them.
        filesSpec(closureOf<RecordingCopyTask> {
            from("${project.buildDir}/libs").apply {
                include("*.aar.asc")
                include("*.jar.asc")
            }
            from("${project.buildDir}/publications/$pubName").apply {
                include("pom-default.xml.asc")
                rename("pom-default.xml.asc",
                    "${pubConfig.effectiveArtifactId}-${pubConfig.effectiveArtifactVersion}.pom.asc")
            }
            into("${(pubConfig.artifactGroup as String)
                .replace('.', '/')}/${pubConfig.effectiveArtifactId}/${pubConfig.effectiveArtifactVersion}")
        })
    }

    private fun ArtifactoryPluginConvention.config() {

        setContextUrl("http://oss.jfrog.org")
        publish(delegateClosureOf<PublisherConfig> {
            repository(delegateClosureOf<GroovyObject> {
                setProperty("repoKey", "oss-snapshot-local")
                setProperty("username", pubConfig.bintrayUser)
                setProperty("password", pubConfig.bintrayApiKey)
                setProperty("maven", true)
            })
            defaults(delegateClosureOf<GroovyObject> {
                invokeMethod("publications", pubName)
                setProperty("publishArtifacts", true)
                setProperty("publishArtifacts", true)
                setProperty("publishPom", true)
            })
        })
        resolve(delegateClosureOf<ResolverConfig> {
            setProperty("repoKey", "jcenter")
        })

        project.tasks.register("artifactory${pubName.capitalize()}Publish", ArtifactoryTask::class) {
            publications(pubName)
        }


    }

    private fun setupDokkaJar(): TaskProvider<Jar>? {
        val dokkaJarTaskName = "dokkaJar$variantCap"
        return try {
            project.tasks.named(dokkaJarTaskName, Jar::class.java) {
                archiveClassifier.set("javadoc")
            }
        } catch (e: UnknownTaskException) {
            // TODO add error message here if dokka is null
            project.tasks.register(dokkaJarTaskName, Jar::class.java) {
                group = JavaBasePlugin.DOCUMENTATION_GROUP
                description = "Assembles Kotlin docs with Dokka to Jar"
                archiveClassifier.set("javadoc")
                from(dokka)
                dependsOn(dokka)
            }
        }
    }

    private fun setupSourcesJar(sourceSetName: String): TaskProvider<Jar>? {

        val sourcesTaskName = "sourcesJar$variantCap"
        return try {
            project.tasks.named(sourcesTaskName, Jar::class.java) {
                archiveClassifier.set("sources")
            }
        } catch (e: UnknownTaskException) {
            project.tasks.register(sourcesTaskName, Jar::class.java) {
                description = "Create archive of source code for the binary"
                archiveClassifier.set("sources")
                from(project.sourceSets.getByName(sourceSetName).allSource)
            }
        }
    }

    private fun PublicationContainer.createPublication(pubComponent: String) {

        val dokkaJar = setupDokkaJar()
        val sourcesJar = setupSourcesJar(sourceSetName)

        register(pubName, MavenPublication::class.java) {

            groupId = pubConfig.artifactGroup

            // The default artifactId is project.name
            artifactId = pubConfig.effectiveArtifactId
            // version is gotten from an external plugin
            //            version = project.versioning.info.display
            version = pubConfig.effectiveArtifactVersion

            with(pubConfig) {

            // This is the main artifact
                from(project.components[pubComponent])
                // We are adding documentation artifact
                project.afterEvaluate {
                    dokkaJar?.let { artifact(it.get()) }
                    // And sources
                    sourcesJar?.let { artifact(it.get()) }
                }

                // See https://maven.apache.org/pom.html for POM definitions

                pom {
                    name.set(effectiveArtifactId)
                    description.set(pomDescription)
                    url.set(pubConfig.pomUrl)
                    licenses {
                        license {
                            name.set(licenseName)
                            url.set(licenseUrl)
                        }
                    }
                    developers {
                        developer {
                            id.set(developerId)
                            name.set(developerName)
                            email.set(developerEmail)
                        }
                    }
                    scm {
                        connection.set(scmConnection)
                        developerConnection.set(scmConnection)
                        url.set(scmUrl)
                    }
                }

                // TODO dependency versionMapping
            }
        }
    }

    private fun RepositoryHandler.createRepository() {
        maven {
            name = "Maven$pubName"
            with(pubConfig) {
                url = project.uri(
                    if (effectiveArtifactVersion.toString().endsWith("SNAPSHOT"))
                        nexusSnapshotRepositoryUrl!!
                    else
                        nexusReleaseRepositoryUrl!!
                )

                System.out.println("Repository URL = ${url}")

                credentials {
                    username = nexusUsername!!
                    password = nexusPassword!!
                }
            }
        }
    }

    private fun currentZonedDateTime(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ"))

    private val Project.sourceSets: SourceSetContainer get() =
        ext.getByName("sourceSets") as SourceSetContainer
}
