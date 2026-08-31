package com.ahu.ahutong.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.R
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.ui.state.PaymentState
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

/**
 * 标题栏右上角的支付胶囊确认按钮：
 * 确认(34dp 圆、黑色 check-one 勾) → 支付中(转圈) → 成功(自身撑开显示真实订单号)/失败(红叉)。
 * 与底部大确认按钮不同，这是 Radiant 标题栏的统一动作键。两种状态类型各一个重载，外观一致。
 */
@Composable
fun ConfirmPayButton(
    payState: State<PayState>,
    onClick: () -> Unit
) {
    PayCapsuleCore(
        idle = payState.value is PayState.Idle,
        submitting = payState.value is PayState.InProgress,
        orderId = (payState.value as? PayState.Succeeded)?.message,
        failed = payState.value is PayState.Failed,
        onClick = onClick
    )
}

@Composable
fun ConfirmPayCapsule(
    paymentState: State<PaymentState>,
    onClick: () -> Unit
) {
    PayCapsuleCore(
        idle = paymentState.value is PaymentState.Idle,
        submitting = paymentState.value is PaymentState.Loading,
        orderId = (paymentState.value as? PaymentState.Success)?.orderId,
        failed = paymentState.value is PaymentState.Error,
        onClick = onClick
    )
}

@Composable
private fun PayCapsuleCore(
    idle: Boolean,
    submitting: Boolean,
    orderId: String?,
    failed: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = when {
            idle -> 90.a1 withNight 85.a1
            submitting -> Color(0xFF558B2F)
            orderId != null && !failed -> Color(0xFF2E7D32)
            failed -> Color(0xFFD32F2F)
            else -> Color(0xFF2E7D32)
        },
        label = "confirmPayBg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .animateContentSize(spring(stiffness = Spring.StiffnessLow))
            .then(if (idle) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            idle -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_check_one),
                    "确认",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }

            submitting -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = 100.n1, strokeWidth = 3.dp
                )
            }

            failed -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "支付失败", tint = 100.n1, modifier = Modifier.size(18.dp))
            }

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "支付成功",
                    color = 100.n1,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 12.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = orderId ?: "",
                    color = 100.n1.copy(alpha = 0.92f),
                    maxLines = 1,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}