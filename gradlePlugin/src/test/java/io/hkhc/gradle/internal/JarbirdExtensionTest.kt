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

package io.hkhc.gradle.internal

import io.hkhc.gradle.internal.repo.MavenLocalRepoSpecImpl
import io.hkhc.gradle.internal.repo.PropertyRepoSpecBuilder
import io.hkhc.gradle.pom.PomGroup
import io.hkhc.gradle.pom.internal.PomGroupFactory
import io.hkhc.utils.test.MockProjectInfo
import io.hkhc.utils.test.MockProjectProperty
import io.hkhc.utils.test.tempDirectory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

class JarbirdExtensionTest : FunSpec({

    lateinit var project: Project
    lateinit var projectProperty: ProjectProperty
    lateinit var projectInfo: ProjectInfo
    lateinit var pomGroup: PomGroup

    fun commonPom(version: String) =
        """
            group: mygroup
            artifactId: myplugin
            version: $version
            description: Test artifact
            packaging: jar
        """.trimIndent()

    fun commonGradlePluginPom(version: String) =
        """
            group: mygroup
            artifactId: myplugin
            version: $version
            description: Test artifact
            packaging: jar
            plugin:
                id: mygroup.myplugin
                displayName: Testing Plugin
                implementationClass: plugin.class
        """.trimIndent()

    beforeTest {
        val projectDir = tempDirectory()
        project = mockk()
        every { project.rootDir } returns projectDir
        every { project.projectDir } returns File(projectDir, "module")
        every { project.logger } returns mockk<Logger>().apply {
            every { debug(any()) } returns Unit
        }

        every { project.group } returns "io.hkhc"
        every { project.name } returns "test.artifact"
        every { project.version } returns "0.1"
        every { project.description } returns "This is description"

        every { project.property("repository.bintray.username") } returns "username"
        every { project.property("repository.bintray.password") } returns "password"
        every { project.property(any()) } returns ""

        projectProperty = MockProjectProperty(
            mapOf(
                "repository.bintray.username" to "username",
                "repository.bintray.password" to "password"
            )
        )
        projectInfo = MockProjectInfo()
        File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonPom("0.1"))
        pomGroup = PomGroupFactory.resolvePomGroup(projectDir, File(projectDir, "module"))
    }

    context("initialization with no explicit pub declaration") {

        test("Plain extension should have some default repo") {
            val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
            ext.pubList.shouldBeEmpty()

            ext.createImplicit()
            ext.pubList.shouldHaveSize(1)

            ext.removeImplicit()
            ext.repos.shouldHaveSize(0)

            ext.finalizeRepos()
            ext.repos.shouldContainExactly(setOf(MavenLocalRepoSpecImpl()))
        }

        test("Implicit pub will be removed if there are explicitly declared pub") {
            val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
            ext.pubList.shouldBeEmpty()

            ext.createImplicit()
            ext.pubList.shouldHaveSize(1)

            ext.pub { }

            ext.removeImplicit()
            ext.repos.shouldHaveSize(0)

            ext.finalizeRepos()
            ext.repos.shouldContainExactly(setOf(MavenLocalRepoSpecImpl()))
        }

        test("Explicitly declared repo") {

            val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
            ext.pubList.shouldBeEmpty()

            ext.createImplicit()
            ext.pubList.shouldHaveSize(1)

            ext.bintray()

            ext.pub { }

            ext.pubList[0].needsBintray() shouldBe true
            ext.pubList.needsBintray() shouldBe true

            ext.finalizeRepos()
            ext.repos.shouldContainExactlyInAnyOrder(
                setOf(
                    MavenLocalRepoSpecImpl(),
                    PropertyRepoSpecBuilder(projectProperty).buildBintrayRepo()
                )
            )
        }

        test("Publish to maven repo") {

            listOf("0.1", "0.1-SNAPSHOT").forEach { version ->

                File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonPom(version))
                pomGroup = PomGroupFactory.resolvePomGroup(project.projectDir, File(project.projectDir, "module"))

                val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
                ext.pubList.shouldBeEmpty()

                ext.createImplicit()
                ext.pubList.shouldHaveSize(1)

                ext.mavenRepo("mock")

                ext.pub { }

                ext.removeImplicit()

                ext.finalizeRepos()

                ext.repos.shouldContainExactlyInAnyOrder(
                    setOf(MavenLocalRepoSpecImpl(), PropertyRepoSpecBuilder(projectProperty).buildMavenRepo("mock"))
                )

                if (ext.pubList[0].pom.isSnapshot()) {
                    ext.pubList.needsArtifactory() shouldBe false
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe true
                } else {
                    ext.pubList.needsArtifactory() shouldBe false
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe true
                }
            }
        }

        test("Publish to bintray") {

            listOf("0.1", "0.1-SNAPSHOT").forEach { version ->

                System.out.println("version " + version)

                File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonPom(version))
                pomGroup = PomGroupFactory.resolvePomGroup(project.projectDir, File(project.projectDir, "module"))

                val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
                ext.pubList.shouldBeEmpty()

                ext.createImplicit()
                ext.pubList.shouldHaveSize(1)

                ext.bintray()

                ext.pub { }

                ext.removeImplicit()

                ext.finalizeRepos()

                if (ext.pubList[0].pom.isSnapshot()) {

                    with(PropertyRepoSpecBuilder(projectProperty)) {
                        ext.repos.shouldContainExactlyInAnyOrder(
                            setOf(
                                MavenLocalRepoSpecImpl(),
                                buildBintrayRepo(),
                                buildBintrayRepo()
                            )
                        )

                        (ext.pubList[0] as JarbirdPubImpl).getRepos().shouldContainExactlyInAnyOrder(
                            setOf(
                                MavenLocalRepoSpecImpl(),
                                buildBintrayRepo(),
                                buildBintraySnapshotRepoSpec(buildBintrayRepo())
                            )
                        )
                    }

                    ext.pubList.needsArtifactory() shouldBe true
                    ext.pubList.needsBintray() shouldBe true
                    ext.pubList.needsNonLocalMaven() shouldBe false
                } else {
                    with(PropertyRepoSpecBuilder(projectProperty)) {
                        ext.repos.shouldContainExactlyInAnyOrder(
                            setOf(
                                MavenLocalRepoSpecImpl(),
                                buildBintrayRepo()
                            )
                        )

                        (ext.pubList[0] as JarbirdPubImpl).getRepos().shouldContainExactlyInAnyOrder(
                            setOf(
                                MavenLocalRepoSpecImpl(),
                                buildBintrayRepo()
                            )
                        )
                    }

                    ext.pubList.needsArtifactory() shouldBe false
                    ext.pubList.needsBintray() shouldBe true
                    ext.pubList.needsNonLocalMaven() shouldBe false
                }
            }
        }

        test("Publish to maven repo and bintray") {

            listOf("0.1", "0.1-SNAPSHOT").forEach { version ->

                File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonPom(version))

                val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
                ext.pubList.shouldBeEmpty()

                ext.createImplicit()
                ext.pubList.shouldHaveSize(1)

                ext.mavenRepo("mock")
                ext.bintray()

                ext.pub { }

                ext.removeImplicit()

                ext.finalizeRepos()

                ext.repos.shouldContainExactlyInAnyOrder(
                    setOf(
                        MavenLocalRepoSpecImpl(),
                        PropertyRepoSpecBuilder(projectProperty).buildBintrayRepo(),
                        PropertyRepoSpecBuilder(projectProperty).buildMavenRepo("mock")
                    )
                )

                if (ext.pubList[0].pom.isSnapshot()) {
                    ext.pubList.needsArtifactory() shouldBe true
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe true
                } else {
                    ext.pubList.needsArtifactory() shouldBe false
                    ext.pubList.needsBintray() shouldBe true
                    ext.pubList.needsNonLocalMaven() shouldBe true
                }
            }
        }
        test("Publish to artifactory") {

            listOf("0.1", "0.1-SNAPSHOT").forEach { version ->

                File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonPom(version))

                val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
                ext.pubList.shouldBeEmpty()

                ext.createImplicit()
                ext.pubList.shouldHaveSize(1)

                ext.artifactory()

                ext.pub { }

                ext.removeImplicit()

                ext.finalizeRepos()

                ext.repos.shouldContainExactlyInAnyOrder(
                    setOf(MavenLocalRepoSpecImpl(), PropertyRepoSpecBuilder(projectProperty).buildArtifactoryRepo())
                )

                if (ext.pubList[0].pom.isSnapshot()) {
                    ext.pubList.needsArtifactory() shouldBe true
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe false
                } else {
                    ext.pubList.needsArtifactory() shouldBe true
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe false
                }
            }
        }

        test("Publish to maven repo and artifactory") {

            listOf("0.1", "0.1-SNAPSHOT").forEach { version ->

                File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonPom(version))

                val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
                ext.pubList.shouldBeEmpty()

                ext.createImplicit()
                ext.pubList.shouldHaveSize(1)

                ext.mavenRepo("mock")
                ext.artifactory()

                ext.pub { }

                ext.removeImplicit()

                ext.finalizeRepos()

                ext.repos.shouldContainExactlyInAnyOrder(
                    setOf(
                        MavenLocalRepoSpecImpl(),
                        PropertyRepoSpecBuilder(projectProperty).buildArtifactoryRepo(),
                        PropertyRepoSpecBuilder(projectProperty).buildMavenRepo("mock")
                    )
                )

                if (ext.pubList[0].pom.isSnapshot()) {
                    ext.pubList.needsArtifactory() shouldBe true
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe true
                } else {
                    ext.pubList.needsArtifactory() shouldBe true
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe true
                }
            }
        }
        test("Publish gradle plugin to bintray is supported with release version") {

            listOf("0.1", "0.1-SNAPSHOT").forEach { version ->

                File(project.projectDir.also { it.mkdirs() }, "pom.yml").writeText(commonGradlePluginPom(version))

                val ext = JarbirdExtensionImpl(project, projectProperty, projectInfo, pomGroup)
                ext.pubList.shouldBeEmpty()

                ext.createImplicit()
                ext.pubList.shouldHaveSize(1)

                ext.bintray()

                ext.pub { }

                ext.removeImplicit()

                ext.finalizeRepos()

                ext.repos.shouldContainExactlyInAnyOrder(
                    setOf(MavenLocalRepoSpecImpl(), PropertyRepoSpecBuilder(projectProperty).buildBintrayRepo())
                )

                if (ext.pubList[0].pom.isSnapshot()) {
                    ext.pubList.needsArtifactory() shouldBe false
                    ext.pubList.needsBintray() shouldBe false
                    ext.pubList.needsNonLocalMaven() shouldBe false
                } else {
                    ext.pubList.needsArtifactory() shouldBe false
                    ext.pubList.needsBintray() shouldBe true
                    ext.pubList.needsNonLocalMaven() shouldBe false
                }
            }
        }
    }
})