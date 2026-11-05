package no.scamguardian.app.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.scamguardian.app.board.BoardClient
import no.scamguardian.app.board.Member
import no.scamguardian.app.board.WordRules
import no.scamguardian.app.monitor.BoardSession
import no.scamguardian.app.ui.errorMessage
import no.scamguardian.app.ui.formatTime

data class WordsUi(
    val members: List<Member> = emptyList(),
    val maxMembers: Int = WordRules.MAX_MEMBERS,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Family members and their security words. Words go straight from the text field to the board's
 * POST /words over the LAN: this class never stores, logs or caches them.
 */
@HiltViewModel
class WordsViewModel @Inject constructor(private val session: BoardSession) : ViewModel() {
    private val _ui = MutableStateFlow(WordsUi())
    val ui: StateFlow<WordsUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = act(null) { c ->
        val list = c.members()
        _ui.update { it.copy(members = list.members, maxMembers = list.maxMembers, loaded = true) }
    }

    fun add(name: String, word: String?) = act("Added ${name.trim()}") { c -> c.addMember(name.trim(), word?.ifEmpty { null }); reload(c) }
    fun rename(id: Int, name: String) = act("Renamed") { c -> c.renameMember(id, name.trim()); reload(c) }
    fun setWord(id: Int, word: String) = act("Security word saved on the board") { c -> c.setWord(id, word); reload(c) }
    fun clearWord(id: Int) = act("Security word removed") { c -> c.clearWord(id); reload(c) }
    fun delete(id: Int) = act("Member deleted") { c -> c.deleteMember(id); reload(c) }

    fun dismissMessage() = _ui.update { it.copy(message = null, error = null) }

    private suspend fun reload(c: BoardClient) {
        val list = c.members()
        _ui.update { it.copy(members = list.members, maxMembers = list.maxMembers, loaded = true) }
    }

    private fun act(success: String?, block: suspend (BoardClient) -> Unit) {
        _ui.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            val err = runCatching { block(session.requireClient()) }.exceptionOrNull()
            _ui.update { it.copy(loading = false, error = err?.let(::errorMessage), message = if (err == null) success else null) }
        }
    }
}

private sealed interface WordsDialog {
    data object Add : WordsDialog
    data class SetWord(val member: Member) : WordsDialog
    data class Rename(val member: Member) : WordsDialog
    data class ConfirmClear(val member: Member) : WordsDialog
    data class ConfirmDelete(val member: Member) : WordsDialog
}

@Composable
fun WordsScreen(vm: WordsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<WordsDialog?>(null) }

    Scaffold(
        floatingActionButton = {
            if (ui.loaded && ui.members.size < ui.maxMembers) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = WordsDialog.Add },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add member") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Family security words", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "When a caller says a member's word, the board shows it. Words are kept only on the board, never on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            ui.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            ui.message?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
            if (ui.loaded && ui.members.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No family members yet. Add one and give them a word only the family knows.")
                }
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.members, key = { it.id }) { m ->
                    MemberRow(m) { dialog = it }
                }
                item { Text("${ui.members.size} of ${ui.maxMembers} members", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        WordsDialog.Add -> MemberDialog(
            title = "Add family member",
            initialName = "",
            askName = true,
            askWord = true,
            wordOptional = true,
            onDismiss = { dialog = null },
            onConfirm = { name, word -> vm.add(name, word); dialog = null },
        )
        is WordsDialog.SetWord -> MemberDialog(
            title = if (d.member.hasWord) "Change ${d.member.name}'s word" else "Set ${d.member.name}'s word",
            initialName = d.member.name,
            askName = false,
            askWord = true,
            wordOptional = false,
            onDismiss = { dialog = null },
            onConfirm = { _, word -> vm.setWord(d.member.id, word.orEmpty()); dialog = null },
        )
        is WordsDialog.Rename -> MemberDialog(
            title = "Rename ${d.member.name}",
            initialName = d.member.name,
            askName = true,
            askWord = false,
            wordOptional = true,
            onDismiss = { dialog = null },
            onConfirm = { name, _ -> vm.rename(d.member.id, name); dialog = null },
        )
        is WordsDialog.ConfirmClear -> Confirm(
            "Remove ${d.member.name}'s word?",
            "The member stays, without a security word.",
            "Remove word",
            onDismiss = { dialog = null },
            onConfirm = { vm.clearWord(d.member.id); dialog = null },
        )
        is WordsDialog.ConfirmDelete -> Confirm(
            "Delete ${d.member.name}?",
            "The member and their word are removed from the board.",
            "Delete",
            onDismiss = { dialog = null },
            onConfirm = { vm.delete(d.member.id); dialog = null },
        )
    }
}

@Composable
private fun MemberRow(m: Member, open: (WordsDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (m.hasWord) "Has a security word" + (formatTime(m.updated).takeIf { it.isNotEmpty() }?.let { " · set $it" } ?: "")
                    else "No security word",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (m.hasWord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = { open(WordsDialog.SetWord(m)) }) { Text(if (m.hasWord) "Change" else "Set word") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More for ${m.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; open(WordsDialog.Rename(m)) })
                    if (m.hasWord) {
                        DropdownMenuItem(text = { Text("Remove word") }, onClick = { menu = false; open(WordsDialog.ConfirmClear(m)) })
                    }
                    DropdownMenuItem(text = { Text("Delete member") }, onClick = { menu = false; open(WordsDialog.ConfirmDelete(m)) })
                }
            }
        }
    }
}

/**
 * Name and/or word entry. The word field is a password field: hidden by default, no autocorrect or
 * suggestions, plain `remember` (not saved in the activity's state), and the dialog window is
 * FLAG_SECURE so it stays out of screenshots and the recent-apps preview.
 */
@Composable
private fun MemberDialog(
    title: String,
    initialName: String,
    askName: Boolean,
    askWord: Boolean,
    wordOptional: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var word by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val nameError = if (askName) WordRules.checkName(name) else null
    val wordError = when {
        !askWord -> null
        word.isEmpty() && wordOptional -> null
        else -> WordRules.checkWord(word)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (askName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = name.isNotEmpty() && nameError != null,
                        supportingText = { if (name.isNotEmpty() && nameError != null) Text(nameError) },
                    )
                }
                if (askWord) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text(if (wordOptional) "Security word (optional)" else "Security word") },
                        singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                        isError = word.isNotEmpty() && wordError != null,
                        supportingText = {
                            Text(
                                if (word.isNotEmpty() && wordError != null) wordError
                                else "1-4 words. A phrase is safer than one short word. Case and punctuation do not matter.",
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameError == null && wordError == null && (!askWord || wordOptional || word.isNotEmpty()),
                onClick = {
                    val w = word
                    word = ""
                    onConfirm(name, if (askWord) w else null)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { word = ""; onDismiss() }) { Text("Cancel") } },
    )
}

@Composable
private fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
