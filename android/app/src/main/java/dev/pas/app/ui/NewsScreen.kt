package dev.pas.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.pas.app.CoachMessage
import dev.pas.app.Digest
import dev.pas.app.DigestItem
import dev.pas.app.Story
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAYS_OF_DAILY_NEWS = 7

@Composable
fun NewsScreen(
    digests: List<Digest>,
    messages: List<CoachMessage>,
    readStoryIds: Set<String>,
    onOpenStory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDigestIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedDigest = digests.getOrNull(selectedDigestIndex)
    val oldestDailyNewsDay = LocalDate.now().minusDays(DAYS_OF_DAILY_NEWS.toLong())
    val messagesWithNews = messages
        .filter { it.news.isNotEmpty() && !localDateOf(it.createdAt).isBefore(oldestDailyNewsDay) }
        .reversed()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = "From the world of AI",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
        )
        Text(
            text = "Three things to understand in depth, then the rest of the week.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        if (digests.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                digests.forEachIndexed { index, digest ->
                    FilterChip(
                        selected = index == selectedDigestIndex,
                        onClick = { selectedDigestIndex = index },
                        label = { Text(shortDate(digest.id)) },
                    )
                }
            }
        }

        if (selectedDigest == null) {
            PasCard(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = Pas.decor.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "The first digest arrives on Sunday. Until then, today's news is below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(22.dp),
                )
            }
        } else if (selectedDigest.stories.isEmpty()) {
            DigestSection(label = "In brief", items = selectedDigest.summary, accent = MaterialTheme.colorScheme.primary)
            DigestSection(label = "Worth watching", items = selectedDigest.watch, accent = MaterialTheme.colorScheme.tertiary)
            DigestSection(label = "Worth reading", items = selectedDigest.reading, accent = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DigestWithStories(digest = selectedDigest, readStoryIds = readStoryIds, onOpenStory = onOpenStory)
        }

        if (messagesWithNews.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Day by day, from the morning messages")
            for (message in messagesWithNews) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = dayLabel(message.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (newsItem in message.news) {
                    NewsBullet(
                        text = AnnotatedString(newsItem.text),
                        caption = newsItem.source,
                        accent = MaterialTheme.colorScheme.tertiary,
                        onClick = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DigestWithStories(digest: Digest, readStoryIds: Set<String>, onOpenStory: (String) -> Unit) {
    val coreStories = digest.stories.filter { it.coreRank != null }.sortedBy { it.coreRank }
    val readCoreCount = coreStories.count { it.id in readStoryIds }
    val storiesBySection = digest.stories.groupBy { it.section }

    if (coreStories.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            SectionLabel("To understand this week", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(
                text = "$readCoreCount of ${coreStories.size} read",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        coreStories.forEachIndexed { index, story ->
            CoreStoryCard(
                story = story,
                isRead = story.id in readStoryIds,
                onClick = { onOpenStory(story.id) },
                modifier = Modifier
                    .enterInOrder(index)
                    .padding(bottom = 12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    DigestSection(label = "In brief", items = digest.summary, accent = MaterialTheme.colorScheme.primary)

    SectionLabel("All of this week's news")
    Spacer(Modifier.height(12.dp))
    for ((section, stories) in storiesBySection) {
        StorySectionCard(section = section, stories = stories, readStoryIds = readStoryIds, onOpenStory = onOpenStory)
    }

    DigestSection(label = "Worth watching", items = digest.watch, accent = MaterialTheme.colorScheme.tertiary)
}

/** A big numbered card: the title, why it matters, and how long it takes, so starting feels small. */
@Composable
private fun CoreStoryCard(story: Story, isRead: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val whyItMatters = story.parts.find { it.label == WHY_IT_MATTERS_LABEL }?.text ?: story.intro
    HeroCard(
        modifier = modifier
            .pressScale(interactionSource)
            .fillMaxWidth()
            .clip(Pas.decor.hero)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp)) {
            Text(
                text = "${story.coreRank}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = storyPlace(story),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(4.dp))
                Text(text = story.title, style = MaterialTheme.typography.titleMedium)
                whyItMatters?.let { text ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = withBoldMarks(text),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isRead) "${story.readMinutes} min · read ✓" else "${story.readMinutes} min · open",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

private const val WHY_IT_MATTERS_LABEL = "Why it matters"

@Composable
private fun StorySectionCard(section: String, stories: List<Story>, readStoryIds: Set<String>, onOpenStory: (String) -> Unit) {
    var isExpanded by rememberSaveable(section) { mutableStateOf(false) }
    val visibleStories = if (isExpanded) stories else stories.take(COLLAPSED_ITEM_COUNT)
    PasCard(
        color = MaterialTheme.colorScheme.surface,
        shape = Pas.decor.card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(
            Modifier
                .animateContentSize()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        ) {
            SectionLabel(section, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(4.dp))
            for (story in visibleStories) {
                StoryRow(story = story, isRead = story.id in readStoryIds, onClick = { onOpenStory(story.id) })
            }
            if (stories.size > COLLAPSED_ITEM_COUNT) {
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Less" else "${stories.size - COLLAPSED_ITEM_COUNT} more")
                }
            }
        }
    }
}

@Composable
private fun StoryRow(story: Story, isRead: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            story.topic?.let { topic ->
                Text(
                    text = "$topic · ${story.readMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (isRead) "✓" else "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Three bullets are enough for a glance; the rest waits behind "More". */
private const val COLLAPSED_ITEM_COUNT = 3

@Composable
private fun DigestSection(label: String, items: List<DigestItem>, accent: Color) {
    if (items.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    var isExpanded by rememberSaveable(label) { mutableStateOf(false) }
    val visibleItems = if (isExpanded) items else items.take(COLLAPSED_ITEM_COUNT)
    PasCard(
        color = MaterialTheme.colorScheme.surface,
        shape = Pas.decor.card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(
            Modifier
                .animateContentSize()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        ) {
            SectionLabel(label, color = accent)
            for (item in visibleItems) {
                NewsBullet(
                    text = withBoldHeadline(item.text),
                    caption = item.url?.let { "Open ↗" },
                    accent = accent,
                    onClick = item.url?.let { url -> { uriHandler.openUri(url) } },
                )
            }
            if (items.size > COLLAPSED_ITEM_COUNT) {
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Less" else "More (${items.size - COLLAPSED_ITEM_COUNT})")
                }
            }
        }
    }
}

@Composable
private fun NewsBullet(text: AnnotatedString, caption: String?, accent: Color, onClick: (() -> Unit)?) {
    val rowModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = accent,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp),
        ) {}
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (onClick == null) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The digest writes each item as "Headline: details" or "Headline. Details"; the headline reads better in bold. */
private fun withBoldHeadline(text: String): AnnotatedString {
    val headlineEnd = listOf(text.indexOf(": "), text.indexOf(". "))
        .filter { it in 1..140 }
        .minOrNull()
    if (headlineEnd == null) return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(0, headlineEnd + 1)) }
        append(text.substring(headlineEnd + 1))
    }
}

private fun localDateOf(isoDateTime: String): LocalDate =
    OffsetDateTime.parse(isoDateTime).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(isoDateTime: String): String =
    localDateOf(isoDateTime).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))

private fun shortDate(isoDate: String): String =
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
