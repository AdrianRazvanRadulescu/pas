package dev.pas.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pas.app.CoachStore

// GitHub's pre-filled fine-grained token form; the repository itself still has to be picked by hand.
private fun newTokenUrl(owner: String): String =
    "https://github.com/settings/personal-access-tokens/new" +
        "?name=Pas&description=Pas%20phone%20app" +
        "&target_name=$owner&expires_in=366&contents=write"

@Composable
fun SetupScreen(
    initialRepository: String,
    isReconnecting: Boolean,
    isConnecting: Boolean,
    connectError: String?,
    onConnect: (token: String, repository: String) -> Unit,
    onCancel: () -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }
    var repository by rememberSaveable { mutableStateOf(initialRepository) }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val owner = CoachStore.DEFAULT_REPOSITORY.substringBefore('/')
    val repositoryName = CoachStore.DEFAULT_REPOSITORY.substringAfter('/')

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text("Pas", style = MaterialTheme.typography.displaySmall)
            Text(
                text = "One day at a time.",
                style = quoteTextStyle,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "The coach on your PC writes to you three times a day, quietly. To get here, " +
                    "Pas needs a GitHub token that can see only your private $repositoryName repo. It takes a minute.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(28.dp))
            SetupStep(number = 1, title = "Create the token on GitHub") {
                Text(
                    text = "The form comes pre-filled. You only pick “Only select repositories” → $repositoryName, " +
                        "then “Generate token”, and copy the token.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { uriHandler.openUri(newTokenUrl(owner)) }, shape = Pas.decor.pill) {
                    Text("Open GitHub ↗")
                }
            }

            Spacer(Modifier.height(16.dp))
            SetupStep(number = 2, title = "Paste the token here") {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = Pas.decor.field,
                    trailingIcon = {
                        TextButton(onClick = { clipboard.getText()?.let { token = it.text.trim() } }) {
                            Text("Paste")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onConnect(token, repository) },
                enabled = token.isNotBlank() && repository.isNotBlank() && !isConnecting,
                shape = Pas.decor.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Checking the token…")
                } else {
                    Text("Connect", style = MaterialTheme.typography.titleMedium)
                }
            }
            connectError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (isReconnecting) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }

            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = repository,
                onValueChange = { repository = it },
                label = { Text("Repo (usually no need to change it)") },
                singleLine = true,
                shape = Pas.decor.field,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, content: @Composable () -> Unit) {
    PasCard(
        color = MaterialTheme.colorScheme.surface,
        shape = Pas.decor.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {}
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
