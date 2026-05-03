package ru.maxx.app.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.maxx.app.data.model.AttachType
import ru.maxx.app.data.model.Attachment
import ru.maxx.app.data.model.Message
import ru.maxx.app.data.model.TypingEvent
import ru.maxx.app.data.repository.UploadResult
import ru.maxx.app.di.AppContainer
import ru.maxx.app.ui.screens.chats.MaxXSnackbar
import ru.maxx.app.ui.components.*
import ru.maxx.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── ViewModel ───────────────────────────────────────────────────────────────

class ChatViewModel(private val container: AppContainer, val chatId: Long) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _typing = MutableStateFlow<TypingEvent?>(null)
    val typing: StateFlow<TypingEvent?> = _typing.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Int?>(null)
    val uploadProgress: StateFlow<Int?> = _uploadProgress.asStateFlow()

    private val _toast = Channel<String>(Channel.BUFFERED)
    val toast: Flow<String> = _toast.receiveAsFlow()

    val myUserId: Long = container.authPrefs.getUserId()?.toLongOrNull() ?: 0L

    private var loadingMore = false

    init {
        loadInitial()
        observeIncoming()
        observeTyping()
    }

    private fun loadInitial() = viewModelScope.launch {
        _loading.value = true
        val msgs = container.msgRepo.loadMessages(chatId, 50).reversed()
        _messages.value = msgs
        _loading.value = false
        msgs.lastOrNull()?.let { container.msgRepo.markRead(chatId, it.id) }
    }

    private fun observeIncoming() = viewModelScope.launch {
        container.msgRepo.observeNewMessages(chatId).collect { msg ->
            _messages.value = _messages.value + msg
            container.msgRepo.markRead(chatId, msg.id)
        }
    }

    private var typingClearJob: kotlinx.coroutines.Job? = null

    private fun observeTyping() = viewModelScope.launch {
        container.msgRepo.observeTyping(chatId).collect { ev ->
            _typing.value = ev
            typingClearJob?.cancel()
            typingClearJob = viewModelScope.launch {
                kotlinx.coroutines.delay(3_500)
                if (_typing.value?.userId == ev.userId) _typing.value = null
            }
        }
    }

    fun loadMore() {
        if (loadingMore) return
        val oldest = _messages.value.firstOrNull()?.id ?: return
        loadingMore = true
        viewModelScope.launch {
            val older = container.msgRepo.loadMessages(chatId, 30, oldest).reversed()
            if (older.isNotEmpty()) _messages.value = older + _messages.value
            loadingMore = false
        }
    }

    fun sendMessage(text: String, replyToId: Long? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val ok = container.msgRepo.sendMessage(chatId, trimmed, replyToId)
            if (!ok) _toast.trySend("Не удалось отправить сообщение")
        }
    }

    fun editMessage(messageId: Long, text: String) = viewModelScope.launch {
        container.msgRepo.editMessage(chatId, messageId, text)
    }

    fun deleteMessage(messageId: Long, forAll: Boolean) = viewModelScope.launch {
        val ok = container.msgRepo.deleteMessage(chatId, messageId, forAll)
        if (ok) _messages.value = _messages.value.filter { it.id != messageId }
        else _toast.trySend("Не удалось удалить")
    }

    fun sendReaction(messageId: Long, emoji: String) = viewModelScope.launch {
        container.msgRepo.sendReaction(chatId, messageId, emoji)
    }

    fun sendTypingDebounced(isTyping: Boolean) = viewModelScope.launch {
        if (!container.appPrefs.hideTypingStatus) container.msgRepo.sendTyping(chatId, isTyping)
    }

    fun markUnread() = viewModelScope.launch {
        container.msgRepo.markUnread(chatId)
    }

    fun forwardMessage(messageId: Long, toChatId: Long) = viewModelScope.launch {
        val ok = container.msgRepo.forwardMessage(chatId, messageId, toChatId)
        if (!ok) _toast.trySend("Не удалось переслать")
    }

    fun searchMessages(query: String) = viewModelScope.launch {
        _loading.value = true
        val results = container.msgRepo.searchMessages(chatId, query)
        _messages.value = results.reversed()
        _loading.value = false
    }

    private var voiceFile: java.io.File? = null

    fun startVoiceRecording(): Boolean {
        return voiceFile != null
    }

    fun stopAndSendVoice() = viewModelScope.launch {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            container.ctx, "${container.ctx.packageName}.provider", file
        )
        uploadAndSend(uri)
        file.deleteOnExit()
    }

    fun cancelVoiceRecording() {
        voiceFile = null
    }

    fun setAutoDelete(seconds: Int) = viewModelScope.launch {
        container.msgRepo.setAutoDelete(chatId, seconds)
    }

    fun sendTypingIfAllowed(isTyping: Boolean) = viewModelScope.launch {
        if (!container.appPrefs.hideTypingStatus) {
            container.msgRepo.sendTyping(chatId, isTyping)
        }
    }

    fun markReadIfAllowed(messageId: Long) = viewModelScope.launch {
        if (container.appPrefs.autoMarkRead) {
            container.msgRepo.markRead(chatId, messageId)
        }
    }

    fun saveToFavorites(msg: Message, chatTitle: String) {
        container.favoritesRepo.saveMessage(msg, chatTitle)
        _toast.trySend("Сохранено в избранное")
    }

    fun exportChat(title: String, format: ru.maxx.app.core.export.ExportFormat = ru.maxx.app.core.export.ExportFormat.TXT) = viewModelScope.launch {
        val result = container.exportService.exportChat(_messages.value, title, myUserId, format)
        when (result) {
            is ru.maxx.app.core.export.ExportResult.Success -> {
                container.exportService.share(result.file, format)
            }
            is ru.maxx.app.core.export.ExportResult.Error -> _toast.trySend(result.msg)
        }
    }

    fun uploadAndSend(uri: Uri) = viewModelScope.launch {
        container.mediaRepo.uploadFile(uri).collect { result ->
            when (result) {
                is UploadResult.Progress -> _uploadProgress.value = result.percent
                is UploadResult.Success -> {
                    _uploadProgress.value = null
                    container.msgRepo.sendMessage(chatId, result.url)
                }
                is UploadResult.Failure -> {
                    _uploadProgress.value = null
                    _toast.trySend("Ошибка загрузки: ${result.error}")
                }
            }
        }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(container: AppContainer, chatId: Long, title: String, onBack: () -> Unit) {
    val vm = remember(chatId) { ChatViewModel(container, chatId) }
    val messages by vm.messages.collectAsState()
    val typing by vm.typing.collectAsState()
    val loading by vm.loading.collectAsState()
    val uploadProgress by vm.uploadProgress.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editingMsg by remember { mutableStateOf<Message?>(null) }
    var selectedMsg     by remember { mutableStateOf<Message?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var showAutoDelete    by remember { mutableStateOf(false) }
    var showMediaGallery  by remember { mutableStateOf(false) }
    var showSearch        by remember { mutableStateOf(false) }
    var searchQuery       by remember { mutableStateOf("") }
    val clipboardManager  = androidx.compose.ui.platform.LocalClipboardManager.current
    val listState = rememberLazyListState()
    val snackbarHost = remember { SnackbarHostState() }

    // Toast из ViewModel
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.toast.collect { text ->
            snackbarHost.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    // Автоскролл при новом сообщении
    val msgCount = messages.size
    LaunchedEffect(msgCount) {
        if (msgCount > 0 && listState.firstVisibleItemIndex >= msgCount - 5) {
            listState.animateScrollToItem(msgCount - 1)
        }
    }

    // Загрузить ещё при скролле вверх (не при начальной загрузке)
    var initialLoadDone by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading && messages.isNotEmpty()) initialLoadDone = true }
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible) {
        if (initialLoadDone && firstVisible == 0 && messages.isNotEmpty() && !loading) vm.loadMore()
    }

    // File picker
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.uploadAndSend(it) }
    }
    var showAttachMenu    by remember { mutableStateOf(false) }
    var isRecordingVoice  by remember { mutableStateOf(false) }
    val voicePermission = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) { isRecordingVoice = true; vm.startVoiceRecording() } }

    if (showMediaGallery) {
        ru.maxx.app.ui.screens.media.MediaGalleryScreen(
            container  = container,
            chatId     = chatId,
            chatTitle  = title,
            onBack     = { showMediaGallery = false }
        )
        return
    }

    // Диалог автоудаления
    if (showAutoDelete) {
        AlertDialog(
            onDismissRequest = { showAutoDelete = false },
            containerColor   = BgCard,
            title = { Text("Автоудаление сообщений", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    listOf(0 to "Выключено", 3600 to "1 час", 86400 to "1 день",
                        604800 to "1 неделя", 2592000 to "1 месяц").forEach { (sec, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.setAutoDelete(sec); showAutoDelete = false
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    LaunchedEffect(searchQuery) {
        if (showSearch && searchQuery.length >= 2) vm.searchMessages(searchQuery)
    }

    Scaffold(
        containerColor = BgPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                MaxXSnackbar(data.visuals.message)
            }
        },
        topBar = {
            MaxXChatTopBar(
                title = title,
                subtitle = when {
                    typing != null -> "${typing!!.name} печатает..."
                    else -> null
                },
                onBack = onBack
            )
        },
        bottomBar = {
            Column {
                // Upload progress bar
                AnimatedVisibility(visible = uploadProgress != null) {
                    LinearProgressIndicator(
                        progress = { (uploadProgress ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Accent, trackColor = BgCard
                    )
                }
                // Reply/Edit preview
                AnimatedVisibility(
                    visible = replyTo != null || editingMsg != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()
                ) {
                    val previewMsg = editingMsg ?: replyTo
                    Row(
                        modifier = Modifier.fillMaxWidth().background(BgCard)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(34.dp).background(Accent, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editingMsg != null) "Редактирование" else "Ответ",
                                fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Medium
                            )
                            Text(previewMsg?.text ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { replyTo = null; editingMsg = null; inputText = "" },
                            modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                // Search bar
                AnimatedVisibility(
                    visible = showSearch,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit  = slideOutVertically { -it } + fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(BgCard)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск в чате...", color = TextHint, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp), singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary,
                                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                            )
                        )
                        IconButton(onClick = { showSearch = false; searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                // Input bar
                Row(
                    modifier = Modifier.fillMaxWidth().background(BgSecondary)
                        .padding(horizontal = 8.dp, vertical = 6.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = { showAttachMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, null, tint = if (showAttachMenu) Accent else TextMuted)
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            vm.sendTypingDebounced(it.isNotEmpty())
                        },
                        placeholder = { Text("Сообщение...", color = TextHint, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                            focusedBorderColor = Border, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        )
                    )
                    val canSend = inputText.trim().isNotEmpty()
                    AnimatedContent(
                        targetState = when {
                            isRecordingVoice -> "voice"
                            canSend          -> "send"
                            else             -> "mic"
                        },
                        transitionSpec = { scaleIn(tween(150)) togetherWith scaleOut(tween(150)) },
                        label = "send_btn"
                    ) { state ->
                        when (state) {
                            "send" -> IconButton(
                                onClick = {
                                    if (editingMsg != null) {
                                        vm.editMessage(editingMsg!!.id, inputText.trim())
                                        editingMsg = null
                                    } else {
                                        vm.sendMessage(inputText.trim(), replyTo?.id)
                                        replyTo = null
                                    }
                                    inputText = ""
                                },
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Accent)
                            ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = BgSecondary, modifier = Modifier.size(18.dp)) }

                            "mic" -> IconButton(
                                onClick = {
                                },
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(BgCard)
                            ) { Icon(Icons.Outlined.Mic, null, tint = TextMuted, modifier = Modifier.size(20.dp)) }

                            "voice" -> Row(
                                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                                    .background(Red).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { isRecordingVoice = false; vm.cancelVoiceRecording() },
                                    modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                                IconButton(
                                    onClick = { isRecordingVoice = false; vm.stopAndSendVoice() },
                                    modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                            else -> Box(Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter   = fadeIn(tween(160)) + slideInVertically(tween(160)) { 20 }
                ) {
                    MessageBubble(
                        msg = msg, isMine = msg.senderId == vm.myUserId,
                        onLongPress = { selectedMsg = msg },
                        onReplyClick = { replyTo = it }
                    )
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }

    // Attach menu
    if (showAttachMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAttachMenu = false },
            containerColor = BgCard,
            dragHandle = { Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Border)) }
        ) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Прикрепить", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    AttachItem(Icons.Default.Photo, "Фото", AccentDark, Accent) {
                        showAttachMenu = false; filePicker.launch("image/*")
                    }
                    AttachItem(Icons.Default.VideoFile, "Видео", BlueDark, Blue) {
                        showAttachMenu = false; filePicker.launch("video/*")
                    }
                    AttachItem(Icons.Default.AudioFile, "Аудио", PurpleDark, Purple) {
                        showAttachMenu = false; filePicker.launch("audio/*")
                    }
                    AttachItem(Icons.Default.AttachFile, "Файл", BgTertiary, TextSecondary) {
                        showAttachMenu = false; filePicker.launch("*/*")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Context menu
    if (selectedMsg != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMsg = null },
            containerColor = BgCard,
            dragHandle = { Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Border)) }
        ) {
            val msg = selectedMsg!!
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                // Реакции
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.spacedBy(4.dp)) {
                    listOf("👍","❤️","😂","😮","😢","🔥","👎").forEach { e ->
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(BgTertiary)
                                .clickable { vm.sendReaction(msg.id, e); selectedMsg = null },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                ActionMenuItem(Icons.Default.Reply, "Ответить") { replyTo = msg; selectedMsg = null }
                if (msg.senderId == vm.myUserId) {
                    ActionMenuItem(Icons.Default.Edit, "Редактировать") {
                        editingMsg = msg; inputText = msg.text; selectedMsg = null
                    }
                    ActionMenuItem(Icons.Default.Delete, "Удалить у всех", color = Red) {
                        vm.deleteMessage(msg.id, true); selectedMsg = null
                    }
                }
                ActionMenuItem(Icons.Default.ContentCopy, "Копировать") {
                    selectedMsg?.let { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it.text)) }
                    selectedMsg = null
                }
                ActionMenuItem(Icons.Default.Forward, "Переслать") {
                    showForwardDialog = true
                }
                ActionMenuItem(Icons.Default.MarkEmailUnread, "Оставить непрочитанным") {
                    vm.markUnread(); selectedMsg = null
                }
                ActionMenuItem(Icons.Default.Bookmark, "Сохранить в избранное") {
                    selectedMsg?.let { vm.saveToFavorites(it, title) }
                    selectedMsg = null
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private val TIME_FMT = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }

// ─── Bubble ──────────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    msg: Message, isMine: Boolean,
    onLongPress: () -> Unit, onReplyClick: (Message) -> Unit
) {
    val timeStr = remember(msg.time) {
        if (msg.time > 0) TIME_FMT.get()!!.format(Date(msg.time)) else ""
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 64.dp, max = 300.dp)
                .clip(RoundedCornerShape(
                    topStart    = 18.dp, topEnd   = 18.dp,
                    bottomStart = if (isMine) 18.dp else 6.dp,
                    bottomEnd   = if (isMine) 6.dp  else 18.dp
                ))
                .background(
                    if (isMine)
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(BubbleOut, Color(0xFF223815))
                        )
                    else
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(BubbleIn, BgCard)
                        )
                )
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
                .padding(horizontal = 11.dp, vertical = 8.dp)
        ) {
            Column {
                // Reply
                if (msg.replyToId != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 5.dp)
                            .clip(RoundedCornerShape(6.dp)).background(
                                if (isMine) AccentDark.copy(alpha = 0.5f) else BgTertiary
                            ).padding(horizontal = 7.dp, vertical = 4.dp)
                            .clickable { msg.replyTo?.let { onReplyClick(it) } },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(2.dp).height(24.dp).background(Accent, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Ответ", fontSize = 10.sp, color = Accent, fontWeight = FontWeight.Medium)
                            Text(msg.replyTo?.text ?: "...", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                        }
                    }
                }

                // Sender name in groups
                if (!isMine && msg.senderName.isNotEmpty()) {
                    Text(msg.senderName, fontSize = 11.sp, color = Accent,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
                }

                // Media attachments
                msg.attachments.forEach { attach ->
                    AttachmentView(attach = attach, isMine = isMine)
                    Spacer(Modifier.height(4.dp))
                }

                // Text
                if (msg.text.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            msg.text,
                            color = if (isMine) BubbleTextOut else BubbleTextIn,
                            fontSize = 14.sp, lineHeight = 19.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        MsgMeta(timeStr, msg.edited, isMine, msg.readBy.isNotEmpty())
                    }
                } else if (msg.attachments.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                        MsgMeta(timeStr, msg.edited, isMine, msg.readBy.isNotEmpty())
                    }
                }

                // Reactions
                if (msg.reactions.isNotEmpty()) {
                    Row(modifier = Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        msg.reactions.forEach { r ->
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (r.myReaction) AccentDark else BgTertiary)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("${r.emoji} ${r.count}", fontSize = 11.sp,
                                    color = if (r.myReaction) Accent else TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MsgMeta(time: String, edited: Boolean, isMine: Boolean, read: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (edited) Text("ред.", fontSize = 9.sp, color = TextHint)
        Text(time, fontSize = 10.sp, color = if (isMine) AccentLight.copy(alpha = 0.55f) else TextHint)
        if (isMine) {
            Icon(if (read) Icons.Default.DoneAll else Icons.Default.Done, null,
                tint = if (read) Accent else AccentLight.copy(alpha = 0.45f),
                modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun AttachmentView(attach: Attachment, isMine: Boolean) {
    when (attach.type) {
        AttachType.IMAGE -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(attach.url).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(8.dp))
                    .aspectRatio(if (attach.width != null && attach.height != null)
                        attach.width.toFloat() / attach.height else 1.5f),
                contentScale = ContentScale.Crop
            )
        }
        AttachType.VIDEO -> {
            Box(
                modifier = Modifier.size(220.dp, 140.dp).clip(RoundedCornerShape(8.dp)).background(BgTertiary),
                contentAlignment = Alignment.Center
            ) {
                if (attach.thumbnailUrl != null) {
                    AsyncImage(model = attach.thumbnailUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                if (attach.duration != null) {
                    Text(formatDuration(attach.duration), fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
            }
        }
        AttachType.VOICE -> {
            Row(
                modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)
                    .clip(RoundedCornerShape(8.dp)).background(BgTertiary)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(AccentDark), Alignment.Center) {
                    Icon(Icons.Default.Mic, null, tint = Accent, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    // Wave mock
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(24) { i ->
                            val h = (4 + (i * 7 + 3) % 16).dp
                            Box(Modifier.width(2.dp).height(h).background(if (i < 8) Accent else TextHint, RoundedCornerShape(1.dp)))
                        }
                    }
                    if (attach.duration != null) {
                        Text(formatDuration(attach.duration), fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        AttachType.AUDIO -> {
            Row(
                modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
                    .clip(RoundedCornerShape(8.dp)).background(BgTertiary)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(PurpleDark), Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Purple, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(attach.name ?: "Аудио", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                    if (attach.duration != null) Text(formatDuration(attach.duration), fontSize = 10.sp, color = TextMuted)
                }
            }
        }
        AttachType.FILE -> {
            Row(
                modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
                    .clip(RoundedCornerShape(8.dp)).background(BgTertiary)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BlueDark), Alignment.Center) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = Blue, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(attach.name ?: "Файл", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                    if (attach.size != null) Text(formatSize(attach.size), fontSize = 10.sp, color = TextMuted)
                }
                Icon(Icons.Default.Download, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        AttachType.STICKER -> {
            AsyncImage(
                model = attach.url, contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
        }
    }
}

@Composable
private fun AttachItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(68.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(bg), Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun ActionMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color = TextPrimary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
fun MaxXChatTopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).background(BgSecondary).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Accent) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                AnimatedContent(targetState = subtitle, transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                }, label = "subtitle") { sub ->
                    if (sub != null) Text(sub, fontSize = 11.sp, color = Accent, maxLines = 1)
                    else Text("", fontSize = 11.sp)
                }
            }
            IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = TextMuted) }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = TextMuted) }
        }
        HorizontalDivider(color = Border, thickness = 0.5.dp)
    }
}



private fun formatDuration(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "%.1f МБ".format(bytes / 1024.0 / 1024.0)
}
