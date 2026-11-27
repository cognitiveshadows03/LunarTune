/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk shape of a module that was not compiled into the build.
 *
 * A module arrives as one JSON document. It is deliberately the same vocabulary
 * the in-app modules use — id, name, description, choices, fields, plus one
 * endpoint per capability — so a loaded module and a built-in one look identical
 * to the rest of the app.
 *
 * What a module is NOT allowed to do is run code. There is no script here, no
 * expression language, nothing to evaluate: a module can only describe HTTP
 * requests and how to read the answers. That keeps three things true at once —
 * nothing arbitrary executes inside the app, the whole contract can be validated
 * before a module is accepted, and a hostile module can only be as dangerous as a
 * hostile URL.
 */
@Serializable
data class RemoteModuleDocument(
    /** bumped when this shape changes in a way older modules cannot express */
    val schema: Int = SUPPORTED_SCHEMA,
    val id: String,
    val name: String,
    val version: String = "",
    val description: String = "",
    val author: String? = null,
    val homepage: String? = null,
    /** capability -> what to do; only the ones present are offered */
    val capabilities: RemoteCapabilities = RemoteCapabilities(),
    val choices: List<RemoteChoice> = emptyList(),
    val fields: List<RemoteField> = emptyList(),
) {
    companion object {
        const val SUPPORTED_SCHEMA = 1

        /** ids are used as preference-key fragments, so they stay in a safe alphabet */
        private val IdPattern = Regex("[a-z0-9][a-z0-9._-]{0,63}")

        val json: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = false
                encodeDefaults = true
            }

        /**
         * Reads a document, or explains why it is not one.
         *
         * Every failure here is a sentence a person can act on: a module that
         * silently fails to load and shows nothing is worse than one that says
         * what is wrong with it.
         */
        fun parse(text: String): Result<RemoteModuleDocument> =
            runCatching {
                val document = json.decodeFromString<RemoteModuleDocument>(text)
                document.validate()
                document
            }
    }

    /** the reasons a document cannot be used, checked before it is stored */
    fun validate() {
        require(id.isNotBlank()) { "the module has no id" }
        require(id.matches(IdPattern)) {
            "the id \"$id\" must be lowercase letters, digits, dot, dash or underscore"
        }
        require(name.isNotBlank()) { "the module has no name" }
        require(schema == SUPPORTED_SCHEMA) {
            "this module needs schema $schema, this build reads $SUPPORTED_SCHEMA"
        }
        require(capabilities.stream != null || capabilities.search != null) {
            "the module offers neither a stream nor a search, so it cannot play anything"
        }
        capabilities.stream?.let { it.validate("stream") }
        capabilities.search?.let { it.validate("search") }
        choices.forEach { choice ->
            require(choice.id.isNotBlank()) { "a choice has no id" }
            require(choice.options.isNotEmpty()) { "the choice \"${choice.id}\" has no options" }
        }
        fields.forEach { field ->
            require(field.id.isNotBlank()) { "a field has no id" }
            require(field.kind != RemoteFieldKind.Select || field.options.isNotEmpty()) {
                "the select field \"${field.id}\" has no options"
            }
        }
        val duplicateIds = choices.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }
        require(duplicateIds.isEmpty()) { "two choices share an id: ${duplicateIds.keys.joinToString()}" }
        val duplicateFields = fields.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }
        require(duplicateFields.isEmpty()) { "two fields share an id: ${duplicateFields.keys.joinToString()}" }
    }

}

/** The things a module can be asked to do. */
@Serializable
data class RemoteCapabilities(
    /** find the track, then read a stream out of the answer */
    val stream: RemoteStreamCapability? = null,
    /** a module that only answers searches, without serving audio */
    val search: RemoteSearchCapability? = null,
)

/**
 * Everything needed to turn a track into a playable URL, in two steps.
 *
 * [search] finds the service's own record for the track, [stream] then reads the
 * audio out of it. They are separate because that is how the services actually
 * work: the record and the playable URL are different endpoints, and a module
 * that skipped the record would have nothing to match against.
 */
@Serializable
data class RemoteStreamCapability(
    val search: RemoteRequestTemplate,
    val stream: RemoteRequestTemplate,
    /** how to get from the search answer to the ids the stream step needs */
    val match: RemoteMatch = RemoteMatch(),
    /** how to read the stream address out of the stream answer */
    val extract: RemoteExtract,
    /** codec name to report for the player's badge, when the module knows it */
    val codec: String? = null,
    /** quality wording shown next to the source ("320 kbps") */
    val quality: String = "",
) {
    fun validate(where: String) {
        search.validate("$where.search")
        stream.validate("$where.stream")
        extract.validate("$where.extract")
    }
}

/** A module that only searches: useful for lookups, cannot serve audio by itself. */
@Serializable
data class RemoteSearchCapability(
    val search: RemoteRequestTemplate,
    val extract: RemoteExtract,
) {
    fun validate(where: String) {
        search.validate("$where.search")
        extract.validate("$where.extract")
    }
}

/**
 * A request to make, with holes to fill in.
 *
 * Placeholders use `{name}` and are replaced from the track being played or from
 * values an earlier step produced. Only `{http://…}` and `{https://…}` addresses
 * survive validation: a module may not be able to reach `file:`, `content:` or
 * anything else that lives inside the device.
 */
@Serializable
data class RemoteRequestTemplate(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    /** a body template; a JSON body is the common case */
    val body: String? = null,
    val contentType: String? = null,
    /**
     * an API key to append as a query parameter, for services that put the key
     * in the URL rather than a header — which is most of them
     */
    val keyParam: String? = null,
    /** the field that holds that key, when the service wants one */
    val keyField: String? = null,
) {
    fun validate(where: String) {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "$where has an address that is not http(s): $url"
        }
        require(method.uppercase() in AllowedMethods) { "$where uses an unsupported method: $method" }
    }

    private companion object {
        val AllowedMethods = setOf("GET", "POST", "PUT", "HEAD")
    }
}

/**
 * How to pick the right record out of a search answer.
 *
 * [idPath] is walked to the list of results, then each result is tested against
 * the track: the title and artist must contain what they are asked to contain,
 * and the duration, when both sides know it, must be within tolerance. A module
 * that names no paths at all still works — the first usably-shaped object wins —
 * but naming them is what makes the match trustworthy.
 */
@Serializable
data class RemoteMatch(
    /** where the list of candidate records lives in the answer */
    val resultsPath: String = "",
    /** where the service's id sits inside one candidate */
    val idPath: String = "id",
    val titlePath: String = "title",
    val artistPath: String = "artist",
    val albumPath: String = "album",
    val durationPath: String = "duration",
    /** the field the stream URL should be looked up by, when it is not [idPath] */
    val streamIdPath: String = "",
)

/**
 * How to read values out of an answer.
 *
 * [path] accepts dotted keys and array indexes, which covers every shape the
 * services actually return: `data.songs[0].media.url`. [base64] handles the
 * services that hand back an encoded address instead of a plain one.
 */
@Serializable
data class RemoteExtract(
    /** where the playable address is */
    val urlPath: String,
    /** an optional second address, tried when the first is absent */
    val fallbackUrlPath: String = "",
    /** dotted path to either a full URL or a path to prefix with [urlPrefix] */
    val urlPrefix: String = "",
    /** read the address as base64 first */
    val base64: Boolean = false,
    /** how the audio is delivered; anything unrecognised is treated as progressive */
    val format: String = "progressive",
    val headersPath: String = "",
    val codecPath: String = "",
    val bitratePath: String = "",
    val sampleRatePath: String = "",
) {
    fun validate(where: String) {
        require(urlPath.isNotBlank()) { "$where does not say where the stream address is" }
    }
}

@Serializable
data class RemoteChoice(
    val id: String,
    val label: String,
    /** the field the picked option is substituted into, as `{picks.<id>}` */
    val options: List<RemoteOption>,
)

@Serializable
data class RemoteOption(
    val id: String,
    val label: String,
    val description: String = "",
)

@Serializable
enum class RemoteFieldKind {
    @SerialName("text") Text,
    @SerialName("password") Password,
    @SerialName("select") Select,
    @SerialName("toggle") Toggle,
}

@Serializable
data class RemoteField(
    val id: String,
    val label: String,
    val kind: RemoteFieldKind = RemoteFieldKind.Text,
    val default: String = "",
    val hint: String = "",
    val description: String = "",
    /** for a select: the values it can take */
    val options: List<RemoteOption> = emptyList(),
)
