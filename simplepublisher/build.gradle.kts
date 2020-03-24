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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
    jcenter()
}

val kotlin_version = "1.3.70"

plugins {
    kotlin("jvm") version "1.3.70"
    `kotlin-dsl`
    id("org.jetbrains.dokka") version "0.10.1"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
    id("io.gitlab.arturbosch.detekt") version "1.5.1"
    id("com.gradle.plugin-publish") version "0.10.1"
    `java-gradle-plugin`
    id("com.dorongold.task-tree") version "1.5"
    id("io.hkhc.simplepublisher.bootstrap") version "1.0.0"
}

val functionalTestSourceSetName = "testFunctional"

val functionalTestSourceSet = sourceSets.create(functionalTestSourceSetName) {
    withConvention(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet::class) {
        kotlin.srcDir("src/$functionalTestSourceSetName/java")
    }
    java.srcDir("src/$functionalTestSourceSetName/java")
    resources.srcDirs("src/$functionalTestSourceSetName/resources", "build/pluginUnderTestMetadata")
    compileClasspath = sourceSets["main"].output +
            configurations.named("${functionalTestSourceSetName}CompileClasspath")
    runtimeClasspath = output + compileClasspath
}

tasks {

    val functionalTestTask = register<Test>("functionalTest") {
        description = "Runs the functional tests."
        group = "verification"
        testClassesDirs = functionalTestSourceSet.output.classesDirs
        classpath = functionalTestSourceSet.runtimeClasspath
    }

    functionalTestTask.get().dependsOn(get("pluginUnderTestMetadata"))

    check { dependsOn(get("functionalTest")) }

    dokka {
        outputFormat = "html"
        outputDirectory = "$buildDir/dokka"
    }
}

ktlint {
    debug.set(true)
    verbose.set(true)
    coloredOutput.set(true)
    reporters {
        setOf(ReporterType.CHECKSTYLE, ReporterType.PLAIN)
    }
}

detekt {
    buildUponDefaultConfig = true
    config = files("detekt-config.yml")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

// tasks.withType<Test> {
//     useJUnitPlatform()
// }

simplyPublish {
    useGpg = true
    gradlePlugin = true
}

gradlePlugin {

    testSourceSets(sourceSets[functionalTestSourceSetName])
}

dependencies {

    implementation(kotlin("stdlib-jdk8", kotlin_version))
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
    embeddedKotlin(kotlin("stdlib-jdk8", kotlin_version))
    embeddedKotlin("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
    detekt(kotlin("stdlib-jdk8", kotlin_version))
    compileOnlyDependenciesMetadata("org.jetbrains.kotlin:kotlin-reflect", kotlin_version)

    // TODO extract common dependencies to a separate file

    implementation(gradleApi())
    implementation("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4")
    implementation("org.jfrog.buildinfo:build-info-extractor-gradle:4.13.0")
    implementation("org.yaml:snakeyaml:1.25")
    implementation("com.gradle.publish:plugin-publish-plugin:0.10.1")

    testImplementation("junit:junit:4.12")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.0.0-BETA1")

    "${functionalTestSourceSetName}Implementation"("junit:junit:4.12")
    "${functionalTestSourceSetName}Implementation"(gradleTestKit())
}