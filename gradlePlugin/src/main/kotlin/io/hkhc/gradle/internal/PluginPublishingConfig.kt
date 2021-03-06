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

import com.gradle.publish.PluginBundleExtension
import io.hkhc.gradle.JarbirdPub
import io.hkhc.gradle.internal.maven.MavenPomAdapter
import io.hkhc.gradle.internal.utils.findExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

class PluginPublishingConfig(
    private val project: Project,
    private val pubs: List<JarbirdPub>
) {

    /*
        The following plugins shall be declared as dependencies in build.gradle.kts.
        The exact dependency identifier can be find by accessing the plugin POM file at
        https://plugins.gradle.org/m2/[path-by-id]/[plugin-id].gradle.plugin/[version]/
            [plugin-id].gradle.plugin-[version].pom

        e.g. for plugin com.gradle.plugin-publish, check the dependency section of POM at
        https://plugins.gradle.org/m2/com/gradle/plugin-publish/
            com.gradle.plugin-publish.gradle.plugin/0.10.1/com.gradle.plugin-publish.gradle.plugin-0.10.1.pom

     */

    fun config() {

        assert(pubs.needGradlePlugin())

        val model = PluginPublishingModel(project, pubs)

        requireNotNull(project.findExtension(GradlePluginDevelopmentExtension::class.java)) {
            "\"gradlePlugin\" extension is not found, may be " +
                "\"plugin org.gradle.java-gradle-plugin\" is not applied?"
        }.config(model)

        // TODO rename findByType to findExtension
        requireNotNull(project.findExtension(PluginBundleExtension::class.java)) {
            "\"pluginBundle\" extension is not found, may be " +
                "\"com.gradle.plugin-publish\" plugin is not applied?"
        }.config(model)

        presetupPluginMarkerPublication(model)
    }

    /*
        The gradle publish plugin hardcoded to use project name as publication artifact name.
        We further customize that publication here and replace it with pom.artifactId
        The default value of pom.name is still project.name so we are not violating the Gradle convention.
        "publishPluginMavenPublication*" task is created by gradle plugin publish plugin.
     */
    private fun presetupPluginMarkerPublication(model: PluginPublishingModel) {

        model.publishingPub.also { pub ->
            // We create the marker publication here so that the MavenPluginPublishPlugin may reuse it
            // and we can do some customization here.
            val publishing = project.findExtension(PublishingExtension::class.java)
            publishing?.publications {
                val pluginMainPublication = maybeCreate(
                    "pluginMaven",
                    MavenPublication::class.java
                )
                with(pluginMainPublication) {
                    from((pub as JarbirdPubImpl).component)
                    groupId = pub.pom.group
                    artifactId = pub.pom.artifactId
                    version = pub.pom.version
                    pom {
                        MavenPomAdapter().fill(this, pub.pom)
                    }
                }
            }
        }
    }

    private fun PluginBundleExtension.config(model: PluginPublishingModel) {

        // TODO we can have one set of metadata for gradle plugins in one project.
        // TODO do a pre check

        website = model.website
        vcsUrl = model.vcsUrl
        description = model.description
        tags = model.tags

        plugins {
            model.publishingPub.also { pub ->
                mavenCoordinates {
                    group = pub.pom.group
                    artifactId = pub.pom.artifactId
                    version = pub.pom.version
                }
                maybeCreate(pub.pubNameWithVariant()).apply {
                    // We have overrided a large part of the gradle plugin publish plugin.
                    // so we need to set the coordinate by ourselves.
                    displayName = pub.pom.name
                    description = pub.pom.description
                }
            }
        }
    }

    private fun GradlePluginDevelopmentExtension.config(model: PluginPublishingModel) {

        project.logger.debug("$LOG_PREFIX configure Gradle plugin development plugin")

        isAutomatedPublishing = false

        plugins {
            model.entries.forEach {
                maybeCreate(it.pubName).apply {
                    id = it.id
                    implementationClass = it.implementationClass
                }
            }
        }
    }
}
