package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.commits.ActiveAuthor
import com.ahu.ahutong.data.commits.CommitStatsSource

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 贡献名单：前辈（21/22 级）来自本地名单；活跃开发者的概述来自公开的提交统计。
 */
@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val commitStats: CommitStatsSource
) : ViewModel() {

    class Senior(val name: String, val desc: String, val qq: String) {
        val avatar: String
            get() = "https://q1.qlogo.cn/g?b=qq&nk=$qq&s=640"

        /** 摊开时空间有限：去掉年级后缀与占位空格。 */
        val shortName: String
            get() = name.substringBefore('（').replace(" ", "")

        fun openQq(context: Context) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "mqqapi://card/show_pslcard?&uin=$qq".toUri()
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "请安装 QQ 或 Tim", Toast.LENGTH_SHORT).show()
            }
        }
    }

    sealed interface CommitState {
        data object Loading : CommitState
        data class Ready(val authors: List<ActiveAuthor>) : CommitState
        data object Failed : CommitState
    }

    val seniors: List<Senior> = listOf(
        Senior("高玉灿（20级）", "架构规划、页面设计、爬虫", "468766131"),
        Senior("谭哲昊（21级）", "架构规划、小组件", "330771794"),
        Senior("王学雷（22级）", "页面设计、交互设计、新技术探索", "257314409"),
        Senior("徐健灿（22级）", "爬虫、交互设计", "3148336396"),
        Senior("王    钰（22级）", "架构规划、爬虫", "605606366")
    )

    private val _commits = MutableStateFlow<CommitState>(CommitState.Loading)
    val commits: StateFlow<CommitState> = _commits.asStateFlow()

    /** 展示顺序：先固定已知登录名的先后，未知的保持接口顺序排在后面。 */
    private val authorOrder = listOf("Kaltsit-cell", "MuxYang", "InChange-Jiang")

    init {
        refreshCommits()
    }

    fun refreshCommits() {
        _commits.value = CommitState.Loading
        viewModelScope.launch {
            _commits.value = runCatching { commitStats.load() }
                .fold(
                    onSuccess = { authors ->
                        CommitState.Ready(authors.sortedBy { authorOrder.indexOf(it.login).let { i -> if (i < 0) Int.MAX_VALUE else i } })
                    },
                    onFailure = { CommitState.Failed }
                )
        }
    }

    private val displayNames = mapOf(
        "Kaltsit-cell" to "zirui.xu（24级）",
        "MuxYang" to "MuxYang（25级）",
        "InChange-Jiang" to "InChange-Jiang（25级）"
    )

    private val qqByLogin = mapOf(
        "Kaltsit-cell" to "1793838025",
        "MuxYang" to "32142587",
        "InChange-Jiang" to "2719904894"
    )

    fun displayName(login: String): String = displayNames[login] ?: login

    fun qqOf(login: String): String? = qqByLogin[login]

    /** 未展开时的概述：总提交数、最近活跃日期、涉及的领域。 */
    fun summary(author: ActiveAuthor): String {
        val latest = author.commits.firstOrNull()?.dateCst?.take(10)
        val scopes = author.commits.mapNotNull { commitScope(it.title) }
            .distinct()
            .take(2)
        return buildString {
            if (author.totalCommits > 0) {
                append("共 ${author.totalCommits} 次提交")
            } else {
                append("近 ${author.commits.size} 次提交")
            }
            latest?.let { append("，最近 $it") }
            if (scopes.isNotEmpty()) {
                append("，主要涉及 ")
                append(scopes.joinToString("、"))
            }
        }
    }

    private fun commitScope(title: String): String? {
        val head = title.substringBefore(':')
        val scope = head.substringAfter('(', "").substringBefore(')')
        return scope.takeIf { it.isNotBlank() } ?: head.takeIf { it.isNotBlank() }
    }
}
