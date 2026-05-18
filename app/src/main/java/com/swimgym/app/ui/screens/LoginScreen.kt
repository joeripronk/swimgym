package com.swimgym.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swimgym.app.data.api.WebScraper

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    webScraper: WebScraper,
    baseUrl: String = "https://swimgym.virtuagym.com"
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasNavigated by remember { mutableStateOf(false) }
    var cookiesToSave by remember { mutableStateOf<Map<String, String>?>(null) }
    val cookieManager = CookieManager.getInstance()

    // Save cookies to cache when they change
    LaunchedEffect(cookiesToSave) {
        cookiesToSave?.let { cookies ->
            // Update the global companion object directly
            //WebScraper.cookies = cookies
            // Also save to cache via the webScraper instance
            webScraper.saveCookiesToCache(cookies)
        }
    }

    // Save User-Agent to cache when it changes

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoading = false

                        val cookies = cookieManager.getCookie(url) ?: ""
                        if (cookies.contains(regex = Regex("virtuagym_u=[0-9][0-9]+")) && !hasNavigated) {
                            val cookieMap = parseCookiesFromString(cookies)
                            cookiesToSave = cookieMap
                            
                            // Save User-Agent from WebView
                            //userAgentToSave = settings.userAgentString
                            
                            hasNavigated = true
                            onLoginSuccess()
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        loadUrl(baseUrl)
                        if (errorCode != -1) {
                            errorMessage = "Error loading page: $description"
                        }
                    }
                }

                loadUrl(baseUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    errorMessage?.let { error ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { /* Reload handled by WebView */ }) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun parseCookiesFromString(cookieString: String): Map<String, String> {
    return cookieString.split("; ")
        .filter { it.contains("=") }
        .map { pair ->
            val parts = pair.split("=")
            if (parts.size >= 2) {
                parts[0] to parts[1]
            } else {
                "" to ""
            }
        }
        .filter { it.first.isNotBlank() }
        .toMap()
}
