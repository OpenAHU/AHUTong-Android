package com.ahu.ahutong.ui.screen.main

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.model.Tel
import com.ahu.ahutong.feature.tools.R
import com.ahu.ahutong.ui.components.AhuChip
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuListGroup
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.components.AhuScreenBox
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.TelDirectoryViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.capsule.ContinuousCapsule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneBook() {
    val context = LocalContext.current
    var dialData by rememberSaveable { mutableStateOf<Tel?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf("师生综合服务大厅") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val allTels = remember {
        TelDirectoryViewModel.TelBook.values.flatten()
    }

    val searchResults = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        allTels.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                (it.tel?.contains(searchQuery) == true) ||
                (it.tel2?.contains(searchQuery) == true)
        }
    }

    fun onTelClick(tel: Tel) {
        if (tel.tel != null && tel.tel2 != null && tel.tel != tel.tel2) {
            dialData = tel
        } else {
            context.startActivity(
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:0551-${tel.tel ?: tel.tel2}")
                )
            )
        }
    }

    if (isSearchActive) {
        AhuScreenBox {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AhuHeaderIconButton(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        },
                    )
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_phone_or_dept)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = AhuColors.onSurface,
                            unfocusedTextColor = AhuColors.onSurface,
                            cursorColor = AhuColors.primaryAction,
                        ),
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                AhuHeaderIconButton(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    onClick = { searchQuery = "" },
                                )
                            }
                        } else {
                            null
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AhuDimens.ContentHorizontal),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_search_results),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AhuColors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        items(searchResults) { tel ->
                            TelItem(
                                tel = tel,
                                onItemClick = { onTelClick(it) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        AhuScreen {
            AhuPageHeader(
                title = stringResource(id = R.string.phone_book),
                actions = {
                    AhuHeaderIconButton(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        onClick = { isSearchActive = true },
                    )
                },
            )
            Categories(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )
            Telephones(
                selectedCategory = selectedCategory,
                onItemClick = { onTelClick(it) }
            )
        }
    }

    DialDialog(
        onDismiss = { dialData = null },
        tel = dialData
    )
}

@Composable
private fun TelItem(
    tel: Tel,
    onItemClick: (Tel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(AhuDimens.ListItemCorner))
            .background(AhuColors.card)
            .clickable(role = Role.Button) { onItemClick(tel) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = tel.name,
            style = MaterialTheme.typography.titleMedium,
            color = AhuColors.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelNumbers(tel)
        }
    }
}

@Composable
private fun Categories(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .padding(horizontal = AhuDimens.ContentHorizontal)
            .clip(ContinuousCapsule)
            .background(AhuColors.card),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TelDirectoryViewModel.TelBook.keys.toList()) {
            AhuChip(
                text = it,
                selected = it == selectedCategory,
                onClick = { onCategorySelected(it) },
            )
        }
    }
}

@Composable
private fun Telephones(
    selectedCategory: String,
    onItemClick: (Tel) -> Unit
) {
    AhuListGroup {
        TelDirectoryViewModel.TelBook.getValue(selectedCategory).forEach {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(AhuDimens.ListItemCorner))
                    .background(AhuColors.card)
                    .clickable(role = Role.Button) { onItemClick(it) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = AhuColors.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelNumbers(it)
                }
            }
        }
    }
}

@Composable
private fun TelNumbers(tel: Tel) {
    val primaryTel = tel.tel
    val secondaryTel = tel.tel2
    when {
        primaryTel != null && secondaryTel != null && primaryTel == secondaryTel -> {
            Tel(tel = primaryTel)
        }

        primaryTel != null && secondaryTel == null -> {
            Tel(tel = primaryTel, campus = stringResource(R.string.campus_short_qingyuan))
        }

        primaryTel == null && secondaryTel != null -> {
            Tel(tel = secondaryTel, campus = stringResource(R.string.campus_short_longhe))
        }

        primaryTel != null && secondaryTel != null && primaryTel != secondaryTel -> {
            Tel(tel = primaryTel, campus = stringResource(R.string.campus_short_qingyuan))
            Tel(tel = secondaryTel, campus = stringResource(R.string.campus_short_longhe))
        }
    }
}

@Composable
private fun Tel(
    tel: String,
    campus: String? = null
) {
    campus?.let {
        Text(
            text = it,
            modifier = Modifier
                .padding(4.dp)
                .clip(SmoothRoundedCornerShape(8.dp))
                .background(AhuColors.primaryAction)
                .padding(8.dp, 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = AhuColors.onPrimaryAction,
        )
    }
    Text(
        text = tel,
        color = AhuColors.onSurface.copy(alpha = 0.55f),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun DialDialog(
    onDismiss: () -> Unit,
    tel: Tel?
) {
    val context = LocalContext.current
    if (tel != null) {
        AhuDialog(
            onDismissRequest = onDismiss,
            scrollable = false,
        ) {
            Text(
                text = stringResource(R.string.select_campus),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = AhuColors.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AhuColors.onSurface.copy(alpha = 0.2f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Text(
                    text = stringResource(R.string.campus_qingyuan),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:0551-${tel.tel}"))
                            )
                            onDismiss()
                        }
                        .padding(24.dp, 16.dp),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(AhuColors.onSurface.copy(alpha = 0.2f))
                )
                Text(
                    text = stringResource(R.string.campus_longhe),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:0551-${tel.tel2}"))
                            )
                            onDismiss()
                        }
                        .padding(24.dp, 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
