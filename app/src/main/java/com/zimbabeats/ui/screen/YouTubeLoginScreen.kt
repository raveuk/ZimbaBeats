package com.zimbabeats.ui.screen

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zimbabeats.data.AppPreferences
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "YouTubeLoginScreen"
private const val YOUTUBE_MUSIC_LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
private const val YOUTUBE_MUSIC_MAIN_URL = "https://music.youtube.com"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    appPreferences: AppPreferences = koinInject()
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to YouTube Music") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Clear cookies first
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportZoom(true)
                        settings.databaseEnabled = true

                        // Set user agent to desktop Chrome for better login experience
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                currentUrl = url ?: ""
                                Log.d(TAG, "Page finished: $url")

                                // Check if we're on YouTube Music main page (login successful)
                                if (url != null && (url.startsWith(YOUTUBE_MUSIC_MAIN_URL) || url.contains("music.youtube.com"))) {
                                    Log.d(TAG, "Login successful, extracting cookies...")

                                    // Get cookies
                                    val cookie = CookieManager.getInstance().getCookie(url)
                                    if (cookie != null && cookie.isNotEmpty()) {
                                        Log.d(TAG, "Cookies extracted: ${cookie.take(100)}...")

                                        // Check if we have the required authentication cookies
                                        val hasSapisid = cookie.contains("SAPISID") ||
                                                         cookie.contains("__Secure-3PAPISID")
                                        val hasSid = cookie.contains("SID") ||
                                                     cookie.contains("__Secure-1PSID")

                                        if (hasSapisid || hasSid) {
                                            Log.d(TAG, "Authentication cookies found!")
                                            scope.launch {
                                                // Save cookie
                                                appPreferences.saveYouTubeAccount(
                                                    cookie = cookie,
                                                    name = "",  // Could extract from page if needed
                                                    email = ""
                                                )
                                                onLoginSuccess()
                                            }
                                        } else {
                                            Log.d(TAG, "No authentication cookies yet, waiting...")
                                        }
                                    }
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                                isLoading = newProgress < 100
                            }
                        }

                        // Load YouTube Music login URL
                        loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading indicator
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Loading... $loadingProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
