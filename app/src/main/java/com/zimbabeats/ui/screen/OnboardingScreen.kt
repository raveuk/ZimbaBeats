package com.zimbabeats.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimbabeats.ui.util.WindowSizeUtil
import com.zimbabeats.ui.viewmodel.OnboardingViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
            },
            label = "onboarding_step"
        ) { step ->
            when (step) {
                OnboardingViewModel.STEP_WELCOME -> WelcomeStep(
                    onGetStarted = { viewModel.nextStep() }
                )
                OnboardingViewModel.STEP_FAMILY_CODE -> FamilyCodeStep(
                    isLinking = uiState.isLinking,
                    isLinked = uiState.isLinked,
                    linkingError = uiState.linkingError,
                    onCodeEntered = { code -> viewModel.linkWithFamily(code) },
                    onContinue = { viewModel.completeOnboarding(onComplete) },
                    onClearError = { viewModel.clearError() }
                )
            }
        }

        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }

        if (uiState.isCompleting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    val maxContentWidth = WindowSizeUtil.getMaxContentWidth()
    val horizontalPadding = WindowSizeUtil.getHorizontalPadding()
    val largeIconSize = WindowSizeUtil.getLargeIconSize()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified)
                        Modifier.widthIn(max = maxContentWidth)
                    else Modifier.fillMaxWidth()
                )
                .padding(horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(largeIconSize),
                tint = MaterialTheme.colorScheme.primary
            )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to ZimbaBeats!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your safe place to watch videos and listen to music.\n\nEnjoy kid-friendly content curated just for you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
        }
    }
}

@Composable
private fun FamilyCodeStep(
    isLinking: Boolean,
    isLinked: Boolean,
    linkingError: String?,
    onCodeEntered: (String) -> Unit,
    onContinue: () -> Unit,
    onClearError: () -> Unit
) {
    var familyCode by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val maxContentWidth = WindowSizeUtil.getMaxContentWidth()
    val horizontalPadding = WindowSizeUtil.getHorizontalPadding()
    val largeIconSize = WindowSizeUtil.getLargeIconSize()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified)
                        Modifier.widthIn(max = maxContentWidth)
                    else Modifier.fillMaxWidth()
                )
                .padding(horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                if (isLinked) Icons.Default.FamilyRestroom else Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(largeIconSize),
                tint = if (isLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isLinked) "Connected to Family!" else "Link to Family",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isLinked)
                "Your device is now linked. Parental controls will sync automatically."
            else
                "Enter the 6-digit code from ZimbaBeats Family app to enable parental controls.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isLinked) {
            OutlinedTextField(
                value = familyCode,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    familyCode = filtered
                    onClearError()
                },
                label = { Text("Family Code") },
                placeholder = { Text("ABC123") },
                singleLine = true,
                enabled = !isLinking,
                isError = linkingError != null,
                supportingText = linkingError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (familyCode.length == 6) {
                            onCodeEntered(familyCode)
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onCodeEntered(familyCode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = familyCode.length == 6 && !isLinking
            ) {
                if (isLinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Link to Family", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Family app pairing is required to use ZimbaBeats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        } else {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        }
    }
}
