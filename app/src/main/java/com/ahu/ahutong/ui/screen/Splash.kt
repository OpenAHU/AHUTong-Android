package com.ahu.ahutong.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.SplashViewModel
import com.ahu.ahutong.ui.state.BootstrapTrainingOnboardingState
import com.ahu.ahutong.ui.state.TelemetryOnboardingState
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun Splash(
    navController: NavController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    var dialogRevision by remember { mutableIntStateOf(0) }
    val telemetryState by viewModel.telemetryOnboardingState.collectAsState()
    val bootstrapTrainingState by viewModel.bootstrapTrainingOnboardingState.collectAsState()
    val activity = LocalActivity.current

    val agreementAccepted = AHUCache.isAgreementAccepted()
    val privacyAccepted = AHUCache.isPrivacyAccepted()
    val businessAccepted = AHUCache.isBusinessAccepted()
    val telemetryChoice = (telemetryState as? TelemetryOnboardingState.Ready)?.choice
    val bootstrapTrainingChoice =
        (bootstrapTrainingState as? BootstrapTrainingOnboardingState.Ready)?.choice

    LaunchedEffect(dialogRevision, telemetryState, bootstrapTrainingState) {
        if (agreementAccepted && privacyAccepted && businessAccepted &&
            telemetryChoice != null && bootstrapTrainingChoice != null
        ) {
            if (AHUCache.isLogin()) {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    val onboardingReady = telemetryState is TelemetryOnboardingState.Ready &&
        bootstrapTrainingState is BootstrapTrainingOnboardingState.Ready
    val requiresAcceptance = !agreementAccepted || !privacyAccepted || !businessAccepted ||
        telemetryChoice == null || bootstrapTrainingChoice == null
    if (onboardingReady && requiresAcceptance) {
        UnifiedPrivacyPolicyDialog(
            onAgree = {
                AHUCache.setAgreementAccepted()
                AHUCache.setPrivacyAccepted()
                AHUCache.setBusinessAccepted()
                viewModel.acceptUnifiedPrivacyPolicy()
                dialogRevision++
            },
            onDisagree = { activity?.finish() }
        )
    }
}

@Composable
private fun UnifiedPrivacyPolicyDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    OnboardingDialogTemplate(
        title = "隐私政策",
        body = """
            一、开源项目与使用提示

            1. 安大通是完全开源的项目，任何人均可基于本项目进行二次开发或分发。
            2. 非官方渠道提供的版本可能被修改并产生安全风险，请确认安装包来源可信。
            3. 使用非官方或二次开发版本时，请自行判断其安全性并承担相应风险。因非官方版本造成的损失，原项目开发者不承担责任。

            二、个人与学校业务数据

            1. 安大通不会把学号、账号、课表、成绩、交易内容等个人或学业数据上传到安大通自有云服务器。
            2. 学校业务接口仅用于完成您主动发起的登录、查询、缴费等学校服务请求。您的个人数据不会被安大通分享给第三方。
            3. 个性化学习记录默认保存在本机，您可以在设置中管理本地记录的保留期限。

            三、帮助改进模型质量

            1. 同意本政策后，应用会启用模型质量评估。每类任务至少积累 64 条新有效样本时，每天最多上传一次去标识化聚合指标。
            2. 聚合指标包括普通下一步、多跳目标、参数排序和候选模型的准确率、误差与置信度分桶，以及建议的聚合展示、点击、完成、关闭、超时和门禁计数。
            3. 单个动作等细分项不足 30 条不会上传。服务端仅按版本和任务聚合，初始保存期限最多 90 天。
            4. 该过程不会上传原始行为、页面或旅程序列、逐次标签、逐次概率、特征向量、设置值、参数内容、指纹、模型权重、学号、账号或硬件标识。

            四、帮助训练通用预测模型

            1. 同意本政策后，应用会贡献本机已生成、可直接用于训练的去标识化样本，并包含最近 30 天已有的兼容样本，无需再次勾选确认。
            2. 训练样本包括下一步和多跳预测的数值特征、候选可用性、目标标签，以及参数推荐的候选排序特征和分级反馈。
            3. 不会上传原始页面轨迹、完整旅程、设置值、参数内容、presetId、指纹、学号、账号、设备标识或模型权重。
            4. 每次授权会生成随机参与者编号，仅用于隔离训练、验证和测试数据，以及将来的联邦学习模拟。

            五、控制与撤回

            1. 模型质量评估和训练数据贡献不影响学校业务功能或本地预测。
            2. 您可以在设置中随时关闭相关贡献。关闭训练数据贡献后，应用会停止收集，并请求删除该随机参与者编号下已上传的数据。

            六、商业合作与反馈

            安大通仍在探索可持续发展方式。如您愿意参与发展规划，或对应用有想法和建议，欢迎加入 QQ 群 1006203134 联系我们。

            点击“同意并继续”，表示您已阅读并同意以上隐私政策、数据处理说明及开源使用提示。
        """.trimIndent(),
        confirmText = "同意并继续",
        dismissText = "拒绝",
        onConfirm = onAgree,
        onDismiss = onDisagree,
        buttonWidth = 120.dp
    )
}

@Composable
private fun OnboardingDialogTemplate(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit = {},
    buttonWidth: androidx.compose.ui.unit.Dp = 88.dp
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = 10.n1 withNight 90.n1
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = 10.n1 withNight 90.n1
                )
            }
        },
        shape = SmoothRoundedCornerShape(32.dp),
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                modifier = Modifier.size(buttonWidth, 56.dp),
                shape = SmoothRoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = 90.a1 withNight 85.a1,
                    contentColor = 0.n1
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.size(buttonWidth, 56.dp),
                shape = SmoothRoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = 90.a1 withNight 85.a1,
                    contentColor = 0.n1
                )
            ) {
                Text(dismissText)
            }
        },
        containerColor = 100.n1 withNight 20.n1
    )
}
