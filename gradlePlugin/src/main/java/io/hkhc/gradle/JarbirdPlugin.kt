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

import io.hkhc.gradle.builder.PublicationBuilder
import io.hkhc.gradle.pom.PomFactory
import io.hkhc.util.ANDROID_LIBRARY_PLUGIN_ID
import io.hkhc.util.LOG_PREFIX
import io.hkhc.util.detailMessageError
import io.hkhc.util.fatalMessage
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class JarbirdPlugin : Plugin<Project> {

    private lateinit var extension: JarbirdExtension
    private var androidPluginAppliedBeforeUs = false

    /*
        We need to know which project this plugin instance refer to.
        We got call back from ProjectEveluationListener once for every root/sub project, and we
        want to proceed if the project param in callback match this plugin instance
     */
    private lateinit var project: Project

    // TODO check if POM fulfill minimal requirements for publishing
    // TODO maven publishing dry-run
    // TODO accept both pom.yml or pom.yaml

    @Suppress("ThrowsCount")
    private fun precheck(project: Project) {
        with(project) {
            if (group.toString().isBlank()) {
                detailMessageError(
                    project.logger,
                    "Group name is not specified",
                    "Add 'group' value to build script or pom.xml"
                )
                throw GradleException("$LOG_PREFIX Group name is not specified")
            }
            if (version.toString().isBlank()) {
                detailMessageError(
                    project.logger,
                    "Version name is not specified",
                    "Add 'version' value to build script or pom.xml"
                )
                throw GradleException("$LOG_PREFIX Version is not specified")
            }
            if (pluginManager.hasPlugin(ANDROID_LIBRARY_PLUGIN_ID)) {
                if (!androidPluginAppliedBeforeUs) {
                    fatalMessage(
                        project,
                        "$PLUGIN_ID should not applied before $ANDROID_LIBRARY_PLUGIN_ID"
                    )
                }
                if (extension.gradlePlugin) {
                    fatalMessage(project, "Cannot build Gradle plugin in Android project")
                }
            }
        }
    }

    private fun checkAndroidPlugin(project: Project) {

        if (project.pluginManager.hasPlugin(ANDROID_LIBRARY_PLUGIN_ID)) {
            androidPluginAppliedBeforeUs = true
            project.logger.debug("$LOG_PREFIX $ANDROID_LIBRARY_PLUGIN_ID plugin is found to be applied")
        } else {
            androidPluginAppliedBeforeUs = false
            project.logger.debug("$LOG_PREFIX apply $ANDROID_LIBRARY_PLUGIN_ID is not found to be applied")
        }
    }

    /**
     * The order of applying plugins and whether they are deferred by the two kind of afterEvaluate listener, are
     * important. So mess around them without know exactly the consequence.
     */
    override fun apply(p: Project) {

        project = p
        project.logger.debug("$LOG_PREFIX Start applying $PLUGIN_FRIENDLY_NAME")
        val pom = PomFactory().resolvePom(p)

        extension = project.extensions.create(SP_EXT_NAME, JarbirdExtension::class.java, project)
        extension.pom = pom
        val pubCreator = PublicationBuilder(extension, project, pom)

        project.logger.debug("$LOG_PREFIX Aggregrated POM configuration: $pom")

        checkAndroidPlugin(p)

        /*

        gradle.afterEvaluate
            - Sync POM
            - Phase 1
                - config bintray extension
                - config artifactory exctension
            - plugin: bintray gradle.afterEvaluate
            - Phase 2
                - config plugin publishing

        project.afterEvaluate
            - Phase 3
            - ...
            - bintray
            - gradle plugin
                setup testkit dependency
                validate plugin config
            - Phase 4
                configure publishing
                configure signing
                setup tasks

        gradle.projectEvaluated
            - bintray

        ------------------------------------------------


            ProjectEvaluationListener is invoked before any project.afterEvaluate.
            So we use projectEvaluateListener to make sure our setup has done before the projectEvaluationListener
            in other plugins.

            Further, the ProjectEvaluationListener added by Gradle.addProjectEvaluationListener() within
            project.afterEvaluate will not be executed, as the ProjectEvaluateListeners have been executed
            before callback of project.afterEvaluate and will not go back to run again. So if a plugin needs to invoke
            project.afterEvaluate, then it should not be applied within another project.afterEvaluate. However it is
            OK to apply it in ProjectEvaluationListener.

            Setup bintrayExtension before bintray's ProjectEvaluationListener.afterEvaluate
            which expect bintray extension to be ready.
            The bintray task has been given publication names and it is fine the the publication
            is not ready yet. The actual publication is not accessed until execution of task.

            Setup publication for android library shall be done at late afterEvaluate, so that android library plugin
            has change to create the components and source sets.

         */

        with(project.pluginManager) {

            /**
             * @see org.gradle.api.publish.maven.plugins.MavenPublishPlugin
             * no evaluation listener
             */
            apply("org.gradle.maven-publish")

            /**
             * @see org.jetbrains.dokka.gradle.DokkaPlugin
             * no evaluation listener
             */
            apply("org.jetbrains.dokka")
        }

        /* Under the following situation we need plugins to be applied within the Gradle-scope afterEvaluate
            method. We need the corresponding extension ready before apply it, and we might actually generate
            that extension within plugin, so we need to defer the application of the plugin (e.g. SigningPlugin)
         */

        // Build Phase 1
        project.gradleAfterEvaluate { _ ->
            pom.syncWith(p)

            // pre-check of final data, for child project
            // TODO handle multiple level child project?
            if (!project.isMultiProjectRoot()) {
                precheck(p)
            }

            with(p.pluginManager) {
                if (extension.signing) {
                    /**
                     * @see org.gradle.plugins.signing.SigningPlugin
                     * no evaluation listener
                     */
                    apply("org.gradle.signing")
                }

                if (extension.gradlePlugin) {

                    /**
                     * @see com.gradle.publish.PublishPlugin
                     *      project.afterEvaluate
                     *          setup sourcejar docjar tasks
                     */
                    apply("com.gradle.plugin-publish")

                    /**
                     * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
                     * project.afterEvaluate
                     *      add testkit dependency
                     * project.afterEvaluate
                     *      validate plugin declaration
                     */
                    apply("org.gradle.java-gradle-plugin")
                }
            }
        }

        // Gradle plugin publish plugin is not compatible with Android plugin.
        // apply it only if needed, otherwise android aar build will fail
        // Defer the configuration with afterEvaluate so that Android plugin has a chance
        // to setup itself before we configure the bintray plugin
        project.gradleAfterEvaluate { _ ->
            pubCreator.buildPhase1()
        }

        /*
        We don't apply bintray and artifactory plugin conditionally, because it make use of
        projectEvaluationListener, but we cannot get the flag from extension until we run
        afterEvaluate event. This is a conflict. So we just let go and apply these two
        plugins anyway. We put the bintray extension configuration code at PublicationBuilder.buildPhase1, which is
        executed in another ProjectEvaluationListener setup before Bintray's. (@see PublicationBuilder)
         */
        with(project.pluginManager) {
            /**
             * @see com.jfrog.bintray.gradle.BintrayPlugin
             * ProjectsEvaluationListener
             *     afterEvaluate:
             *         bintrayUpload task depends on subProject bintrayUpload
             *     projectEvaluated:
             *         bintrayUpload task depends on publishToMavenLocal
             */
            apply("com.jfrog.bintray")

            /**
             * @see org.jfrog.gradle.plugin.artifactory.ArtifactoryPlugin
             *     afterEvaluate:
             *         artifactoryTasks task depends on subProject
             *     projectEvaluated:
             *         finialize artifactoryTasks task
             */
            apply("com.jfrog.artifactory")
        }

        // Build phase 3
        project.afterEvaluate {
            pubCreator.buildPhase3()
        }

        // Build phase 2
        project.gradleAfterEvaluate {
            pubCreator.buildPhase2()
        }

        // Build phase 4
        project.afterEvaluate {
            pubCreator.buildPhase4()
        }
    }
}