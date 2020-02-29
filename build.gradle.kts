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

import io.hkhc.gradle.simplyPublish
import io.hkhc.gradle.PublishConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()

        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {

        classpath("io.hkhc.gradle:simplepublisher:0.2")
    }
}

repositories {
    mavenCentral()
    jcenter()

//    maven {
//        url = uri("http://oss.jfrog.org/oss-snapshot-local")
//    }
}

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.3.61"
    id("org.jetbrains.dokka") version "0.10.1"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
    id("io.gitlab.arturbosch.detekt") version "1.5.1"
    `maven-publish`
    signing
    id("com.jfrog.bintray") version "1.8.4"
    id("com.jfrog.artifactory") version "4.13.0"
    id("com.gradle.plugin-publish") version "0.10.1"
    id("java-gradle-plugin")
}

apply(plugin = "io.hkhc.simplepublisher")

val pubConfig = PublishConfig(project)

tasks {
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

group = pubConfig.artifactGroup!!
version = pubConfig.effectiveArtifactVersion

simplyPublish {
    publish()
}

pluginBundle {
    website = pubConfig.pomUrl
    vcsUrl = pubConfig.scmUrl
    tags = listOf("publish")
}

gradlePlugin {
    plugins {
        create("simplepublisher") {
            id = "io.hkhc.simplepublisher"
            displayName = "Plugin to make publishing artifacts easy."
            description = "Wrapping build script for major repositories and make simple things as simple as possible"
            implementationClass = "io.hkhc.gradle.SimplePublisherPlugin"
        }
    }
}

dependencies {

    implementation(kotlin("stdlib-jdk8"))

    implementation(gradleApi())
    implementation("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4")
    implementation("org.jfrog.buildinfo:build-info-extractor-gradle:4.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.14.0")
    implementation("com.charleskorn.kaml:kaml:0.15.0")
}
