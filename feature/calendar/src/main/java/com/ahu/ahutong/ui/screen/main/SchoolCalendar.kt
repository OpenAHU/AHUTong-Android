package com.ahu.ahutong.ui.screen.main

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahu.ahutong.feature.calendar.R
import com.ahu.ahutong.ui.components.AhuErrorToastEffect
import com.ahu.ahutong.ui.components.AhuScreenBox
import com.ahu.ahutong.ui.state.SchoolCalendarViewModel
import com.ahu.ahutong.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SchoolCalendar(
    navController: NavHostController,
    viewModel: SchoolCalendarViewModel = hiltViewModel(),
    mockRefreshRevision: Long = 0L,
    isMockMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPreview by remember { mutableStateOf(false) }
    val calendarFile = viewModel.calendarFile
    val isLoading = viewModel.isLoading
    val progress = viewModel.progress

    AhuErrorToastEffect(
        message = viewModel.errorMessage,
        onConsumed = { viewModel.clearError() },
        duration = Toast.LENGTH_SHORT,
    )

    LaunchedEffect(calendarFile) {
        if (calendarFile != null && calendarFile.exists()) {
            showPreview = true
        }
    }

    val savePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && calendarFile != null) {
            scope.launch(Dispatchers.IO) {
                FileUtils.saveImageToGallery(context, calendarFile)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.saved_to_gallery),
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.popBackStack()
                }
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.storage_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val cached = FileUtils.getImageFile(context, "xiaoli.jpg")
        if (!isMockMode && cached.exists()) {
            // Warm VM cache via repository (also returns existing file).
            viewModel.fetchCalendar(forceRefresh = false)
        } else {
            viewModel.fetchCalendar(forceRefresh = true)
        }
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && isMockMode) {
            viewModel.fetchCalendar(forceRefresh = true)
        }
    }

    AhuScreenBox(
        modifier = Modifier.background(Color.Black),
    ) {
        if (calendarFile != null) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(calendarFile)
                    .crossfade(true)
                    .build(),
                contentDescription = "School Calendar",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset += pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        scope.launch(Dispatchers.IO) {
                            FileUtils.saveImageToGallery(context, calendarFile!!)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.saved_to_gallery),
                                    Toast.LENGTH_SHORT
                                ).show()
                                navController.popBackStack()
                            }
                        }
                    }
                }) {
                    Text(stringResource(id = R.string.save), color = Color.White)
                }
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(stringResource(id = R.string.exit), color = Color.White)
                }
            }
        }

        if (isLoading) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = if (progress > 0f) {
                        stringResource(id = R.string.downloading_percent, (progress * 100).toInt())
                    } else {
                        stringResource(id = R.string.fetching_calendar)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}
