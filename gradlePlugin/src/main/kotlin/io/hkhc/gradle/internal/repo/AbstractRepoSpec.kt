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

package io.hkhc.gradle.internal.repo

import io.hkhc.gradle.RepoSpec

abstract class AbstractRepoSpec : RepoSpec {

    override fun equals(other: Any?): Boolean {

        return other?.let {

            if (this === it) return true

            val that = it as? RepoSpec ?: return false

            return releaseUrl == that.releaseUrl &&
                snapshotUrl == that.snapshotUrl &&
                username == that.username &&
                password == that.password &&
                description == that.description &&
                id == that.id
        } ?: false
    }

    override fun toString() =
        "[ ${this::class.java.name} -- releaseUrl: $releaseUrl, snapshotUrl: $snapshotUrl, username: $username, password: $password, " +
            "description: $description, id: $id]"

    override fun hashCode(): Int {
        var result = this::class.java.name.hashCode()
        result = 31 * result + releaseUrl.hashCode()
        result = 31 * result + snapshotUrl.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}
