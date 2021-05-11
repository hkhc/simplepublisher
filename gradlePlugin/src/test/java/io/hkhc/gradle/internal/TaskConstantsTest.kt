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

import groovy.lang.MissingPropertyException
import io.hkhc.gradle.JarbirdPub
import io.hkhc.gradle.internal.repo.PropertyRepoSpecBuilder
import io.hkhc.gradle.pom.Pom
import io.hkhc.utils.test.MockProjectProperty
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class TaskConstantsTest: FunSpec( {

    class MapProjectProperty(private val map: Map<String,Any>) : ProjectProperty {
        override fun property(name: String): Any {
            println("map property '$name")
            return map[name] ?:
            throw MissingPropertyException("Cannot resolve property '$name'")
        }
    }

    val pub = mockk<JarbirdPub>()
    val pubPlugin = mockk<JarbirdPub>()
    val repoBuilder = PropertyRepoSpecBuilder(MockProjectProperty(mapOf(
        "repository.maven.mock.release" to "releaseUrl",
        "repository.maven.mock.snapshot" to "snapshotUrl"
    )))

    val repo = repoBuilder.buildMavenRepo("mock")

    beforeSpec() {
        val pom = mockk<Pom>()
        every { pom.isGradlePlugin() } returns false

        every { pub.pubName } returns "abc"
        every { pub.variant } returns "var"
        every { pub.getGAV() } returns "group:artifact:1.0"
        every { pub.pluginCoordinate() } returns "NOT-A-PLUGIN"
        every { pub.pom } returns pom

        val pomPlugin = mockk<Pom>()
        every { pomPlugin.isGradlePlugin() } returns true

        every { pubPlugin.pubName } returns "abc"
        every { pubPlugin.variant } returns "var"
        every { pubPlugin.getGAV() } returns "group:artifact:1.0"
        every { pubPlugin.pluginCoordinate() } returns "group.artifact.plugin"
        every { pubPlugin.pom } returns pomPlugin

    }

    test("publish all") {
        Publish.taskName shouldBe "publish"
    }

    test("publish one pub") {
        Publish.pub(pub).taskName shouldBe "publishAbcVarPublication"
        Publish.pub(pub).to.mavenLocal.taskName shouldBe "publishAbcVarPublicationToMavenLocal"
    }

    test("publish all to one repo") {
        Publish.to.mavenLocal.taskName shouldBe "publishToMavenLocal"
        Publish.to.mavenRepo(repo).taskName shouldBe "publishToMavenMockRepository"
    }

    test("publish plugin marker of one pub") {
        Publish.pluginMarker(pub).taskName shouldBe
            "publishAbcVarPluginMarkerMavenPublication"
        Publish.pluginMarker(pub).to.mavenLocal.taskName shouldBe
            "publishAbcVarPluginMarkerMavenPublicationToMavenLocal"
    }

    test("publish to custom maven repo") {
        Publish.pub(pub).to.mavenRepo(repo).taskName shouldBe
            "publishAbcVarPublicationToMavenMockRepository"
        Publish.pluginMarker(pub).to.mavenRepo(repo).taskName shouldBe
            "publishAbcVarPluginMarkerMavenPublicationToMavenMockRepository"
    }

    test("Jarbird publish task info") {
        JbPublish.taskInfo shouldBe JbPublish.SimpleTaskInfo("jbPublish", "Publish")
    }

    test("jbPublish one pub task info") {
        JbPublish.pub(pub).taskInfo shouldBe
            JbPublish.SimpleTaskInfo(
                "jbPublishAbcVar",
                "Publish module 'abc' (var) to all targeted repositories"
            )
        JbPublish.pub(pub).to.mavenLocal.taskInfo shouldBe
            JbPublish.SimpleTaskInfo(
                "jbPublishAbcVarToMavenLocal",
                "Publish module 'abc' (var) to Maven Local repository"
            )
        JbPublish.pub(pubPlugin).taskInfo shouldBe
            JbPublish.SimpleTaskInfo(
                "jbPublishAbcVar",
                "Publish module 'abc' (var) to all targeted repositories"
            )
    }

    test("Jarbird publish all to one repo") {
        JbPublish.to.mavenLocal.taskInfo shouldBe
            JbPublish.SimpleTaskInfo("jbPublishToMavenLocal", "Publish to Maven Local repository")
        JbPublish.to.mavenRepo(repo).taskInfo shouldBe
            JbPublish.SimpleTaskInfo("jbPublishToMavenMockRepository", "Publish to Maven repository 'mock'")
        JbPublish.to.mavenRepo.taskInfo shouldBe
            JbPublish.SimpleTaskInfo("jbPublishToMavenRepository", "Publish to all Maven repositories")
    }

//    test("Jarbird publish plugin marker of one pub") {
//        JbPublish.pluginMarker(pub).taskInfo shouldBe
//            JbPublish.SimpleTaskInfo(
//            "jbPublishAbcVarPluginMarkerMavenPublication",
//            "Publish Abc (var) plugin marker publication to all repositories"
//            )
//        JbPublish.pluginMarker(pub).to.mavenLocal.taskInfo shouldBe
//            JbPublish.SimpleTaskInfo(
//                "jbPublishAbcVarPluginMarkerMavenPublicationToMavenLocal",
//                "Publish Abc (var) plugin marker publication to Maven Local repository"
//            )
//    }

    test("JbPublish to custom maven repo") {
        JbPublish.pub(pub).to.mavenRepo(repo).taskInfo shouldBe
            JbPublish.SimpleTaskInfo(
                "jbPublishAbcVarToMavenMockRepository",
                "Publish module 'abc' (var) to Maven repository 'mock'"
            )
//        JbPublish.pluginMarker(pub).to.mavenRepo(repo).taskInfo shouldBe
//            JbPublish.SimpleTaskInfo(
//                "jbPublishAbcVarPluginMarkerMavenPublicationToMavenMockRepository",
//                "Publish Abc (var) plugin marker publication to Maven repository 'mock'"
//            )
    }



})