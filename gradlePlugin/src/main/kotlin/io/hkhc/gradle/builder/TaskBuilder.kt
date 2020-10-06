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

package io.hkhc.gradle.builder

import io.hkhc.gradle.pom.Pom
import io.hkhc.gradle.SP_GROUP
import io.hkhc.gradle.JarbirdExtension
import io.hkhc.gradle.isMultiProjectRoot
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer

class TaskBuilder(
    private val project: Project,
    private val pom: Pom,
    private val extension: JarbirdExtension,
    private val pubName: String
) {

    private val pubNameCap = pubName.capitalize()
    private val pubId = "${pubNameCap}Publication"
    private val markerPubId = "${pubNameCap}PluginMarkerMavenPublication"
    private val mavenRepo = "Maven${pubNameCap}Repository"
    private val mavenLocal = "MavenLocal"

    private fun registerRootProjectTasks(taskPath: String) {

        project.tasks.register(taskPath) {
            group = SP_GROUP
            project.childProjects.forEach { (_, child) ->
                val rootTask = this
                child.tasks.findByPath(taskPath)?.let { childTask ->
                    rootTask.dependsOn(childTask.path)
                }
            }
        }
    }

    private fun TaskContainer.registerMavenLocalTask() {

        if (project.isMultiProjectRoot()) {
            registerRootProjectTasks("jbPublishTo$mavenLocal")
        } else {

            register("jbPublishTo$mavenLocal") {
                group = SP_GROUP

                description = if (extension.gradlePlugin) {
                    "Publish Maven publication '$pubName' " +
                        "and plugin '${pom.plugin?.id}' to the local Maven Repository"
                } else {
                    "Publish Maven publication '$pubName' to the local Maven Repository"
                }

                dependsOn("publish${pubId}To$mavenLocal")

                if (extension.gradlePlugin) {
                    dependsOn("publish${markerPubId}To$mavenLocal")
                }
            }
        }
    }

    private fun TaskContainer.registerMavenRepositoryTask() {

        if (project.isMultiProjectRoot()) {
            registerRootProjectTasks("jbPublishToMavenRepository")
        } else {

            register("jbPublishToMavenRepository") {
                group = SP_GROUP

                // I don't know why the maven repository name in the task name is not capitalized

                description = if (extension.gradlePlugin) {
                    "Publish Maven publication '$pubName' " +
                        "and plugin '${pom.plugin?.id}' to the 'Maven$pubNameCap' Repository"
                } else {
                    "Publish Maven publication '$pubName' to the 'Maven$pubNameCap' Repository"
                }

                dependsOn("publish${pubId}To$mavenRepo")

                //            if (extension.gradlePlugin) {
                //                dependsOn("publish${markerPubId}To$mavenRepo")
                //            }
            }
        }
    }

    private fun TaskContainer.registerBintrayTask() {

        if (project.isMultiProjectRoot()) {
            registerRootProjectTasks("jbPublishToBintray")
        } else {
            register("jbPublishToBintray") {
                group = SP_GROUP

                val target = if (pom.isSnapshot()) "OSS JFrog" else "Bintray"

                description = if (extension.gradlePlugin) {
                    "Publish Maven publication '$pubName' " +
                        "and plugin '${pom.plugin?.id}' to $target"
                } else {
                    "Publish Maven publication '$pubName' to $target"
                }

                /*
                    bintray repository does not allow publishing SNAPSHOT artifacts, it has to be published
                    to the OSS JFrog repository
                 */
                if (pom.isSnapshot()) {
                    if (extension.ossArtifactory) {
                        dependsOn("artifactory${pubNameCap}Publish")
                    }
                } else {
                    dependsOn("bintrayUpload")
                }
            }
        }
    }

    private fun TaskContainer.registerGradlePortalTask() {

        if (project.isMultiProjectRoot()) {
            registerRootProjectTasks("jbPublishToGradlePortal")
        } else {
            register("jbPublishToGradlePortal") {
                group = SP_GROUP
                description = "Publish plugin '${pom.plugin?.id}' to the Gradle plugin portal"
                dependsOn("publishPlugins")
            }
        }
    }

    private fun TaskContainer.registerPublishTask() {

        if (project.isMultiProjectRoot()) {
            registerRootProjectTasks("jbPublish")
        } else {
            register("jbPublish") {
                group = SP_GROUP

                // assemble a list of repositories
                val repoList = mutableListOf<String>()
                repoList.add("Maven Local")
                repoList.add("'Maven$pubName' Repository")
                if (extension.bintray) {
                    repoList.add("Bintray")
                }
                if (extension.gradlePlugin) {
                    repoList.add("Gradle Plugin Portal")
                }
                val repoListStr = repoList.joinToString()

                description = if (extension.gradlePlugin) {
                    "Publish Maven publication '$pubNameCap' and plugin '${pom.plugin?.id}' to $repoListStr"
                } else {
                    "Publish Maven publication '$pubNameCap' to $repoListStr"
                }

                dependsOn("jbPublishTo$mavenLocal")
                dependsOn("jbPublishToMavenRepository")
                if (extension.bintray) {
                    dependsOn("jbPublishToBintray")
                }
                if (extension.gradlePlugin) {
                    dependsOn("jbPublishToGradlePortal")
                }
            }
        }
    }

    fun build() {

        with(project.tasks) {
            registerMavenLocalTask()
            registerMavenRepositoryTask()
            if (extension.bintray) {
                registerBintrayTask()
            }
            if (extension.gradlePlugin) {
                registerGradlePortalTask()
            }
            registerPublishTask()
        }
    }
}