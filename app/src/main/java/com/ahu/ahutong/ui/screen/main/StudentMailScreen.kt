package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.data.mail.*
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.*
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.*
import java.time.LocalDate

private data class MailMoveConfirmation(val id: String, val subject: String, val targetFolder: Int,
    val discardEditing: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentMailScreen(onBack: () -> Unit, vm: StudentMailViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var messageQuery by rememberSaveable { mutableStateOf("") }
    var contactQuery by rememberSaveable { mutableStateOf("") }
    var showFolders by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var confirmSend by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmMove by remember { mutableStateOf<MailMoveConfirmation?>(null) }
    var selectedMessage by remember { mutableStateOf<MailMessage?>(null) }
    var pickingRecipient by rememberSaveable { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<MailContact?>(null) }
    val inboxList = rememberLazyListState()
    val contactList = rememberLazyListState()
    val attachmentList = rememberLazyListState()
    val detailList = rememberLazyListState()
    val composeList = rememberLazyListState()
    val listState = when (state.screen) {
        MailScreen.MESSAGES -> inboxList
        MailScreen.CONTACTS -> contactList
        MailScreen.ATTACHMENTS -> attachmentList
        MailScreen.DETAIL -> detailList
        MailScreen.COMPOSE -> composeList
    }
    val rootPage = state.screen in listOf(MailScreen.MESSAGES, MailScreen.CONTACTS, MailScreen.ATTACHMENTS)
    val busy = state.loading || state.sending || state.saving || state.transfer != null
    val hasDraft = state.draft.attachments.isNotEmpty() || state.draft.let { listOf(it.to, it.cc, it.bcc, it.subject, it.body).any(String::isNotBlank) }
    val chooseAttachments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.uploadAttachments(context, uris)
    }
    val saveAttachment = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        vm.downloadAttachment(context, uri)
    }
    val download: (MailAttachment) -> Unit = { attachment ->
        vm.prepareDownload(attachment)?.let { saveAttachment.launch(it) }
    }
    val back: () -> Unit = {
        focus.clearFocus()
        if (pickingRecipient && state.screen == MailScreen.CONTACTS) {
            if (!busy) { pickingRecipient = false; vm.startCompose() }
        } else if (!vm.back()) {
            if (state.draftDirty || state.draftSaveUncertain) confirmLeave = true else onBack()
        }
    }
    BackHandler(onBack = back)
    LaunchedEffect(state.folderId, state.unreadOnly) { inboxList.scrollToItem(0) }
    LaunchedEffect(state.detail?.id) { detailList.scrollToItem(0) }
    LaunchedEffect(state.screen) { if (state.screen == MailScreen.COMPOSE) composeList.scrollToItem(0) }
    LaunchedEffect(state.account) { confirmMove = null; selectedMessage = null }
    LaunchedEffect(state.screen, busy) { if (!busy && state.screen != MailScreen.CONTACTS) pickingRecipient = false }

    val actions = buildList {
        // Radiant's shared page header exposes back as a trailing action.
        if (LocalAppUiTheme.current == AppUiTheme.RADIANT) {
            add(TrailingAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = back))
        }
        if (state.connected && rootPage) {
            add(TrailingAction(Icons.Outlined.FolderOpen, "切换邮件文件夹") { if (!busy) showFolders = true })
            if (LocalAppUiTheme.current != AppUiTheme.RADIANT) {
                add(TrailingAction(Icons.Outlined.Refresh, "刷新邮箱") { if (!busy) vm.refresh() })
            }
        }
        if (state.connected) add(TrailingAction(Icons.Outlined.MoreHoriz, "邮箱更多操作") { if (!busy) showMore = true })
        if (state.connected && state.screen == MailScreen.DETAIL) state.detail?.let { detail ->
            val fromTrash = state.messages.firstOrNull { it.id == detail.id }?.folderId == 4 || state.folderId == 4
            add(TrailingAction(if (fromTrash) Icons.Outlined.MoveToInbox else Icons.Outlined.DeleteOutline,
                if (fromTrash) "移到收件箱" else "删除邮件") {
                if (!busy) confirmMove = MailMoveConfirmation(detail.id, detail.subject, if (fromTrash) 1 else 4,
                    state.draft.draftId == detail.id && state.draftDirty)
            })
        }
    }
    AppPageScaffold(
        title = when (state.screen) {
            MailScreen.MESSAGES -> "学生邮箱"
            MailScreen.DETAIL -> "邮件详情"
            MailScreen.COMPOSE -> "写邮件"
            MailScreen.CONTACTS -> if (pickingRecipient) "选择收件人" else "联系人"
            MailScreen.ATTACHMENTS -> "附件"
        },
        onBack = back,
        modifier = Modifier.imePadding(),
        actions = actions,
        trailingContent = if (state.connected && state.screen == MailScreen.COMPOSE) ({
            AppButton(
                onClick = { focus.clearFocus(); confirmSend = true },
                enabled = !busy && !state.sendUncertain && !state.draftSaveUncertain && !state.draft.unsupportedResources &&
                    listOf(state.draft.to, state.draft.cc, state.draft.bcc).any(String::isNotBlank)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.sending) "发送中" else "发送")
            }
        }) else null,
        freeContent = {
            Column(Modifier.fillMaxSize()) {
                if (state.account.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = AppComponentTokens.HeaderHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            state.account,
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "复制",
                            modifier = Modifier.clip(SmoothRoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) {
                                    context.getSystemService(android.content.ClipboardManager::class.java)?.let { clipboard ->
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("邮箱地址", state.account))
                                        Toast.makeText(context, "邮箱地址已复制", Toast.LENGTH_SHORT).show()
                                    }
                                }.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                state.transfer?.let { transfer ->
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppCircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text((if (transfer.uploading) "正在上传 · " else "正在下载 · ") + transfer.name,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (transfer.total > 0) "${mailFileSize(transfer.bytes)} / ${mailFileSize(transfer.total)}"
                                    else mailFileSize(transfer.bytes), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AppHeaderIconButton(Icons.Outlined.Close, contentDescription = "取消附件传输", onClick = vm::cancelTransfer)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val content: @Composable () -> Unit = {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = if (rootPage) 96.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            state.error?.let { error ->
                                item("error") {
                                    AppStateCard.Error(
                                        message = error,
                                        title = when { state.mailMoveUncertain -> "请先确认邮件位置"; state.sendUncertain -> "请先确认发送结果"; state.draftSaveUncertain -> "请先确认草稿保存结果"; else -> "邮箱操作未完成" },
                                        onRetry = if (state.sendUncertain || state.draftSaveUncertain) null else ({
                                            if (!busy) { if (state.connected) vm.refresh() else vm.connect() }
                                        })
                                    )
                                    if (state.draftSaveUncertain) AppButton(vm::showDrafts, Modifier.padding(horizontal = 16.dp),
                                        enabled = !busy, variant = AppButtonVariant.Secondary) { Text("查看草稿箱") }
                                }
                            }
                            state.notice?.let { notice ->
                                item("notice") {
                                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.Outlined.CheckCircleOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            AppHeaderIconButton(Icons.Outlined.Close, contentDescription = "关闭提示", onClick = vm::dismissNotice)
                                        }
                                    }
                                }
                            }
                            if (!state.connected) {
                                item("connection") {
                                    if (state.loading) AppStateCard.Loading("正在连接学生邮箱…")
                                    else if (state.error == null) AppStateCard.Empty(
                                        "登录后即可查看邮件",
                                        icon = Icons.Outlined.MailOutline,
                                        subtitle = "使用当前智慧安大账号，首次使用需已开通学校邮箱。",
                                        actionLabel = "连接邮箱",
                                        onAction = vm::connect
                                    )
                                }
                            } else when (state.screen) {
                                MailScreen.MESSAGES -> messagesContent(state, messageQuery, { messageQuery = it },
                                    { focus.clearFocus(); showFolders = true }, { selectedMessage = it }, vm)
                                MailScreen.DETAIL -> detailContent(state, download, vm::bodyImages)
                                MailScreen.COMPOSE -> composeMailContent(state, vm::updateDraft, {
                                        focus.clearFocus()
                                        pickingRecipient = true
                                        vm.showContacts()
                                    }, {
                                        focus.clearFocus()
                                        if (vm.prepareUpload()) chooseAttachments.launch(arrayOf("*/*"))
                                    }, { vm.saveDraft() }, vm::removeDraftAttachment, vm::previewSavedDraft)
                                MailScreen.CONTACTS -> contactsContent(state, contactQuery, { contactQuery = it }) { contact ->
                                    focus.clearFocus()
                                    if (pickingRecipient) {
                                        pickingRecipient = false
                                        vm.startCompose(contact)
                                    } else selectedContact = contact
                                }
                                MailScreen.ATTACHMENTS -> attachmentsContent(state, download) { vm.openWebsite(context) }
                            }
                        }
                    }
                    if (state.connected && rootPage) {
                        PullToRefreshBox(state.loading, { if (!busy) vm.refresh() }, Modifier.fillMaxSize()) { content() }
                    } else content()
                    if (state.connected && rootPage && !pickingRecipient) {
                        AppFloatingActionButton(
                            onClick = { if (!busy) { focus.clearFocus(); vm.startCompose() } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 18.dp)
                        ) { Icon(Icons.Outlined.Edit, "写邮件") }
                    }
                }
                if (state.connected && rootPage && !pickingRecipient) MailNavigation(
                    state.screen, enabled = !busy,
                    messages = { focus.clearFocus(); vm.back() },
                    contacts = { focus.clearFocus(); vm.showContacts() },
                    attachments = { focus.clearFocus(); vm.showAttachments() }
                )
                if (state.connected && state.screen == MailScreen.DETAIL) {
                    MailPanel(Modifier.padding(bottom = 12.dp)) {
                        if (state.detail?.id == state.draft.draftId) {
                            AppButton({ vm.startCompose() }, Modifier.fillMaxWidth(), enabled = !busy) { Text("继续编辑草稿") }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppButton({ focus.clearFocus(); vm.reply() }, Modifier.weight(1f), enabled = !busy) {
                                Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp)); Text("回复")
                            }
                            AppButton({ focus.clearFocus(); vm.forward() }, Modifier.weight(1f), enabled = !busy, variant = AppButtonVariant.Secondary) {
                                Icon(Icons.AutoMirrored.Outlined.Forward, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp)); Text("转发")
                            }
                        }
                    }
                }
            }
        }
    )

    if (showFolders) AppModalBottomSheet("邮件文件夹", { showFolders = false }) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            state.folders.forEach { folder ->
                FolderRow(folder, state.folderId == folder.id) {
                    showFolders = false
                    messageQuery = ""
                    pickingRecipient = false
                    vm.selectFolder(folder.id)
                }
            }
        }
    }
    if (showMore) AppModalBottomSheet("邮箱更多操作", { showMore = false }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasDraft) SheetAction(Icons.Outlined.Edit, "继续编辑", "尚未发送的邮件") {
                showMore = false; vm.startCompose()
            }
            if (hasDraft) SheetAction(Icons.Outlined.Save, "保存草稿", "保存到邮箱服务器，下次可以继续编辑") {
                showMore = false; vm.saveDraft()
            }
            SheetAction(Icons.Outlined.Drafts, "草稿箱", "打开已保存的草稿继续编辑") {
                showMore = false; vm.showDrafts()
            }
            if (hasDraft) SheetAction(Icons.Outlined.DeleteOutline, "清空编辑内容", "不会删除服务器中的邮件") {
                showMore = false; confirmDiscard = true
            }
            if (state.screen == MailScreen.COMPOSE && !state.sendUncertain && !state.draftSaveUncertain) state.draft.draftId?.let { id ->
                SheetAction(Icons.Outlined.DeleteOutline, "删除这封草稿", "移到已删除，可在其中找回") {
                    showMore = false
                    confirmMove = MailMoveConfirmation(id, state.draft.subject, 4, state.draftDirty)
                }
            }
            SheetAction(Icons.Outlined.OpenInBrowser, "邮箱完整版", "复杂富文本、超大附件及更多设置") {
                showMore = false; vm.openWebsite(context)
            }
        }
    }
    selectedMessage?.let { message ->
        AppModalBottomSheet("邮件操作", { selectedMessage = null }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message.subject.ifBlank { "（无主题）" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(message.from, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SheetAction(Icons.Outlined.MailOutline, if (message.folderId == 2) "编辑草稿" else "打开邮件", "查看邮件内容") {
                    selectedMessage = null
                    if (!busy) vm.openMessage(message)
                }
                val fromTrash = message.folderId == 4
                SheetAction(if (fromTrash) Icons.Outlined.MoveToInbox else Icons.Outlined.DeleteOutline,
                    if (fromTrash) "移到收件箱" else "删除邮件",
                    if (fromTrash) "在收件箱继续查看" else "移到已删除，可在其中找回") {
                    selectedMessage = null
                    if (!busy) confirmMove = MailMoveConfirmation(message.id, message.subject, if (fromTrash) 1 else 4,
                        state.draft.draftId == message.id && state.draftDirty)
                }
            }
        }
    }
    confirmMove?.let { move ->
        AppDialog(
            title = if (move.targetFolder == 4) "删除这封邮件？" else "移到收件箱？",
            subtitle = move.subject.ifBlank { "（无主题）" },
            onDismiss = { confirmMove = null },
            contentScrollable = true,
            actions = listOf(
                AppDialogAction("取消", { confirmMove = null }),
                AppDialogAction(if (move.targetFolder == 4) "删除" else "移动", {
                    focus.clearFocus()
                    confirmMove = null
                    vm.moveMessage(move.id, move.targetFolder)
                }, if (move.targetFolder == 4) AppDialogActionStyle.Danger else AppDialogActionStyle.Primary, enabled = !busy)
            )
        ) {
            Text(if (move.targetFolder == 4) "邮件会移到“已删除”，仍可移回收件箱。" else "邮件会移到收件箱。")
            if (move.discardEditing) Text("此草稿尚未保存的修改将被放弃。")
        }
    }
    if (confirmSend) AppDialog(
        title = "发送邮件？",
        onDismiss = { confirmSend = false },
        subtitle = state.draft.subject.ifBlank { "（无主题）" },
        contentScrollable = true,
        actions = listOf(
            AppDialogAction("继续编辑", { confirmSend = false }),
            AppDialogAction("发送", { confirmSend = false; vm.send() }, AppDialogActionStyle.Primary, !busy && !state.sendUncertain)
        )
    ) {
        Text("收件人：" + state.draft.to)
        if (state.draft.cc.isNotBlank()) Text("抄送：" + state.draft.cc)
        if (state.draft.bcc.isNotBlank()) Text("密送：" + state.draft.bcc)
        Text("发件人：" + state.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.draft.attachments.isNotEmpty()) Text("附件：${state.draft.attachments.size} 个")
    }
    if (confirmDiscard) AppDialog(
        title = "清空编辑内容？",
        onDismiss = { confirmDiscard = false },
        actions = listOf(
            AppDialogAction("保留", { confirmDiscard = false }),
            AppDialogAction("清空", { confirmDiscard = false; vm.discardDraft() }, AppDialogActionStyle.Danger, !state.sending)
        )
    ) { Text(if (state.sendUncertain) "请先检查已发送，确认邮件是否已发出。清空后无法恢复当前内容。" else "当前未发送的内容将被清空，无法恢复。") }
    if (confirmLeave) AppDialog(
        title = "退出邮箱？",
        onDismiss = { confirmLeave = false },
        actions = listOf(
            AppDialogAction("继续编辑", { confirmLeave = false; vm.startCompose() }, AppDialogActionStyle.Primary),
            AppDialogAction("保存并退出", { confirmLeave = false; vm.saveDraft(onSaved = onBack) },
                enabled = !busy && !state.sendUncertain && !state.draftSaveUncertain && !state.draft.unsupportedResources),
            AppDialogAction("放弃更改并退出", { confirmLeave = false; onBack() })
        )
    ) { Text("当前更改尚未保存，可以先保存到草稿箱。放弃更改不会删除已保存的服务器草稿。") }
    selectedContact?.let { contact ->
        AppModalBottomSheet("联系人", { selectedContact = null }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MailAvatar(contact.name.ifBlank { contact.email }, large = true)
                    Column(Modifier.weight(1f)) {
                        Text(mailDisplayName(contact.name.ifBlank { contact.email }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SelectionContainer { Text(contact.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    AppHeaderIconButton(Icons.Outlined.ContentCopy, contentDescription = "复制邮箱地址", onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(
                            android.content.ClipData.newPlainText("邮箱地址", contact.email))
                        Toast.makeText(context, "邮箱地址已复制", Toast.LENGTH_SHORT).show()
                    })
                }
                AppButton({ selectedContact = null; vm.startCompose(contact) }, Modifier.fillMaxWidth(), enabled = !busy && !state.sendUncertain) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("写邮件")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("抄送", "密送").forEachIndexed { index, label ->
                        AppButton({
                            fun append(value: String) = StudentMailProtocol.splitAddresses(value).plus(contact.email).distinct().joinToString("; ")
                            vm.updateDraft(if (index == 0) state.draft.copy(cc = append(state.draft.cc)) else state.draft.copy(bcc = append(state.draft.bcc)))
                            selectedContact = null; vm.startCompose()
                        }, Modifier.weight(1f), enabled = !busy && !state.sendUncertain, variant = AppButtonVariant.Secondary) { Text("添加为" + label) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.messagesContent(
    state: StudentMailUiState, query: String, onQuery: (String) -> Unit,
    selectFolder: () -> Unit, messageActions: (MailMessage) -> Unit, vm: StudentMailViewModel
) {
    item("search") { AppSearchField(query, onQuery, "搜索邮件", Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
    item("folder") {
        val folder = state.folders.firstOrNull { it.id == state.folderId }
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = selectFolder) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(folderIcon(state.folderId), null, tint = MaterialTheme.colorScheme.primary)
                Text(folder?.name ?: "邮件", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if ((folder?.unread ?: 0) > 0) Text("${folder!!.unread} 未读", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.ExpandMore, "切换文件夹")
            }
        }
    }
    item("filters") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppFilterChip(!state.unreadOnly, { vm.setUnreadOnly(false) }, { Text("全部") }, enabled = !state.loading)
            AppFilterChip(state.unreadOnly, { vm.setUnreadOnly(true) }, { Text("未读") }, enabled = !state.loading)
            Spacer(Modifier.weight(1f))
            Text(state.total.toString() + " 封", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val filtered = state.messages.filter { query.isBlank() || listOf(it.subject, it.from, it.summary).any { value -> value.contains(query, true) } }
    if (query.isNotBlank()) item("search-scope") {
        MailSectionLabel("在已加载的 " + state.messages.size + " 封邮件中找到 " + filtered.size + " 封")
    }
    if (filtered.isEmpty() && !state.loading) item("empty") {
        AppStateCard.Empty(
            if (query.isNotBlank()) "没有找到相关邮件" else if (state.unreadOnly) "没有未读邮件" else "这个文件夹还没有邮件",
            icon = Icons.Outlined.MailOutline,
            subtitle = if (query.isNotBlank()) "试试其他关键词，或先加载更多邮件。" else null
        )
    }
    var previousGroup: String? = null
    filtered.forEach { message ->
        val group = mailDateGroup(message.date)
        if (query.isBlank() && group != previousGroup) {
            item("date:" + message.id) { MailSectionLabel(group) }
            previousGroup = group
        }
        item("message:" + message.id, contentType = "message") {
            MailMessageRow(message, state.folderId == 3,
                enabled = !state.loading && !state.sending && !state.saving && state.transfer == null,
                onActions = { messageActions(message) }, onClick = { vm.openMessage(message) })
        }
    }
    if (state.messages.size < state.total) item("more") {
        AppButton(vm::loadMore, Modifier.fillMaxWidth().padding(horizontal = 16.dp), enabled = !state.loading, variant = AppButtonVariant.Secondary) {
            Text("加载更多 · 已显示 " + state.messages.size + " 封")
        }
    }
}

@Composable
private fun MailMessageRow(message: MailMessage, sent: Boolean, enabled: Boolean, onActions: () -> Unit, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val sender = mailDisplayName(if (sent) message.to.firstOrNull().orEmpty() else message.from).ifBlank { if (sent) "未知收件人" else "未知发件人" }
    AppCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .combinedClickable(enabled = enabled, role = Role.Button, onLongClickLabel = "邮件操作",
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onActions() },
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() })
            .semantics { stateDescription = if (message.read) "已读" else "未读" },
        contentPadding = PaddingValues(14.dp), enabled = enabled
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box {
                MailAvatar(sender)
                if (!message.read) Box(Modifier.align(Alignment.BottomEnd).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sender, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = if (message.read) FontWeight.Medium else FontWeight.Bold)
                    Text(mailDisplayDate(message.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(message.subject.ifBlank { "（无主题）" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = if (message.read) FontWeight.Normal else FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(message.summary.ifBlank { "（无摘要）" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.attachments.isNotEmpty()) Icon(Icons.Outlined.AttachFile, "包含附件", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun LazyListScope.detailContent(state: StudentMailUiState, download: (MailAttachment) -> Unit,
    bodyImages: (MailDetail, Boolean) -> StudentMailBodyImages?) {
    val detail = state.detail ?: return
    item("subject") {
        SelectionContainer {
            Text(detail.subject.ifBlank { "（无主题）" }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
    item("sender") {
        var expanded by remember(detail.id) { mutableStateOf(false) }
        val sender = detail.from.firstOrNull().orEmpty()
        MailPanel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MailAvatar(mailDisplayName(sender))
                Column(Modifier.weight(1f)) {
                    Text(mailDisplayName(sender), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(state.messages.firstOrNull { it.id == detail.id }?.date.orEmpty(),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("发送给 " + detail.to.joinToString("、", transform = ::mailDisplayName), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppHeaderIconButton(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起收件人详情" else "查看收件人详情", onClick = { expanded = !expanded })
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("发件人：" + detail.from.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                        Text("收件人：" + detail.to.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                        if (detail.cc.isNotEmpty()) Text("抄送：" + detail.cc.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    item("body") {
        MailPanel { StudentMailBodyContent(detail, bodyImages) }
    }
    if (detail.attachments.isNotEmpty()) {
        item("attachment-title") { MailSectionLabel("附件 · " + detail.attachments.size) }
        items(detail.attachments) { attachment ->
            MailPanel {
                AttachmentMetadata(attachment)
                Spacer(Modifier.height(10.dp))
                AppButton({ download(attachment) }, enabled = state.transfer == null && !state.loading,
                    variant = AppButtonVariant.Secondary) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("下载附件")
                }
            }
        }
    }
}

internal fun LazyListScope.composeMailContent(state: StudentMailUiState, update: (MailDraft) -> Unit, showContacts: () -> Unit,
    addAttachments: () -> Unit, saveDraft: () -> Unit, removeAttachment: (String) -> Unit, previewSaved: () -> Unit) {
    val draft = state.draft
    val editable = !state.loading && !state.sending && !state.saving && state.transfer == null &&
        !state.sendUncertain && !state.draftSaveUncertain && !draft.unsupportedResources
    item("editor-recipients", contentType = "editor-recipients") {
        var expandRecipients by rememberSaveable { mutableStateOf(false) }
        val recipientsVisible = expandRecipients || draft.cc.isNotBlank() || draft.bcc.isNotBlank()
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(draft.to, { update(draft.copy(to = it)) }, "收件人", Modifier.weight(1f).heightIn(max = 144.dp), enabled = editable, singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                AppHeaderIconButton(Icons.Outlined.PersonAddAlt, contentDescription = "从联系人选择收件人", onClick = { if (editable) showContacts() })
            }
            if (recipientsVisible) {
                AppTextField(draft.cc, { update(draft.copy(cc = it)) }, "抄送", Modifier.fillMaxWidth().heightIn(max = 144.dp), enabled = editable, singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                AppTextField(draft.bcc, { update(draft.copy(bcc = it)) }, "密送", Modifier.fillMaxWidth().heightIn(max = 144.dp), enabled = editable, singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            } else {
                AppButton({ expandRecipients = true }, variant = AppButtonVariant.Secondary, enabled = editable) { Text("抄送 / 密送") }
            }
        }
    }
    item("editor-subject", contentType = "editor-field") {
        AppTextField(draft.subject, { update(draft.copy(subject = it)) }, "主题", Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 144.dp), enabled = editable, singleLine = false)
    }
    if (draft.unsupportedResources) item("editor-unsupported") {
        Text("此草稿包含复杂内嵌或云附件，请使用邮箱完整版编辑以保留内容。", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    items(draft.attachments, key = { "editor-attachment-${it.id}" }, contentType = { "editor-attachment" }) { attachment ->
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(12.dp)) {
            AttachmentMetadata(MailAttachment(attachment.id, attachment.name, attachment.size))
            AppButton({ removeAttachment(attachment.id) }, enabled = editable,
                variant = AppButtonVariant.Secondary) { Text("移除附件") }
        }
    }
    if (draft.draftId != null && draft.attachments.isNotEmpty()) item("editor-preview") {
        AppButton(previewSaved, Modifier.fillMaxWidth().padding(horizontal = 16.dp), enabled = editable, variant = AppButtonVariant.Secondary) {
            Text("查看已保存的附件")
        }
    }
    item("editor-body", contentType = "editor-body") {
        // Bound the native text editor so a long quoted message never creates an oversized glass layer.
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(18.dp)) {
            BasicTextField(
                value = draft.body, onValueChange = { update(draft.copy(body = it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 208.dp, max = 320.dp),
                enabled = editable,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                        if (draft.body.isEmpty()) Text("写下邮件内容…", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                }
            )
        }
    }
    item("editor-actions", contentType = "editor-actions") {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(addAttachments, Modifier.weight(1f), enabled = editable, variant = AppButtonVariant.Secondary) {
                Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("添加附件")
            }
            AppButton(saveDraft, Modifier.weight(1f), enabled = editable && !state.draftSaveUncertain,
                variant = AppButtonVariant.Secondary) {
                Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (state.saving) "保存中" else "保存草稿")
            }
        }
    }
    item("editor-status") {
        Text(when { state.draftDirty -> "有未保存的更改"; draft.draftId != null -> "已保存到草稿箱"; else -> "点击保存草稿，可在下次进入时继续编辑" }, Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item("editor-hint") {
        Text("多个邮箱地址用分号分隔。普通附件单个不超过 50 MB，上传完成后才能发送。", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.sending) item("editor-sending") {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCircularProgressIndicator(modifier = Modifier.size(20.dp)); Text("正在发送，请稍候…")
        }
    }
}

private fun LazyListScope.contactsContent(state: StudentMailUiState, query: String, onQuery: (String) -> Unit, select: (MailContact) -> Unit) {
    item("contact-search") { AppSearchField(query, onQuery, "搜索姓名或邮箱", Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
    val contacts = state.contacts.filter { query.isBlank() || it.name.contains(query, true) || it.email.contains(query, true) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.email } })
    item("contact-title") { MailSectionLabel("个人与最近联系人 · " + contacts.size) }
    if (contacts.isEmpty() && !state.loading) item("empty") {
        AppStateCard.Empty(if (query.isBlank()) "暂无联系人" else "没有找到相关联系人", icon = Icons.Outlined.PeopleOutline)
    }
    items(contacts, key = { it.email.lowercase() }, contentType = { "contact" }) { contact ->
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(14.dp), enabled = !state.loading, onClick = { select(contact) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MailAvatar(contact.name.ifBlank { contact.email })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mailDisplayName(contact.name.ifBlank { contact.email }), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(contact.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.attachmentsContent(state: StudentMailUiState, download: (MailAttachment) -> Unit, openWebsite: () -> Unit) {
    item("attachment-title") { MailSectionLabel("最近附件 · " + state.attachments.size) }
    if (state.attachments.isEmpty() && !state.loading) item("empty") {
        AppStateCard.Empty("暂无附件", icon = Icons.Outlined.AttachFile)
    }
    items(state.attachments) { attachment ->
        MailPanel {
            AttachmentMetadata(attachment)
            Spacer(Modifier.height(10.dp))
            AppButton({ download(attachment) }, enabled = !state.loading && state.transfer == null,
                variant = AppButtonVariant.Secondary) { Text("下载附件") }
        }
    }
    item("attachment-help") {
        MailPanel {
            Text("下载与上传附件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("可直接下载到你选择的位置；写邮件时点击添加附件即可上传。复杂附件也可通过完整版处理。", Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppButton(openWebsite, Modifier.fillMaxWidth(), enabled = !state.loading, variant = AppButtonVariant.Secondary) { Text("打开邮箱完整版") }
        }
    }
}

@Composable
private fun MailNavigation(screen: MailScreen, enabled: Boolean, messages: () -> Unit, contacts: () -> Unit, attachments: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), contentPadding = PaddingValues(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val tabs = listOf(Triple(MailScreen.MESSAGES, Icons.Outlined.MailOutline, "邮件"),
                Triple(MailScreen.CONTACTS, Icons.Outlined.PeopleOutline, "联系人"), Triple(MailScreen.ATTACHMENTS, Icons.Outlined.AttachFile, "附件"))
            tabs.forEachIndexed { index, (page, icon, label) ->
                val selected = screen == page
                val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).clip(SmoothRoundedCornerShape(14.dp))
                        .semantics { this.selected = selected }
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = enabled, role = Role.Tab, onClick = listOf(messages, contacts, attachments)[index])
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = color)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: MailFolder, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(SmoothRoundedCornerShape(14.dp))
            .semantics { this.selected = selected }
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(folderIcon(folder.id), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(folder.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (folder.unread > 0) Text(folder.unread.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        else Text(folder.total.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        if (selected) Icon(Icons.Outlined.Check, "当前文件夹", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

private fun folderIcon(id: Int): ImageVector = when (id) {
    1 -> Icons.Outlined.Inbox
    2 -> Icons.Outlined.Drafts
    3 -> Icons.AutoMirrored.Outlined.Send
    4 -> Icons.Outlined.DeleteOutline
    5 -> Icons.Outlined.ReportGmailerrorred
    else -> Icons.Outlined.FolderOpen
}

@Composable
private fun MailAvatar(name: String, large: Boolean = false) {
    Box(Modifier.size(if (large) 52.dp else 40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "邮",
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MailSectionLabel(title: String) {
    Text(title, Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SheetAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MailPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(18.dp), content = content)
}

@Composable
private fun AttachmentMetadata(attachment: MailAttachment) {
    val size = mailFileSize(attachment.size)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(SmoothRoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        SelectionContainer(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(attachment.name.ifBlank { "未命名附件" }, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun mailFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> bytes.toString() + " B"
}

internal fun mailDisplayName(address: String): String {
    val name = address.substringBefore('<').trim().trim('"', '\'')
    return if (address.contains('<') && name.isNotBlank()) name else mailAddressOf(address).substringBefore('@').ifBlank { address }
}

internal fun mailDateGroup(date: String, today: LocalDate = LocalDate.now()): String {
    val day = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return "邮件"
    return when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> if (day.year == today.year) day.monthValue.toString() + " 月" else day.year.toString() + " 年 " + day.monthValue + " 月"
    }
}

internal fun mailDisplayDate(date: String, today: LocalDate = LocalDate.now()): String {
    val day = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return date.take(16)
    return when (day) {
        today -> date.substringAfter(' ', "").take(5).ifBlank { "今天" }
        today.minusDays(1) -> "昨天"
        else -> if (day.year == today.year) day.monthValue.toString() + "/" + day.dayOfMonth else day.year.toString() + "/" + day.monthValue + "/" + day.dayOfMonth
    }
}
