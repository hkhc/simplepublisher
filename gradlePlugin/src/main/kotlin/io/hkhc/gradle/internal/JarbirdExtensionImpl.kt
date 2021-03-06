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

import groovy.lang.Closure
import io.hkhc.gradle.DocDeclaration
import io.hkhc.gradle.JarbirdExtension
import io.hkhc.gradle.JarbirdPub
import io.hkhc.gradle.RepoDeclaration
import io.hkhc.gradle.SigningStrategy
import io.hkhc.gradle.VariantStrategy
import io.hkhc.gradle.internal.pub.DocDeclarationImpl
import io.hkhc.gradle.internal.pub.RepoDeclarationsImpl
import io.hkhc.gradle.internal.pub.SigningStrategyImpl
import io.hkhc.gradle.internal.pub.VariantStrategyImpl
import io.hkhc.gradle.internal.utils.initPub
import org.gradle.api.GradleException
import org.gradle.api.Project

open class JarbirdExtensionImpl(
    val project: Project,
    protected val projectProperty: ProjectProperty,
    private val pomResolver: PomResolver
) : JarbirdExtension,
    RepoDeclaration by RepoDeclarationsImpl(project, projectProperty, getParentExt(project)),
    DocDeclaration by DocDeclarationImpl(getParentExt(project), null /* no default */),
    SigningStrategy by SigningStrategyImpl(getParentExt(project)),
    VariantStrategy by VariantStrategyImpl(getParentExt(project)) {

    val pubList = mutableListOf<JarbirdPub>()

    companion object {
        /**
         * Get the Jarbird extension of the parent project.
         * @return null if current project is root project or no Jarbird extension is found in parent project
         */
        fun getParentExt(project: Project): JarbirdExtension? {
            return if (project.isRoot()) {
                null
            } else {
                project.parent?.extensions?.findByName(SP_EXT_NAME)?.let {
                    return@getParentExt it as JarbirdExtension
                }
            }
        }
    }

    open fun newPub(project: Project, variant: String = ""): JarbirdPubImpl {
        return JarbirdPubImpl(
            project,
            projectProperty,
            variant,
            this,
            this,
            this
        ).apply {
            pubList.add(this)
        }
    }

    private fun JarbirdPub.configure(action: Closure<JarbirdPub>) {
        action.delegate = this
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    private fun JarbirdPub.configure(action: JarbirdPub.() -> Unit) {
        action.invoke(this)
    }

    fun hasDuplicatedPub(pubList: List<JarbirdPub>): Boolean {
        val pubNameSet = mutableSetOf<String>()
        pubList.forEach {
            if (pubNameSet.contains(it.pubNameWithVariant())) {
                return true
            } else {
                pubNameSet.add(it.pubNameWithVariant())
            }
        }
        return false
    }

    fun checkDuplicatedPub() {
        if (hasDuplicatedPub(pubList)) {
            throw GradleException("pubs with duplication name are found. Registered pub names are ${pubList.joinToString { it.pubNameWithVariant() }}")
        }
    }

    /*
    we call initPub in pub method after callback is invoked if variant is not available.
    we call initPub in pub method before callback is invoked if variant is available.
     */

    /* to be invoked by Groovy Gradle script */
    override fun pub(action: Closure<JarbirdPub>): JarbirdPub {
        val newPub = newPub(project)
        newPub.configure(action)
        initPub(pomResolver, pubList, newPub)
        return newPub
    }

    override fun pub(variant: String, action: Closure<JarbirdPub>): JarbirdPub {
//        if (pubList.any { it.variant == variant }) {
//            throw GradleException("Duplicated pubs with variant '$variant' are found.")
//        }
        val newPub = newPub(project, variant)
        initPub(pomResolver, pubList, newPub)
        newPub.configure(action)
        return newPub
    }

    /* to be invoked by Kotlin Gradle script */
    override fun pub(action: JarbirdPub.() -> Unit): JarbirdPub {
        val newPub = newPub(project)
        newPub.configure(action)
        initPub(pomResolver, pubList, newPub)
        return newPub
    }

    override fun pub(variant: String, action: JarbirdPub.() -> Unit): JarbirdPub {
//        if (pubList.any { it.variant == variant }) {
//            throw GradleException("Duplicated pubs with variant '$variant' are found.")
//        }
        val newPub = newPub(project, variant)
        initPub(pomResolver, pubList, newPub)
        newPub.configure(action)
        return newPub
    }

    fun finalizeRepos() {
        if (project.childProjects.isEmpty() && pubList.isEmpty()) {
            project.logger.warn("No pub has been declared in jarbird plugin extension.")
        }
        mavenLocal()
        pubList.forEach { (it as JarbirdPubImpl).finalizeRepos() }
        checkDuplicatedPub()
    }
}
