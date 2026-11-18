package com.evorez.ezdmanager.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.model.ShizukuStatus
import com.evorez.ezdmanager.model.WorkMode
import com.evorez.ezdmanager.ui.components.EzdAmbientBackground
import com.evorez.ezdmanager.ui.components.GlassButton
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(
    state: EzdUiState,
    onRequestRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshPrivileges: () -> Unit,
    onCompleteOnboarding: (userName: String, tagline: String, photoUri: Uri?, workMode: WorkMode) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val context = LocalContext.current

    var userName by remember(state.settings.userName) {
        mutableStateOf(
            if (state.settings.userName == "Evorez User") "" else state.settings.userName
        )
    }
    var tagline by remember(state.settings.userTagline) {
        mutableStateOf(
            if (state.settings.userTagline == "Device Tuner") "" else state.settings.userTagline
        )
    }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedWorkMode by remember(state.settings.workMode) { mutableStateOf(state.settings.workMode) }

    val photoPreview by produceState<ImageBitmap?>(initialValue = null, key1 = selectedPhotoUri) {
        value = selectedPhotoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedPhotoUri = uri
    }

    EzdAmbientBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(6.dp))

            Image(
                painter = painterResource(R.drawable.evorez_logo_circular),
                contentDescription = "EZD Manager Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.dp, visuals.accent, CircleShape)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome to EZD Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Set up your profile & choose how EZD manages your device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Profile Identity Card (Photo + Name + Tagline)
            GlassCard(cornerRadius = 26.dp, highlightBorder = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("onboarding_photo_picker"),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        if (photoPreview != null) {
                            Image(
                                bitmap = photoPreview!!,
                                contentDescription = "Selected Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, visuals.accent, CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.evorez_avatar),
                                contentDescription = "Pick Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, visuals.accent.copy(alpha = 0.65f), CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(visuals.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Change Photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = if (selectedPhotoUri == null) "Tap avatar to upload photo (optional)" else "Photo ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Your Name") },
                        placeholder = { Text("Evorez User") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_username_input")
                    )

                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Bio / Device Tagline (optional)") },
                        placeholder = { Text("Device Tuner") },
                        leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Privilege & Work Mode Setup Card (Root / Shizuku / Basic)
            GlassCard(cornerRadius = 26.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privilege & Work Mode", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onRefreshPrivileges) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh status")
                    }
                }

                // Live Root & Shizuku badges + Request actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        label = "Root: ${state.rootStatus.name}",
                        accent = if (state.rootStatus == RootStatus.GRANTED) visuals.successColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(
                        label = "Shizuku: ${state.shizukuStatus.label}",
                        accent = if (state.shizukuStatus == ShizukuStatus.GRANTED) visuals.successColor else visuals.accent,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRequestRoot,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("onboarding_grant_root")
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.rootStatus == RootStatus.GRANTED) "Root Active" else "Grant Root")
                    }
                    OutlinedButton(
                        onClick = onRequestShizuku,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("onboarding_grant_shizuku")
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.shizukuStatus == ShizukuStatus.GRANTED) "Shizuku OK" else "Shizuku")
                    }
                }

                Spacer(Modifier.height(10.dp))
                WorkMode.entries.forEach { mode ->
                    val chosen = selectedWorkMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedWorkMode = mode },
                        shape = RoundedCornerShape(16.dp),
                        color = if (chosen) visuals.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, if (chosen) visuals.accent else visuals.glassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (chosen) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = visuals.accent)
                            }
                        }
                    }
                }
            }

            if (state.statusBanner.isNotBlank()) {
                Text(
                    text = state.statusBanner,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = visuals.accent
                )
            }

            GlassButton(
                text = "Enter EZD Manager",
                icon = Icons.Rounded.ArrowForward,
                onClick = {
                    onCompleteOnboarding(
                        userName.ifBlank { "Evorez User" },
                        tagline.ifBlank { "Device Tuner" },
                        selectedPhotoUri,
                        selectedWorkMode
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_continue_btn")
            )

            TextButton(
                onClick = {
                    onCompleteOnboarding("Evorez User", "Device Tuner", null, WorkMode.AUTO)
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Skip & Use Default", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
