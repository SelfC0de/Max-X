package ru.maxx.app.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.model.Contact
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.components.*
import ru.maxx.app.ui.theme.*

class ContactsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.contactRepo
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            _contacts.value = repo.loadContacts()
            _loading.value = false
        }
        viewModelScope.launch {
            repo.observePresence().collect { (uid, online) ->
                _contacts.value = _contacts.value.map { if (it.id == uid) it.copy(online = online) else it }
            }
        }
    }
}

@Composable
fun ContactsScreen(container: AppContainer, onBack: () -> Unit, onContactClick: (Long, String) -> Unit) {
    val vm = remember { ContactsViewModel(container) }
    val contacts by vm.contacts.collectAsState()
    val loading by vm.loading.collectAsState()
    var search by remember { mutableStateOf("") }

    val grouped = remember(contacts, search) {
        contacts
            .filter { it.displayName.contains(search, ignoreCase = true) || it.phone?.contains(search) == true }
            .sortedBy { it.displayName }
            .groupBy { it.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
    }

    Scaffold(containerColor = BgPrimary, topBar = {
        MaxXTopBar("Контакты")  // onBack не нужен — таб, не отдельный экран
    }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Поиск", color = TextHint) },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                    )
                )
            }
            if (loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Accent) } }
            } else {
                grouped.forEach { (letter, list) ->
                    item { SectionLabel(letter) }
                    items(list, key = { it.id }) { contact ->
                        ContactRow(contact = contact, onClick = {
                            onContactClick(contact.chatId ?: contact.id, contact.displayName)
                        })
                        HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 70.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(BgPrimary).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            AvatarView(initials = contact.initials, size = 46, bgColor = AccentDark, textColor = Accent)
            if (contact.online) OnlineDot(Modifier.align(Alignment.BottomEnd).offset((-1).dp, (-1).dp))
        }
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                if (contact.online) "онлайн" else contact.phone ?: "оффлайн",
                style = MaterialTheme.typography.bodySmall,
                color = if (contact.online) Accent else TextMuted
            )
        }
    }
}
