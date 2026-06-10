package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NovaBrowserTab(
    userName: String,
    speakTts: (String) -> Unit,
    onNavigateToChat: (String) -> Unit, // Direct route helper to chat tab with prompt injected
    initialUrl: String = "https://www.google.com",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var urlInputText by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    var isLoadingWebPage by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0) }
    var webPageTitle by remember { mutableStateOf("Nova Core Browser") }
    
    var canGoBackWeb by remember { mutableStateOf(false) }
    var canGoForwardWeb by remember { mutableStateOf(false) }

    // Floating Scrape Inspection Pane State
    var scrapedTextContent by remember { mutableStateOf<String?>(null) }
    var showScrapeDialog by remember { mutableStateOf(false) }
    var isScrapingPage by remember { mutableStateOf(false) }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotEmpty() && initialUrl != currentUrl) {
            currentUrl = initialUrl
            urlInputText = initialUrl
            webViewInstance?.loadUrl(initialUrl)
        }
    }

    // Handle Android device back button press to traverse WebView back stack
    BackHandler(enabled = canGoBackWeb) {
        webViewInstance?.let { web ->
            if (web.canGoBack()) {
                web.goBack()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(bottom = 80.dp) // Leave clean padding for floating bottom bar
    ) {
        // --- Web Navigation & Search Panel ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSlate)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CyberCyan, CircleShape)
                    )
                    Text(
                        text = "NOVA CORE BROWSER PORT",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                
                // Loading & Active Channel Status Details
                Text(
                    text = if (isLoadingWebPage) "LOADING $loadingProgress%" else "GATEWAY SECURED",
                    color = if (isLoadingWebPage) NeonAmber else SageGreen,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // URL Entry Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = urlInputText,
                    onValueChange = { urlInputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = PureWhite, 
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("browser_url_input"),
                    singleLine = true,
                    placeholder = { 
                        Text(
                            "Type URL or search term...", 
                            color = CharcoalMuted, 
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = SpaceBlack,
                        unfocusedContainerColor = SpaceBlack,
                        focusedIndicatorColor = CyberCyan,
                        unfocusedIndicatorColor = BorderSlate.copy(alpha = 0.3f)
                    ),
                    trailingIcon = {
                        if (urlInputText.isNotEmpty()) {
                            IconButton(onClick = { urlInputText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Input", tint = CharcoalMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // Navigation Go Button
                Button(
                    onClick = {
                        var target = urlInputText.trim()
                        if (target.isNotEmpty()) {
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                if (target.contains(".") && !target.contains(" ")) {
                                    target = "https://$target"
                                } else {
                                    // Treat as search query
                                    target = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
                                }
                            }
                            currentUrl = target
                            urlInputText = target
                            webViewInstance?.loadUrl(target)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = SpaceBlack),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp).testTag("browser_go_button")
                ) {
                    Text("GO", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Quick Portal Access Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val portals = listOf(
                    "Google" to "https://www.google.com",
                    "GitHub" to "https://github.com",
                    "Reddit" to "https://www.reddit.com",
                    "V2EX" to "https://www.v2ex.com"
                )
                portals.forEach { (name, url) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .border(0.8.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable {
                                currentUrl = url
                                urlInputText = url
                                webViewInstance?.loadUrl(url)
                            }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            color = CharcoalMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Web Control Row (Back, Forward, Refresh, Stop, Home, Scrape)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = canGoBackWeb,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back Page",
                            tint = if (canGoBackWeb) CyberCyan else CharcoalMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = canGoForwardWeb,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go Forward Page",
                            tint = if (canGoForwardWeb) CyberCyan else CharcoalMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Page",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentUrl = "https://www.google.com"
                            urlInputText = "https://www.google.com"
                            webViewInstance?.loadUrl("https://www.google.com")
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home Gateway",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // AI Web Scraper trigger
                Button(
                    onClick = {
                        isScrapingPage = true
                        scrapedTextContent = null
                        webViewInstance?.let { web ->
                            // Execute javascript to extract the text content of the page safely
                            web.evaluateJavascript(
                                "(function() { " +
                                "  var text = document.body.innerText;" +
                                "  return text.substring(0, 12000);" + // limit size
                                "})()"
                            ) { result ->
                                isScrapingPage = false
                                // evaluateJavascript returns string with quotes/escaped
                                val rawText = result ?: ""
                                // Clean up typical wrapping double quotes
                                val cleaned = if (rawText.startsWith("\"") && rawText.endsWith("\"") && rawText.length > 2) {
                                    val middle = rawText.substring(1, rawText.length - 1)
                                    // Simple unescape of javascript string unicode sequences
                                    middle.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
                                } else {
                                    rawText
                                }
                                
                                if (cleaned.trim().isNotEmpty() && cleaned != "null") {
                                    scrapedTextContent = cleaned.trim()
                                    showScrapeDialog = true
                                    speakTts("Extraction completed. I retrieved active screen information from $webPageTitle.")
                                } else {
                                    scrapedTextContent = "Warning: Empty text retrieved. Make sure the webpage has fully loaded or allows Javascript interactions."
                                    showScrapeDialog = true
                                    speakTts("The page returned empty text context.")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceBlack, contentColor = CyberCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Intelligence", tint = CyberCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isScrapingPage) "SCRAPING..." else "SCRAPE PAGE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- Loading Progress Bar ---
        if (isLoadingWebPage) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = CyberCyan,
                trackColor = Color.Transparent,
            )
        } else {
            HorizontalDivider(
                thickness = 1.dp,
                color = BorderSlate.copy(alpha = 0.15f)
            )
        }

        // --- Active WebView Viewport ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SpaceBlack)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag("viewport_webview"),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 NovaAssistant"
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoadingWebPage = true
                                url?.let { 
                                    currentUrl = it
                                    urlInputText = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoadingWebPage = false
                                url?.let { 
                                    currentUrl = it
                                    urlInputText = it
                                }
                                view?.title?.let { t ->
                                    webPageTitle = t
                                }
                                canGoBackWeb = view?.canGoBack() == true
                                canGoForwardWeb = view?.canGoForward() == true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { webPageTitle = it }
                            }
                        }

                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    // Make sure webView holds the active webViewInstance as updated state
                    webViewInstance = webView
                }
            )
        }
    }

    // --- Scrape & Analyze Sheet Dialog Over-the-hood ---
    if (showScrapeDialog) {
        AlertDialog(
            onDismissRequest = { showScrapeDialog = false },
            containerColor = SpaceBlack,
            modifier = Modifier.border(1.2.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(CyberCyan, CircleShape))
                    Text(
                        text = "INTELLIGENCE EXTRACTOR FEED",
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    Text(
                        text = "Scraped from: $webPageTitle",
                        color = TechTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    HorizontalDivider(thickness = 0.8.dp, color = BorderSlate.copy(alpha = 0.2f))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(CyberSlate, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = scrapedTextContent ?: "Analyzing extracted text lines...",
                            color = PureWhite,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "Feed this visible webpage context directly into Nova's dialogue engine and ask questions about the page data.",
                        color = CharcoalMuted,
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalPrompt = "summarize the news or article from this website context and explain key takeaways:\n\n$scrapedTextContent"
                        showScrapeDialog = false
                        onNavigateToChat(finalPrompt)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = SpaceBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ASK NOVA", fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScrapeDialog = false }) {
                    Text("CLOSE", color = CharcoalMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}
