package ru.maxx.app.ui.screens.contacts

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private val _loading  = MutableStateFlow(false)
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    val loading:  StateFlow<Boolean>       = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            _contacts.value = repo.loadContacts()
            _loading.value = false
        }
        viewModelScope.launch {
            repo.observePresence().collect { presence ->
                val uid = (presence["userId"] as? Number)?.toLong() ?: return@collect
                val online = presence["online"] as? Boolean ?: false
                _contacts.value = _contacts.value.map { if (it.id == uid) it.copy(online = online) else it }
            }
        }
    }

    fun importFromDevice(phones: List<String>) = viewModelScope.launch {
        // Отправляем телефоны на сервер для поиска пользователей MAX
        val found = repo.findByPhones(phones)
        if (found.isNotEmpty()) {
            _contacts.value = (_contacts.value + found).distinctBy { it.id }
        }
    }
}

@Composable
fun ContactsScreen(container: AppContainer, onBack: () -> Unit, onContactClick: (Long, String) -> Unit) {
    val vm       = remember { ContactsViewModel(container) }
    val contacts by vm.contacts.collectAsState()
    val loading  by vm.loading.collectAsState()
    val context  = LocalContext.current

    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Читаем контакты с устройства
            val cr = context.contentResolver
            val phones = mutableListOf<String>()
            val cursor = cr.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )
            cursor?.use {
                val col = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val phone = it.getString(col)?.filter { c -> c.isDigit() || c == '+' } ?: continue
                    if (phone.length >= 7) phones.add(phone)
                }
            }
            vm.importFromDevice(phones.distinct())
        }
    }

    val grouped = remember(contacts, search) {
        contacts
            .filter { it.displayName.contains(search, ignoreCase = true) || it.phone?.contains(search) == true }
            .sortedBy { it.displayName }
            .groupBy { it.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MaxXTopBar("Контакты", actions = {
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) search = "" }) {
                    Icon(if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                        null, tint = if (showSearch) Accent else TextMuted)
                }
                IconButton(onClick = { permLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Icon(Icons.Outlined.PersonAdd, null, tint = TextMuted)
                }
            })
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            AnimatedVisibility(visible = showSearch, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Поиск контактов...", color = TextHint, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                    )
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Accent) }
            } else if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.People, null, tint = TextHint, modifier = Modifier.size(52.dp))
                        Text("Контакты не найдены", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { permLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                        ) {
                            Icon(Icons.Outlined.ContactPage, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Импортировать контакты")
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (letter, list) ->
                        item {
                            Text(letter, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold)
                        }
                        items(list, key = { it.id }) { contact ->
                            ContactRow(contact = contact, onClick = { onContactClick(contact.id, contact.displayName) })
                        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(AccentDark), Alignment.Center) {
                Text(contact.displayName.take(2).uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (contact.online) {
                Box(
                    modifier = Modifier.size(12.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(Color(0xFF4CAF50))
                        .padding(1.dp)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!contact.phone.isNullOrEmpty()) {
                Text(contact.phone, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        if (contact.online) {
            Text("в сети", fontSize = 10.sp, color = Color(0xFF4CAF50))
        }
    }
}
