package com.zimbabeats.ui.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zimbabeats.admin.DeviceAdminManager
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.CloudParentalSettings
import com.zimbabeats.cloud.PairingStatus
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToPairing: () -> Unit = {},
    cloudPairingClient: CloudPairingClient = koinInject(),
    deviceAdminManager: DeviceAdminManager = koinInject()
) {
    val pairingStatus by cloudPairingClient.pairingStatus.collectAsState()
    val cloudSettings by cloudPairingClient.cloudSettings.collectAsState()
    val isAdminActive by deviceAdminManager.isAdminActive.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Start settings sync if paired
    LaunchedEffect(pairingStatus) {
        if (pairingStatus is PairingStatus.Paired) {
            cloudPairingClient.startSettingsSync()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parental Controls") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (val status = pairingStatus) {
                is PairingStatus.Paired -> {
                    // Connected state
                    ConnectedHeader(childName = status.childName)

                    // Current restrictions from cloud
                    cloudSettings?.let { settings ->
                        if (settings.isEnabled) {
                            ActiveRestrictionsCard(settings = settings)
                        } else {
                            InactiveRestrictionsCard()
                        }
                    } ?: LoadingRestrictionsCard()

                    // Manage connection
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToPairing
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Manage Connection",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is PairingStatus.Pairing -> {
                    // Connecting state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                is PairingStatus.Unpaired -> {
                    // Not connected state
                    NotConnectedContent(
                        onConnectClick = onNavigateToPairing,
                        isAdminActive = isAdminActive,
                        onEnableAdmin = { activity?.let { deviceAdminManager.requestAdminActivation(it) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ConnectedHeader(childName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Managed by parent for $childName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ActiveRestrictionsCard(settings: CloudParentalSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Active Restrictions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Age Rating
            RestrictionItem(
                icon = Icons.Default.ChildFriendly,
                title = "Content Rating",
                value = formatAgeRating(settings.ageRating),
                isActive = true
            )

            HorizontalDivider()

            // Screen Time
            RestrictionItem(
                icon = Icons.Default.Timer,
                title = "Daily Screen Time",
                value = if (settings.screenTimeLimitMinutes > 0) {
                    "${settings.screenTimeLimitMinutes} minutes"
                } else {
                    "No limit"
                },
                isActive = settings.screenTimeLimitMinutes > 0
            )

            HorizontalDivider()

            // Bedtime
            RestrictionItem(
                icon = Icons.Default.Bedtime,
                title = "Bedtime",
                value = if (settings.bedtimeEnabled && settings.bedtimeStart != null) {
                    "${settings.bedtimeStart} - ${settings.bedtimeEnd}"
                } else {
                    "Not set"
                },
                isActive = settings.bedtimeEnabled
            )
        }
    }
}

@Composable
private fun RestrictionItem(
    icon: ImageVector,
    title: String,
    value: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isActive) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("Active", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun InactiveRestrictionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Active Restrictions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your parent hasn't enabled any restrictions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingRestrictionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading settings...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceProtectionCard(
    isAdminActive: Boolean,
    onEnableAdmin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAdminActive)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isAdminActive) Icons.Default.Security else Icons.Default.Shield,
                contentDescription = null,
                tint = if (isAdminActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Uninstall Protection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isAdminActive) "Protected" else "Not enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isAdminActive) {
                FilledTonalButton(
                    onClick = onEnableAdmin,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Enable")
                }
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun NotConnectedContent(
    onConnectClick: () -> Unit,
    isAdminActive: Boolean,
    onEnableAdmin: () -> Unit
) {
    // Hero section
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connect to Parent",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Link this device to your parent's ZimbaBeats Family app for safe viewing with screen time limits and content filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enter Pairing Code", style = MaterialTheme.typography.titleMedium)
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // How it works
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "How it works",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            HowItWorksStep(
                number = 1,
                text = "Parent opens ZimbaBeats Family app"
            )
            HowItWorksStep(
                number = 2,
                text = "Parent taps \"Add Child's Device\""
            )
            HowItWorksStep(
                number = 3,
                text = "Enter the 6-digit code shown"
            )
            HowItWorksStep(
                number = 4,
                text = "Done! Settings sync automatically"
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Device protection (collapsed version for unpaired state)
    DeviceProtectionCard(
        isAdminActive = isAdminActive,
        onEnableAdmin = onEnableAdmin
    )
}

@Composable
private fun HowItWorksStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatAgeRating(rating: String): String {
    return when (rating.uppercase()) {
        "ALL" -> "All Ages"
        "FIVE_PLUS" -> "Kids Under 5"
        "EIGHT_PLUS" -> "Kids Under 8"
        // Legacy mappings for backward compatibility
        "TEN_PLUS" -> "Kids Under 8"
        "TWELVE_PLUS" -> "Kids Under 13"
        "THIRTEEN_PLUS" -> "Kids Under 13"
        "FOURTEEN_PLUS" -> "Kids Under 13"
        "SIXTEEN_PLUS" -> "Kids Under 16"
        else -> rating
    }
}
