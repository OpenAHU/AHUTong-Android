package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.n1
import com.kyant.monet.withNight


@Composable
fun ElectricityCard(
    navController: NavHostController,
) {
    val shape = SmoothRoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .appLiquidGlassSurface(
                shape = shape,
                fallbackColor = 100.n1 withNight 20.n1
            )
            .clickable {
                navController.navigate("electricity_pay")
            }
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "电费充值",
            modifier = Modifier.padding(horizontal = 24.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

    }
}
