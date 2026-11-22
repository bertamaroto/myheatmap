package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapView(
    payloadJson: String,
    isDarkTiles: Boolean,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(
                    AndroidColor.parseColor(if (isDarkTiles) "#0B0F14" else "#F4F7FA")
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                        view?.evaluateJavascript("window.updateMapData($payloadJson);", null)
                    }
                }
                loadUrl("file:///android_asset/leaflet_map.html")
                webViewRef = this
            }
        },
        update = { view ->
            webViewRef = view
        }
    )

    LaunchedEffect(payloadJson, pageLoaded) {
        val view = webViewRef
        if (pageLoaded && view != null) {
            view.evaluateJavascript("window.updateMapData($payloadJson);", null)
        }
    }
}
