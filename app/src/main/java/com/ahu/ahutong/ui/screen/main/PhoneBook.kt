package com.ahu.ahutong.ui.screen.main

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.R
import com.ahu.ahutong.core.designsystem.R as DesignSystemR
import com.ahu.ahutong.data.model.Tel
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.TelDirectoryViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneBook(onBack: (() -> Unit)? = null) {
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

    val pageContent: LazyListScope.() -> Unit = {
        if (isSearchActive) {
            if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "未找到相关结果",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else items(
                items = searchResults,
                key = { tel -> "${tel.name}-${tel.tel}-${tel.tel2}" }
            ) { tel ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TelItem(tel = tel, onItemClick = { selected ->
                        openTelOrChooseCampus(context, selected) { dialData = selected }
                    })
                }
            }
        } else {
            item(key = "category") {
                AppSelectField(
                    label = "部门分类",
                    selected = selectedCategory,
                    options = TelDirectoryViewModel.TelBook.keys.map { category ->
                        AppSelectOption(category, category)
                    },
                    onSelected = { selectedCategory = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    miuixStandalone = true
                )
            }
            items(
                items = TelDirectoryViewModel.TelBook.getValue(selectedCategory),
                key = { tel -> "${selectedCategory}-${tel.name}-${tel.tel}-${tel.tel2}" }
            ) { tel ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TelItem(tel = tel, onItemClick = { selected ->
                        openTelOrChooseCampus(context, selected) { dialData = selected }
                    })
                }
            }
        }
    }

    AppPageScaffold(
        title = stringResource(id = R.string.phone_book),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        search = SecondarySearchState(
            query = searchQuery,
            visible = isSearchActive,
            placeholder = "搜索电话或部门",
            onQueryChange = { searchQuery = it },
            onClose = {
                isSearchActive = false
                searchQuery = ""
            },
            onSubmit = {}
        ),
        trailingContent = {
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_find,
                contentDescription = "搜索",
                onClick = { isSearchActive = true }
            )
        },
        bottomPadding = 48.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        lazyContent = pageContent
    )
    DialDialog(
        onDismiss = { dialData = null },
        tel = dialData
    )
}

private fun openTelOrChooseCampus(
    context: android.content.Context,
    tel: Tel,
    onChooseCampus: () -> Unit
) {
    if (tel.tel != null && tel.tel2 != null && tel.tel != tel.tel2) {
        onChooseCampus()
    } else {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0551-${tel.tel ?: tel.tel2}")))
    }
}

@Composable
private fun TelItem(
    tel: Tel,
    onItemClick: (Tel) -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth(),
        shape = SmoothRoundedCornerShape(20.dp),
        onClick = { onItemClick(tel) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = tel.name,
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tel 已迁到 :core:model，跨模块声明的属性无法智能转换，先取局部变量。
            val primaryTel = tel.tel
            val secondaryTel = tel.tel2
            when {
                primaryTel != null && secondaryTel != null && primaryTel == secondaryTel -> {
                    Tel(tel = primaryTel)
                }

                primaryTel != null && secondaryTel == null -> {
                    Tel(tel = primaryTel, campus = "磬苑")
                }

                primaryTel == null && secondaryTel != null -> {
                    Tel(tel = secondaryTel, campus = "龙河")
                }

                primaryTel != null && secondaryTel != null && primaryTel != secondaryTel -> {
                    Tel(tel = primaryTel, campus = "磬苑")
                    Tel(tel = secondaryTel, campus = "龙河")
                }
            }
        }
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
            .padding(horizontal = 16.dp)
            .appLiquidGlassSurface(
                shape = ContinuousCapsule,
                fallbackColor = 100.n1 withNight 20.n1,
                level = LiquidGlassSurfaceLevel.Panel
            ),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TelDirectoryViewModel.TelBook.keys.toList()) {
            val isSelected = it == selectedCategory
            Text(
                text = it,
                modifier = Modifier
                    .clip(ContinuousCapsule)
                    .background(if (isSelected) 90.a1 else Color.Unspecified)
                    .clickable { onCategorySelected(it) }
                    .padding(16.dp, 8.dp),
                color = if (isSelected) 0.n1 else Color.Unspecified,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun Telephones(
    selectedCategory: String,
    onItemClick: (Tel) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .clip(SmoothRoundedCornerShape(32.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TelDirectoryViewModel.TelBook.getValue(selectedCategory).forEach {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(4.dp))
                    .background(100.n1 withNight 20.n1)
                    .clickable { onItemClick(it) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 同上：模型跨模块后改为局部变量再做判断。
                    val primaryTel = it.tel
                    val secondaryTel = it.tel2
                    when {
                        primaryTel != null && secondaryTel != null && primaryTel == secondaryTel -> {
                            Tel(tel = primaryTel)
                        }

                        primaryTel != null && secondaryTel == null -> {
                            Tel(tel = primaryTel, campus = "磬苑")
                        }

                        primaryTel == null && secondaryTel != null -> {
                            Tel(tel = secondaryTel, campus = "龙河")
                        }

                        primaryTel != null && secondaryTel != null && primaryTel != secondaryTel -> {
                            Tel(tel = primaryTel, campus = "磬苑")
                            Tel(tel = secondaryTel, campus = "龙河")
                        }
                    }
                }
            }
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
                .background(90.a1 withNight 30.n1)
                .padding(8.dp, 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
    Text(
        text = tel,
        color = 50.n1 withNight 80.n1,
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
        AppDialog(
            title = "请选择校区",
            onDismiss = onDismiss,
            actions = listOf(
                AppDialogAction("磬苑校区", onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0551-${tel.tel}")))
                    onDismiss()
                }),
                AppDialogAction("龙河校区", onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0551-${tel.tel2}")))
                    onDismiss()
                })
            )
        )
    }
}
