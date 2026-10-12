package dev.pas.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.pas.app.Story
import dev.pas.app.StoryPart

/** The digest writes this part only for stories worth understanding, so it gets its own card. */
private const val TECHNICAL_PART_LABEL = "The technical part"

/** One story read calmly: what happened, why it matters, its limits, then one sentence of your own that becomes a card. */
@Composable
fun StoryScreen(story: Story, onBack: () -> Unit, onLearned: (String) -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Column(Modifier.enterInOrder(0)) {
                SectionLabel(storyPlace(story), color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(8.dp))
                Text(text = story.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${story.readMinutes} min read",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                story.intro?.let { intro ->
                    Spacer(Modifier.height(12.dp))
                    Text(text = withBoldMarks(intro), style = MaterialTheme.typography.bodyLarge)
                }
            }
            story.parts.forEachIndexed { index, part ->
                StoryPartBlock(part = part, modifier = Modifier.enterInOrder(index + 1))
            }
            story.url?.let { url ->
                OutlinedButton(onClick = { uriHandler.openUri(url) }, shape = Pas.decor.pill) {
                    Text("Open source ↗")
                }
            }
            LearnedFromStoryCard(onLearned = onLearned)
        }
    }
}

@Composable
private fun StoryPartBlock(part: StoryPart, modifier: Modifier) {
    if (part.label == TECHNICAL_PART_LABEL) {
        PasCard(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel(part.label, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(8.dp))
                Text(text = withBoldMarks(part.text), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    Column(modifier) {
        part.label?.let { label ->
            SectionLabel(label, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        Text(text = withBoldMarks(part.text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LearnedFromStoryCard(onLearned: (String) -> Unit) {
    var sentence by rememberSaveable { mutableStateOf("") }
    var isSaved by rememberSaveable { mutableStateOf(false) }
    QuietCard(label = "What did you take away?") {
        AnimatedContent(targetState = isSaved, label = "learnedSaved") { saved ->
            if (saved) {
                Text(
                    text = "Saved. You'll get it back as a flashcard to review.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Column {
                    Text(
                        text = "One sentence in your own words. It becomes a flashcard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = sentence,
                        onValueChange = { sentence = it },
                        placeholder = { Text("I understood that…") },
                        shape = Pas.decor.field,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onLearned(sentence.trim())
                            isSaved = true
                        },
                        enabled = sentence.isNotBlank(),
                        shape = Pas.decor.pill,
                    ) {
                        Text("Make it a flashcard")
                    }
                }
            }
        }
    }
}

/** "Research · New findings" */
fun storyPlace(story: Story): String =
    if (story.topic == null) story.section else "${story.section} · ${story.topic}"

/** The board keeps the digest's **bold** marks; they become real bold here. */
fun withBoldMarks(text: String): AnnotatedString = buildAnnotatedString {
    val pieces = text.split("**")
    pieces.forEachIndexed { index, piece ->
        val isInsideBoldMarks = index % 2 == 1
        if (isInsideBoldMarks) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(piece) }
        } else {
            append(piece)
        }
    }
}
