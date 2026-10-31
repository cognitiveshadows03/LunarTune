/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.news.NewsArticle
import dev.citali.lunartune.news.NewsFeedRepository
import dev.citali.lunartune.news.formatNewsDate
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.utils.backToMain
import kotlinx.coroutines.launch

/** Route helpers so the menu, the list and the article screen cannot drift apart. */
const val NewsRoute = "news_feed"
const val NewsArticleRoutePrefix = "news_article/"

fun newsArticleRoute(slug: String): String = NewsArticleRoutePrefix + slug

/**
 * The `news` folder from the repository, newest first.
 *
 * Anything committed there as `.md` or `.txt` shows up without an app update; the
 * list and every article it has read stay available offline through the cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(force: Boolean) {
        isLoading = true
        // Cached list first, so the screen has content while the refresh runs.
        val cache = NewsFeedRepository.cached()
        if (cache.isNotEmpty()) {
            articles = cache
            isLoading = false
        }
        if (!force && cache.isNotEmpty() && !NewsFeedRepository.isStale()) {
            isLoading = false
            return
        }
        NewsFeedRepository
            .refresh()
            .onSuccess {
                articles = it
                error = null
            }.onFailure {
                // A failed refresh must never wipe what is already on screen.
                if (articles.isEmpty()) {
                    error = it.message ?: "Could not reach the news folder"
                }
            }
        isLoading = false
    }

    LaunchedEffect(Unit) { load(force = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.news_feed_title), style = MaterialTheme.typography.titleLarge)
                        if (articles.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.news_feed_count, articles.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { coroutineScope.launch { load(force = true) } }) {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = stringResource(R.string.news_feed_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                articles.isNotEmpty() ->
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(
                                    LocalPlayerAwareWindowInsets.current.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                ),
                    ) {
                        items(articles, key = { it.fileName }) { article ->
                            NewsRow(
                                article = article,
                                onClick = { navController.navigate(newsArticleRoute(article.slug)) },
                            )
                        }
                    }

                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                error != null ->
                    EmptyNews(
                        title = stringResource(R.string.news_feed_unavailable),
                        description = error.orEmpty(),
                        actionLabel = stringResource(R.string.news_feed_retry),
                        onAction = { coroutineScope.launch { load(force = true) } },
                        modifier = Modifier.align(Alignment.Center),
                    )

                else ->
                    EmptyNews(
                        title = stringResource(R.string.news_feed_empty),
                        description = stringResource(R.string.news_feed_empty_description),
                        actionLabel = stringResource(R.string.news_feed_retry),
                        onAction = { coroutineScope.launch { load(force = true) } },
                        modifier = Modifier.align(Alignment.Center),
                    )
            }
        }
    }
}

@Composable
private fun NewsRow(
    article: NewsArticle,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (article.coverUrl != null) {
                AsyncImage(
                    model = article.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                formatNewsDate(article.publishedAt)?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (article.summary.isNotBlank()) {
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNews(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.newspaper),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = actionLabel)
        }
    }
}
