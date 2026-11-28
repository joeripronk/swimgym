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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onLoginSuccess: (Map<String, String>) -> Unit,
    baseUrl: String = "https://swimgym.virtuagym.com"
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasNavigated by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val cookieManager = CookieManager.getInstance()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webView = this
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoading = false
                        if (url != null && url.startsWith(baseUrl)) {
                            errorMessage = null
                        }

                        val cookies = cookieManager.getCookie(url) ?: ""
                        val cookieMap = parseCookiesFromString(cookies)
                        val userId = cookieMap["virtuagym_u"]?.toLongOrNull() ?: 0
                        if (userId > 1 && !hasNavigated) {
                            hasNavigated = true
                            onLoginSuccess(cookieMap)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        if (errorCode != -1 && (failingUrl == null || failingUrl.startsWith(baseUrl))) {
                            isLoading = false
                            errorMessage = "Error loading page: $description"
                        }
                    }
                }

                // The WebView shares the system CookieManager with any previous
                // login. Stale cookies here would make onPageFinish immediately
                // "succeed" with the expired session and loop back to Schedule.
                cookieManager.getCookie(baseUrl)?.split(";")?.forEach { cookie ->
                    val name = cookie.trim().substringBefore("=")
                    if (name.isNotBlank()) cookieManager.setCookie(baseUrl, "$name=; Max-Age=0")
                }
                cookieManager.flush()
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
                Button(onClick = {
                    errorMessage = null
                    isLoading = true
                    webView?.loadUrl(baseUrl)
                }) {
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
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                pair.substring(0, idx) to pair.substring(idx + 1)
            } else {
                "" to ""
            }
        }
        .filter { it.first.isNotBlank() }
        .toMap()
}
