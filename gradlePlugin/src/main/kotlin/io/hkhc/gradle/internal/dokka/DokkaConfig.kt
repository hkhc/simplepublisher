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

import io.hkhc.gradle.JarbirdExtension
import io.hkhc.gradle.JarbirdPub
import io.hkhc.gradle.internal.CLASSIFIER_JAVADOC
import io.hkhc.gradle.internal.DokkaJarPubTaskInfo
import io.hkhc.gradle.internal.JarbirdPubImpl
import io.hkhc.gradle.internal.JbDokkaPubTaskInfo
import io.hkhc.gradle.internal.JbDokkaTaskInfo
import io.hkhc.gradle.internal.LOG_PREFIX
import io.hkhc.gradle.internal.SourceResolver
import io.hkhc.gradle.internal.needsGenDoc
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTask

// TODO create tasks to generate multiple doc types
internal class DokkaConfig(
    private val project: Project,
    private val extension: JarbirdExtension,
    private val sourceResolver: SourceResolver
) {

    private val docType = "Html"

    fun configRootDokka(pubs: List<JarbirdPub>) {

        project.logger.debug("$LOG_PREFIX Configure Dokka at root project")

        JbDokkaTaskInfo().register(project.tasks, DokkaMultiModuleTask::class.java) {
            pubs.filter { it.needsGenDoc() }.forEach { pub ->
                addSubprojectChildTasks(JbDokkaPubTaskInfo(pub).name)
            }
        }
    }

    @Suppress("SpreadOperator")
    fun configDokka(pubs: List<JarbirdPub>) {

        project.logger.debug("$LOG_PREFIX Configure Dokka for pubs")

        pubs.filter { it.needsGenDoc() }.forEach { pub ->
            val impl = pub as JarbirdPubImpl
            requireNotNull(impl.sourceSetModel()) {
                "sourceSetModel is not set"
            }.let { sourceSetModel ->
                val pubClasspath = sourceSetModel.classpath.toTypedArray()
                JbDokkaPubTaskInfo(pub).register(project.tasks, DokkaTask::class.java) {
                    dokkaSourceSets.create("${pub.pom.group}:${pub.pom.artifactId}") {
                        classpath.from(*pubClasspath)
                        sourceRoots.setFrom(
                            *(sourceSetModel.sourceFolders.toTypedArray())
                        )
                    }
                    impl.dokkaConfig.invoke(this, pub)
                }
            }
        }
    }

    @Suppress("UnstableApiUsage")
    fun setupDokkaJar(pub: JarbirdPub): TaskProvider<Jar> {

        // TODO add error message here if dokka is null
        return DokkaJarPubTaskInfo(pub)
            .also { project.logger.debug("$LOG_PREFIX Configure Dokka Jar task ${it.name}") }
            .register(project.tasks, Jar::class.java) {
                archiveClassifier.set(CLASSIFIER_JAVADOC)
                archiveBaseName.set(pub.variantArtifactId())
                archiveVersion.set(pub.variantVersion())
                from(project.tasks.named(JbDokkaPubTaskInfo(pub).name))
                dependsOn(project.tasks.named(JbDokkaPubTaskInfo(pub).name))
            }
    }
}
