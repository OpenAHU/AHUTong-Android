package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.feature.home.R
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.theme.AhuDimens


@Composable
fun ElectricityCard(
    navController: NavHostController,
) {
    AhuCard(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
        onClick = { navController.navigate("electricity_pay") },
    ) {
        Text(
            stringResource(R.string.electricity_recharge),
            modifier = Modifier
                .padding(horizontal = AhuDimens.TitleHorizontal)
                .align(Alignment.CenterHorizontally),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
