/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.news

import androidx.datastore.preferences.core.edit
import dev.citali.lunartune.App
import dev.citali.lunartune.constants.NewsCacheKey
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.getAsync
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One article from the `news` folder in the repository.
 *
 * The folder is the source of truth: anything dropped there as `.md` or `.txt`
 * shows up in the app without a release, because the app reads the folder over
 * the network and caches what it read.
 */
@Serializable
data class NewsArticle(
    /** File name including its extension; also the route argument. */
    val fileName: String,
    val title: String,
    val summary: String,
    val body: String,
    val isMarkdown: Boolean,
    /** `yyyy-MM-dd` taken from the file name when it starts with one. */
    val publishedAt: String? = null,
    /** First image in the article, resolved to an absolute URL. Used as the row thumbnail. */
    val coverUrl: String? = null,
) {
    val slug: String get() = fileName.substringBeforeLast('.')
}

@Serializable
private data class NewsCache(
    val updatedAt: Long = 0L,
    val articles: List<NewsArticle> = emptyList(),
)

/**
 * Reads the repository's `news` folder (kept separate from the older
 * `repository.NewsRepository`, which reads the upstream JSON feed).
 *
 * Listing needs the contents API (one request); the article bodies are pulled
 * from raw.githubusercontent, which is not rate limited the way the API is.
 * Everything read is cached in DataStore so the list and articles open offline
 * and so a rate-limited or offline refresh still shows the last known news.
 */
object NewsFeedRepository {
    private const val Owner = "cognitiveshadows03"
    private const val Repo = "LunarTune"
    private const val Branch = "main"
    private const val Folder = "news"

    /** Refresh at most this often unless the user asks again explicitly. */
    private const val CacheTtlMs = 30 * 60 * 1000L

    /** Safety valve: a runaway folder should not turn one refresh into hundreds of requests. */
    private const val MaxArticles = 60

    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val listingUrl =
        "https://api.github.com/repos/$Owner/$Repo/contents/$Folder?ref=$Branch"
    private val rawBase =
        "https://raw.githubusercontent.com/$Owner/$Repo/$Branch/$Folder/"
    private val rawRoot =
        "https://raw.githubusercontent.com/$Owner/$Repo/$Branch"

    private val datePrefix = Regex("^(\\d{4}-\\d{2}-\\d{2})-?")
    private val headingLine = Regex("^#{1,2}\\s+(.+?)\\s*#*$")
    private val imageLink = Regex("""!\[([^\]]*)]\(\s*([^)\s]+)([^)]*)\)""")
    private val plainLink = Regex("""(?<!!)\[([^\]]*)]\(\s*([^)\s]+)([^)]*)\)""")
    private val imageTag = Regex("""(<img[^>]*?src=["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
    private val markdownNoise =
        Regex("""!\[[^\]]*]\([^)]*\)|\[([^\]]*)]\([^)]*\)|[*_`>#]+|<[^>]+>""")

    /** Whatever was cached last, newest first. Empty when the app has never loaded the folder. */
    suspend fun cached(): List<NewsArticle> = readCache().articles

    suspend fun isStale(): Boolean = System.currentTimeMillis() - readCache().updatedAt > CacheTtlMs

    /**
     * Refresh from the repository. Returns the freshly read articles, or a failure
     * whose cause the caller can show. A cached copy is left untouched on failure.
     */
    suspend fun refresh(): Result<List<NewsArticle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val files = fetchListing()
                val articles =
                    files
                        .take(MaxArticles)
                        .mapNotNull { file -> fetchArticle(file.name) }
                        .sortedWith(
                            // Dated files newest first; undated ones after them, by name.
                            compareByDescending<NewsArticle> { it.publishedAt ?: "" }
                                .thenByDescending { it.fileName.lowercase(Locale.ROOT) },
                        )
                writeCache(articles)
                articles
            }
        }

    fun article(articles: List<NewsArticle>, slug: String): NewsArticle? =
        articles.firstOrNull { it.slug == slug }

    /** Fetches a single article straight from the repository, for a cold deep link. */
    suspend fun fetchOne(slug: String): Result<NewsArticle> =
        withContext(Dispatchers.IO) {
            runCatching {
                listOf("$slug.md", "$slug.txt")
                    .firstNotNullOfOrNull { name -> fetchArticle(name) }
                    ?: error("Article not found")
            }
        }

    private data class NewsFile(
        val name: String,
    )

    private suspend fun fetchListing(): List<NewsFile> {
        val response = client.get(listingUrl)
        if (!response.status.isSuccess()) {
            error("GitHub answered ${response.status.value}")
        }
        val root = json.parseToJsonElement(response.bodyAsText())
        val entries = root as? JsonArray ?: error("Unexpected listing")
        return entries.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val isArticle =
                type == "file" &&
                    !name.startsWith(".") &&
                    !name.startsWith("_") &&
                    // The folder keeps its own README for whoever edits it; it is not news.
                    !name.equals("README.md", ignoreCase = true) &&
                    extension in setOf("md", "txt")
            if (isArticle) NewsFile(name) else null
        }
    }

    private suspend fun fetchText(fileName: String): String? =
        runCatching {
            val encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val response = client.get(rawBase + encoded)
            if (response.status.isSuccess()) response.bodyAsText() else null
        }.getOrNull()

    private suspend fun fetchArticle(fileName: String): NewsArticle? {
        val text = fetchText(fileName) ?: return null
        val isMarkdown = fileName.endsWith(".md", ignoreCase = true)
        val body = if (isMarkdown) absolutize(text) else text
        val rawTitle = extractTitle(text, fileName)
        return NewsArticle(
            fileName = fileName,
            title = rawTitle,
            summary = extractSummary(text),
            body = body,
            isMarkdown = isMarkdown,
            publishedAt = extractDate(fileName),
            coverUrl = Regex("""!\[[^\]]*]\(\s*([^)\s]+)""").find(body)?.groupValues?.getOrNull(1),
        )
    }

    private fun extractTitle(
        text: String,
        fileName: String,
    ): String {
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { headingLine.matches(it) }
            ?.let { line -> headingLine.find(line)?.groupValues?.getOrNull(1) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return stripMarkdown(it) }

        // No heading: fall back to the file name, minus its date prefix.
        return fileName
            .substringBeforeLast('.')
            .replace(datePrefix, "")
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
    }

    private fun extractSummary(text: String): String =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotEmpty() &&
                    !line.startsWith("#") &&
                    !line.startsWith("![") &&
                    !line.startsWith("<img", ignoreCase = true) &&
                    !line.startsWith("```") &&
                    !line.startsWith("|") &&
                    !line.startsWith("---")
            }?.let { stripMarkdown(it) }
            ?.take(180)
            .orEmpty()

    private fun stripMarkdown(text: String): String =
        markdownNoise.replace(text, "$1").replace(Regex("\\s+"), " ").trim()

    private fun extractDate(fileName: String): String? =
        datePrefix.find(fileName)?.groupValues?.getOrNull(1)?.takeIf { it.length == 10 }

    /**
     * Relative references in an article point at files next to it in the folder, so
     * `![banner](images/banner.png)` has to become a raw.githubusercontent URL
     * before Markwon's image plugin can load it.
     */
    private fun absolutize(markdown: String): String {
        fun resolve(path: String): String =
            when {
                path.startsWith("http://") ||
                    path.startsWith("https://") ||
                    path.startsWith("data:") ||
                    path.startsWith("content:") -> path
                path.startsWith("/") -> rawRoot + path
                else -> rawBase + path.removePrefix("./")
            }

        val withImages =
            imageLink.replace(markdown) { match ->
                "![${match.groupValues[1]}](${resolve(match.groupValues[2])}${match.groupValues[3]})"
            }
        val withLinks =
            plainLink.replace(withImages) { match ->
                "[${match.groupValues[1]}](${resolve(match.groupValues[2])}${match.groupValues[3]})"
            }
        return imageTag.replace(withLinks) { match ->
            match.groupValues[1] + resolve(match.groupValues[2]) + match.groupValues[3]
        }
    }

    private suspend fun readCache(): NewsCache =
        runCatching {
            App.instance.dataStore.getAsync(NewsCacheKey)?.let {
                json.decodeFromString(NewsCache.serializer(), it)
            }
        }.getOrNull() ?: NewsCache()

    private suspend fun writeCache(articles: List<NewsArticle>) {
        runCatching {
            val payload = json.encodeToString(NewsCache.serializer(), NewsCache(System.currentTimeMillis(), articles))
            App.instance.dataStore.edit { it[NewsCacheKey] = payload }
        }
    }
}

/** `2026-10-31` shown in the device's date format; anything unparseable is shown as-is. */
fun formatNewsDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        LocalDate
            .parse(raw)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
    }.getOrDefault(raw)
}
