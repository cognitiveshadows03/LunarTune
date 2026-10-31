/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.news.NewsArticle
import dev.citali.lunartune.news.NewsFeedRepository
import dev.citali.lunartune.news.formatNewsDate
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.MarkdownText
import dev.citali.lunartune.ui.utils.backToMain

/**
 * One article from the `news` folder. Markdown is rendered with Markwon (the same
 * renderer the changelog uses, images included); `.txt` is shown verbatim in a
 * monospace block so nothing in it is interpreted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsArticleScreen(
    navController: NavController,
    slug: String,
) {
    var article by remember(slug) { mutableStateOf<NewsArticle?>(null) }
    var isLoading by remember(slug) { mutableStateOf(true) }
    var error by remember(slug) { mutableStateOf<String?>(null) }

    LaunchedEffect(slug) {
        // Cached list first: opening an article you have already seen is instant.
        val cached = NewsFeedRepository.article(NewsFeedRepository.cached(), slug)
        if (cached != null) {
            article = cached
            isLoading = false
            return@LaunchedEffect
        }
        NewsFeedRepository
            .fetchOne(slug)
            .onSuccess { article = it }
            .onFailure { error = it.message ?: "Could not load this article" }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.title ?: stringResource(R.string.news_feed_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
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
            val current = article
            when {
                current != null ->
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(
                                    LocalPlayerAwareWindowInsets.current.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                ),
                    ) {
                        item {
                            Column {
                                Text(
                                    text = current.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                formatNewsDate(current.publishedAt)?.let { date ->
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        item {
                            if (current.isMarkdown) {
                                // Markwon renders headings, lists, tables, links and images
                                // (including the relative ones the repository resolves for us).
                                MarkdownText(
                                    markdown = current.body,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            } else {
                                SelectionContainer {
                                    Text(
                                        text = current.body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                else ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.news_feed_unavailable),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
            }
        }
    }
}
