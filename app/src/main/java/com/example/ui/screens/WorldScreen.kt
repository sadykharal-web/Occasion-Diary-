package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Collections
import java.util.WeakHashMap

const val WORLD_WEBSITE_URL = "https://raizan.gamer.free"

private val disposedWebViews: MutableSet<WebView> =
    Collections.newSetFromMap(WeakHashMap())

private fun disposeWebViewSafely(
    webView: WebView,
    detachFromParent: Boolean = true
) {
    if (!disposedWebViews.add(webView)) return
    runCatching {
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.onPause()
        webView.clearHistory()
        if (detachFromParent) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        webView.removeAllViews()
        webView.destroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldScreen(
    isFullScreen: Boolean = false,
    onToggleFullScreen: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var crashRecoveryAttempts by remember { mutableIntStateOf(0) }
    var rendererCrashed by remember { mutableStateOf(false) }
    var pendingRecoveryOnResume by remember { mutableStateOf(false) }
    var pendingLoadUrl by remember { mutableStateOf(WORLD_WEBSITE_URL) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(WORLD_WEBSITE_URL) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var triedHttpFallback by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                    if (pendingRecoveryOnResume) {
                        pendingRecoveryOnResume = false
                        rendererCrashed = false
                        hasError = false
                        errorMessage = null
                        pendingLoadUrl = currentUrl.ifBlank { WORLD_WEBSITE_URL }
                        webViewGeneration++
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    webViewRef?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isFullScreen || (canGoBack && !rendererCrashed)) {
        if (isFullScreen) {
            onToggleFullScreen(false)
        } else {
            webViewRef?.goBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("world_screen")
    ) {
        if (!isFullScreen) {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "World",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack && !rendererCrashed,
                            modifier = Modifier.testTag("world_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = canGoForward && !rendererCrashed,
                            modifier = Modifier.testTag("world_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Forward"
                            )
                        }
                        IconButton(
                            onClick = {
                                hasError = false
                                errorMessage = null
                                triedHttpFallback = false
                                currentUrl = WORLD_WEBSITE_URL
                                if (rendererCrashed || webViewRef == null) {
                                    rendererCrashed = false
                                    crashRecoveryAttempts = 0
                                    pendingLoadUrl = WORLD_WEBSITE_URL
                                    webViewGeneration++
                                } else {
                                    webViewRef?.loadUrl(WORLD_WEBSITE_URL)
                                }
                            },
                            modifier = Modifier.testTag("world_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = "Website Home",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                hasError = false
                                errorMessage = null
                                if (rendererCrashed || webViewRef == null) {
                                    rendererCrashed = false
                                    crashRecoveryAttempts = 0
                                    triedHttpFallback = false
                                    pendingLoadUrl = currentUrl.ifBlank { WORLD_WEBSITE_URL }
                                    webViewGeneration++
                                } else {
                                    webViewRef?.reload()
                                }
                            },
                            modifier = Modifier.testTag("world_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                        IconButton(
                            onClick = { onToggleFullScreen(true) },
                            modifier = Modifier.testTag("world_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Full screen"
                            )
                        }
                    }
                }
            }
        }

        if (progress in 1..99 && !rendererCrashed) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("world_loading_progress")
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!rendererCrashed) {
                key(webViewGeneration) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("world_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    setSupportZoom(true)
                                    mediaPlaybackRequiresUserGesture = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    allowFileAccess = false
                                    allowContentAccess = true
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        offscreenPreRaster = false
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        safeBrowsingEnabled = false
                                    }
                                }
                                runCatching {
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        hasError = false
                                        errorMessage = null
                                        if (!url.isNullOrBlank()) currentUrl = url
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        progress = 100
                                        if (!url.isNullOrBlank()) currentUrl = url
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url?.toString().orEmpty()
                                        if (target.startsWith("http://") || target.startsWith("https://")) {
                                            return false
                                        }
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, request?.url))
                                        }
                                        return true
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            val failingUrl = request.url?.toString().orEmpty()
                                            if (!triedHttpFallback && failingUrl.startsWith("https://")) {
                                                triedHttpFallback = true
                                                val fallbackUrl = failingUrl.replaceFirst("https://", "http://")
                                                view?.post { view.loadUrl(fallbackUrl) }
                                                return
                                            }
                                            hasError = true
                                            errorMessage = error?.description?.toString()
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "Unable to load raizan.gamer.free"
                                        }
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        handler?.cancel()
                                        val failingUrl = error?.url.orEmpty()
                                        if (!triedHttpFallback && failingUrl.startsWith("https://")) {
                                            triedHttpFallback = true
                                            val fallbackUrl = failingUrl.replaceFirst("https://", "http://")
                                            view?.post { view.loadUrl(fallbackUrl) }
                                            return
                                        }
                                        hasError = true
                                        errorMessage = "Unable to connect securely to raizan.gamer.free"
                                    }

                                    @RequiresApi(Build.VERSION_CODES.O)
                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: RenderProcessGoneDetail?
                                    ): Boolean {
                                        if (webViewRef === view) {
                                            webViewRef = null
                                        }
                                        view?.let { disposeWebViewSafely(it, detachFromParent = true) }
                                        mainHandler.post {
                                            progress = 100
                                            canGoBack = false
                                            canGoForward = false
                                            val isForeground = lifecycleOwner.lifecycle.currentState
                                                .isAtLeast(Lifecycle.State.RESUMED)
                                            if (!isForeground) {
                                                pendingRecoveryOnResume = true
                                                rendererCrashed = true
                                            } else if (detail?.didCrash() == false && crashRecoveryAttempts < 1) {
                                                crashRecoveryAttempts++
                                                hasError = false
                                                errorMessage = null
                                                pendingLoadUrl = currentUrl.ifBlank { WORLD_WEBSITE_URL }
                                                webViewGeneration++
                                            } else {
                                                rendererCrashed = true
                                                hasError = true
                                                errorMessage = "The webpage renderer stopped responding on this device. You can retry or open raizan.gamer.free in your browser."
                                            }
                                        }
                                        return true
                                    }
                                }
                                webViewRef = this
                                val targetUrl = pendingLoadUrl
                                post {
                                    if (webViewRef === this && !disposedWebViews.contains(this)) {
                                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                            onResume()
                                        }
                                        loadUrl(targetUrl)
                                    }
                                }
                            }
                        },
                        onRelease = { view ->
                            if (webViewRef === view) {
                                webViewRef = null
                            }
                            disposeWebViewSafely(view, detachFromParent = true)
                        }
                    )
                }
            }

            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Could not load website",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = errorMessage ?: "Check your internet connection and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        hasError = false
                                        errorMessage = null
                                        triedHttpFallback = false
                                        if (rendererCrashed || webViewRef == null) {
                                            rendererCrashed = false
                                            crashRecoveryAttempts = 0
                                            pendingLoadUrl = WORLD_WEBSITE_URL
                                            webViewGeneration++
                                        } else {
                                            webViewRef?.loadUrl(WORLD_WEBSITE_URL)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("world_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(WORLD_WEBSITE_URL))
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browser")
                                }
                            }
                        }
                    }
                }
            }
            if (isFullScreen) {
                FilledTonalIconButton(
                    onClick = { onToggleFullScreen(false) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .testTag("world_exit_fullscreen_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FullscreenExit,
                        contentDescription = "Exit full screen"
                    )
                }
            }
        }
    }
}
