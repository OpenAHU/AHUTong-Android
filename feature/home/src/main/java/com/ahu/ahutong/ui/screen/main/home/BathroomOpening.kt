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
import androidx.navigation.NavController
import com.ahu.ahutong.feature.home.R
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.theme.AhuDimens

@Composable
fun BathroomOpening(
    navController: NavController,

    ) {
    AhuCard(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
        onClick = { navController.navigate("bathroom_deposit") },
    ) {
        Text(
            text = stringResource(R.string.bathroom_payment),
            modifier = Modifier
                .padding(horizontal = AhuDimens.TitleHorizontal)
                .align(Alignment.CenterHorizontally),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
//        discoveryViewModel.bathroom["桔园浴室"]?.let {
//            Text(
//                text = buildAnnotatedString {
//                    it.forEach {
//                        append(if (it == 'w') "♀️" else "♂️")
//                    }
//                },
//                style = MaterialTheme.typography.titleMedium
//            )
//        }
    }
}
