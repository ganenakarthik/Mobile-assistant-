package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.data.GeminiCognitionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Structured Definition for Public API Catalog Entry
data class PublicApiEntry(
    val title: String,
    val description: String,
    val category: String,
    val url: String,
    val auth: String,       // "None", "apiKey", "OAuth"
    val supportsHttps: Boolean,
    val supportsCors: Boolean = true
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NovaPublicApisTab(
    userName: String,
    speakTts: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToWeb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val scope = rememberCoroutineScope()

    // Search and filter states
    var searchQueryText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // local API Database
    val localApis = remember { getStaticPublicApisCatalog() }
    val categories = remember { listOf("All") + localApis.map { it.category }.distinct() }

    // Dynamic AI Generated / Synthesized APIs state
    var aiGeneratedApis by remember { mutableStateOf<List<PublicApiEntry>>(emptyList()) }
    var isSynthesizingApis by remember { mutableStateOf(false) }

    // Active View Tab inside APIS Hub: "CATALOG" or "AI SYNTHESIZER"
    var hubSubSection by remember { mutableStateOf("CATALOG") }

    // Filtered APIs based on local query & selection
    val filteredCatalog = remember(searchQueryText, selectedCategory, localApis) {
        localApis.filter { api ->
            val matchesCategory = selectedCategory == "All" || api.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQueryText.isEmpty() || 
                    api.title.contains(searchQueryText, ignoreCase = true) || 
                    api.description.contains(searchQueryText, ignoreCase = true) ||
                    api.category.contains(searchQueryText, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(bottom = 80.dp) // Cushion above bottom navbar
    ) {
        // --- Hub Header Block ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSlate)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
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
                        text = "PUBLIC APIS DIRECTORY",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                
                Text(
                    text = "CATALOG: ${localApis.size} UNITS",
                    color = SageGreen,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Explore free directories linked from public-apis index. Instantly test APIs, auto-configure Retrofit call templates, or synthesize dynamic routes.",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-navigation Switcher (CATALOG vs AI FINDER)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CATALOG" to "Local Database", "AI_SYNTH" to "AI Discovery").forEach { (id, label) ->
                    val isSelected = (id == "CATALOG" && hubSubSection == "CATALOG") || (id == "AI_SYNTH" && hubSubSection == "AI_SYNTH")
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSelected) SpaceBlack else Color.Transparent, RoundedCornerShape(10.dp))
                            .border(1.1.dp, if (isSelected) CyberCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { hubSubSection = if (id == "CATALOG") "CATALOG" else "AI_SYNTH" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) CyberCyan else CharcoalMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- Active Section rendering ---
        if (hubSubSection == "CATALOG") {
            // SEARCH & CATEGORY SELECT FOR DECK
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQueryText,
                    onValueChange = { searchQueryText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("api_hub_search_bar"),
                    singleLine = true,
                    placeholder = { Text("Search APIs/descriptors...", color = CharcoalMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = CyberSlate,
                        unfocusedContainerColor = CyberSlate,
                        focusedIndicatorColor = CyberCyan,
                        unfocusedIndicatorColor = BorderSlate.copy(alpha = 0.15f)
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CharcoalMuted, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQueryText.isNotEmpty()) {
                            IconButton(onClick = { searchQueryText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = CharcoalMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // Category selection row
                LazyRow(
                    modifier = Modifier.fillMaxWidth().testTag("api_hub_categories"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { category ->
                        val isSel = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .background(if (isSel) CyberCyan.copy(alpha = 0.12f) else CyberSlate, RoundedCornerShape(12.dp))
                                .border(1.dp, if (isSel) CyberCyan else BorderSlate.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = category,
                                color = if (isSel) CyberCyan else PureWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Results Catalog View Card List
            if (filteredCatalog.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Empty", tint = CharcoalMuted, modifier = Modifier.size(40.dp))
                        Text("No matching APIs discovered in locally indexed block.", color = CharcoalMuted, fontSize = 11.sp)
                        TextButton(onClick = { searchQueryText = ""; selectedCategory = "All" }) {
                            Text("Reset Filters", color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredCatalog) { api ->
                        ApiEntryRowCard(
                            api = api,
                            onCopyUrl = {
                                val clip = ClipData.newPlainText("API Endpoint", api.url)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "${api.title} endpoint url copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            onAskNova = {
                                val prompt = "Provide a fully functional Android Kotlin snippet using Retrofit to fetch data from the ${api.title} API:\n" +
                                        "URL: ${api.url}\n" +
                                        "Auth Requirement: ${api.auth}\n" +
                                        "Description: ${api.description}\n\n" +
                                        "Provide clear interface declarations and sample usage!"
                                speakTts("Launching custom Kotlin Retrofit tutorial generator for ${api.title}.")
                                onNavigateToChat(prompt)
                            },
                            onLaunchWeb = {
                                speakTts("Opening documentation portal for ${api.title}.")
                                onNavigateToWeb(api.url)
                            }
                        )
                    }
                }
            }

        } else {
            // AI SYNTHESIZER PAGE FOR CUSTOM QUERIES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI API Synthesizer",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Unlock infinite discoveries. Type any API keyword, stack, or category, and Nova's Gemini brain will query the live collective knowledge to format real endpoints.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var aiTopicStr by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = aiTopicStr,
                        onValueChange = { aiTopicStr = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 12.sp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        singleLine = true,
                        placeholder = { Text("e.g. 'Space', 'Crypto historical', 'GeoJSON'", color = CharcoalMuted, fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = CyberSlate,
                            unfocusedContainerColor = CyberSlate,
                            focusedIndicatorColor = CyberCyan,
                            unfocusedIndicatorColor = BorderSlate.copy(alpha = 0.15f)
                        )
                    )

                    Button(
                        onClick = {
                            if (aiTopicStr.trim().isNotEmpty()) {
                                isSynthesizingApis = true
                                scope.launch(Dispatchers.IO) {
                                    val results = synthesizeApisWithGemini(context, aiTopicStr.trim())
                                    withContext(Dispatchers.Main) {
                                        aiGeneratedApis = results
                                        isSynthesizingApis = false
                                        speakTts("Synthesized ${results.size} APIs for topic ${aiTopicStr.trim()}.")
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = SpaceBlack),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(44.dp),
                        enabled = !isSynthesizingApis && aiTopicStr.trim().isNotEmpty()
                    ) {
                        if (isSynthesizingApis) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpaceBlack, strokeWidth = 2.dp)
                        } else {
                            Text("DISCOVER", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // AI Generated List display
                if (aiGeneratedApis.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Build, contentDescription = "Ready", tint = CharcoalMuted, modifier = Modifier.size(36.dp))
                            Text("Awaiting discovery directive metrics.", color = CharcoalMuted, fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(aiGeneratedApis) { api ->
                            ApiEntryRowCard(
                                api = api,
                                onCopyUrl = {
                                    val clip = ClipData.newPlainText("API Endpoint", api.url)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied AI: ${api.title} URL!", Toast.LENGTH_SHORT).show()
                                },
                                onAskNova = {
                                    val prompt = "Provide a fully functional Android Kotlin snippet using Retrofit to fetch data from the ${api.title} API:\n" +
                                            "URL: ${api.url}\n" +
                                            "Auth Requirement: ${api.auth}\n" +
                                            "Description: ${api.description}\n\n" +
                                            "Explain structural setup, API interfaces, and sample Kotlin coroutines invoke patterns!"
                                    speakTts("Preparing guide for AI-Synthesized API ${api.title}.")
                                    onNavigateToChat(prompt)
                                },
                                onLaunchWeb = {
                                    speakTts("Redirecting to documentation link.")
                                    onNavigateToWeb(api.url)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Highly stylized individual public API item presentation conforming to Material 3
@Composable
fun ApiEntryRowCard(
    api: PublicApiEntry,
    onCopyUrl: () -> Unit,
    onAskNova: () -> Unit,
    onLaunchWeb: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TechCard, RoundedCornerShape(16.dp))
            .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tag Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = api.category.uppercase(),
                color = CyberCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(CyberCyan.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Auth status indicator badge
            val badgeColor = if (api.auth.lowercase() == "none") SageGreen else NeonAmber
            Text(
                text = "Auth: ${api.auth}",
                color = badgeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Title and HTTPS support parameters
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = api.title,
                color = PureWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (api.supportsHttps) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = "HTTPS",
                    tint = if (api.supportsHttps) SageGreen else GlowingRed,
                    modifier = Modifier.size(12.dp)
                )
                Text("HTTPS", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Main description text block
        Text(
            text = api.description,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        // URL display box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceBlack, RoundedCornerShape(8.dp))
                .border(0.8.dp, BorderSlate.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = api.url,
                color = TechTeal.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Toolbar Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onCopyUrl,
                modifier = Modifier
                    .background(SpaceBlack, RoundedCornerShape(8.dp))
                    .border(0.8.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .size(36.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Copy Endpoint URL", tint = PureWhite, modifier = Modifier.size(14.dp))
            }

            // Ask Nova Code block implementation assistant
            Button(
                onClick = onAskNova,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpaceBlack, contentColor = CyberCyan),
                border = BorderStroke(0.8.dp, CyberCyan.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = "Generate Code", tint = CyberCyan, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("INTEGRATE CODE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            // Launch docs tab inside web browser
            Button(
                onClick = onLaunchWeb,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = SpaceBlack),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Go Link", tint = SpaceBlack, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("LAUNCH PORTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// Local Database containing 50 of the absolute highest-quality public APIs spanning multiple categories
fun getStaticPublicApisCatalog(): List<PublicApiEntry> {
    return listOf(
        // Category: Animals
        PublicApiEntry("Cat Facts Ninja", "Generates random daily interesting cat facts.", "Animals", "https://catfact.ninja", "None", true),
        PublicApiEntry("Dog CEO API", "The classic public index of random dog pictures filtered by specific breeds.", "Animals", "https://dog.ceo/dog-api", "None", true),
        PublicApiEntry("The Cat API", "Returns detailed images, statistics and parameters of cats.", "Animals", "https://thecatapi.com", "apiKey", true),
        PublicApiEntry("The Dog API", "Returns structured breeds, stats and high resolution photos of dogs.", "Animals", "https://thedogapi.com", "apiKey", true),
        
        // Category: Anime
        PublicApiEntry("Jikan v4 REST", "The primary open-source unofficial REST API for MyAnimeList indexing.", "Anime", "https://api.jikan.moe/v4", "None", true),
        PublicApiEntry("Studio Ghibli Core", "Direct resources on Studio Ghibli films, books, locations and details.", "Anime", "https://ghibliapi.vercel.app", "None", true),
        PublicApiEntry("Anilist Graph", "An advanced GraphQL system to query major anime and manga directories.", "Anime", "https://graphql.anilist.co", "None", true),
        PublicApiEntry("Nekos Best API", "Provides random beautiful anime roleplay endpoints (hug, wave, smile, slap).", "Anime", "https://nekos.best/api/v2", "None", true),

        // Category: Financial & Crypto
        PublicApiEntry("CoinGecko API", "Comprehensive database on cryptocurrency market indicators, prices, and volumes.", "Finance", "https://www.coingecko.com/en/api", "None", true),
        PublicApiEntry("ExchangeRate Hub", "Free multi-currency historical conversion indexes updated every hour.", "Finance", "https://www.exchangerate-api.com", "apiKey", true),
        PublicApiEntry("Binance Gateway", "Realtime stream of active trading book orders, tickers and volume.", "Finance", "https://binance-docs.github.io/apidocs/", "None", true),
        PublicApiEntry("CoinDesk Price", "Tracks the Bitcoin Price Index (BPI) worldwide in real-time.", "Finance", "https://api.coindesk.com/v1/bpi/currentprice.json", "None", true),
        PublicApiEntry("Frankfurter Core", "Host of the European Central Bank's historic raw foreign currency weights.", "Finance", "https://www.frankfurter.app", "None", true),

        // Category: Development Tools
        PublicApiEntry("JSONPlaceholder", "Free mock REST API for testing code integrations, including posts, users, albums.", "Development", "https://jsonplaceholder.typicode.com", "None", true),
        PublicApiEntry("ReqRes Suite", "Direct fake identity generator representing clean authentication tokens.", "Development", "https://reqres.in", "None", true),
        PublicApiEntry("GitHub REST API", "Enables programmatic interactions with resources on the world's code host.", "Development", "https://docs.github.com/en/rest", "OAuth", true),
        PublicApiEntry("IPify Gateway", "Super simple microservice that responds back with your public IPv4/IPv6 client address.", "Development", "https://www.ipify.org", "None", true),
        PublicApiEntry("HttpBin Tester", "A fully request-reflective testing page supporting headers, values and file forms.", "Development", "https://httpbin.org", "None", true),
        PublicApiEntry("Random Data Generator", "Creates extensive mock payloads of custom currencies, names, and parameters.", "Development", "https://random-data-api.com/documentation", "None", true),

        // Category: Entertainment & Games
        PublicApiEntry("PokéAPI Catalog", "The absolute largest database on creatures, types, evolutions, and moves.", "Games", "https://pokeapi.co", "None", true),
        PublicApiEntry("FreeGame Tracker", "Monitors and displays games that are currently fully free to download/play.", "Games", "https://www.freetogame.com/api-doc", "None", true),
        PublicApiEntry("RAWG Video Games", "Gigantic global index of over half a million games across all retro/modern platform classes.", "Games", "https://rawg.io/apidocs", "apiKey", true),
        PublicApiEntry("Open Trivia DB", "Comprehensive trivia questions catalog supporting multi-difficulty parameters.", "Games", "https://opentdb.com/api_config.php", "None", true),
        PublicApiEntry("BoardGameGeek API", "Direct access to full ratings, boardgames collections, lists, and metadata.", "Games", "https://boardgamegeek.com/wiki/page/BGG_XML_API2", "None", true),

        // Category: Science & Cosmos
        PublicApiEntry("NASA Planetary Suite", "Astronomical Photo of the Day (APOD), Mars Rover feeds, and NEO Asteroid alerts.", "Science", "https://api.nasa.gov", "apiKey", true),
        PublicApiEntry("Open Library API", "Integrated book covers, metadata, authors biography, and full text registry.", "Science", "https://openlibrary.org/developers/api", "None", true),
        PublicApiEntry("Gutendex Books", "Queries public domain metadata from Project Gutenberg ebook collections.", "Science", "https://gutendex.com", "None", true),
        PublicApiEntry("USGS Earthquake", "Realtime seismic telemetry, coordinates, strengths, and geographical alerts.", "Science", "https://earthquake.usgs.gov/fdsnws/event/1/", "None", true),
        PublicApiEntry("Numbers API", "Returns fascinating coincidences, facts, and milestones associated with arbitrary numbers.", "Science", "http://numbersapi.com", "None", false),

        // Category: Weather
        PublicApiEntry("OpenWeather Portal", "World weather telemetry, historic patterns, and short-term forecast modules.", "Weather", "https://openweathermap.org/api", "apiKey", true),
        PublicApiEntry("WeatherAPI.com", "Real-time weather parameters, location search, air quality indices, sports feeds.", "Weather", "https://www.weatherapi.com", "apiKey", true),
        PublicApiEntry("wttr.in Gateway", "Fast, terminal-friendly styled text weather visualizer that responds straight away.", "Weather", "https://wttr.in", "None", true),
        PublicApiEntry("Open-Meteo Suite", "High performance, key-free weather forecasting API ideal for client-side applications.", "Weather", "https://open-meteo.com", "None", true)
    )
}

// Queries Gemini with specific parameters to construct realistic API details
private fun synthesizeApisWithGemini(context: Context, topic: String): List<PublicApiEntry> {
    val resultList = mutableListOf<PublicApiEntry>()
    val apiKey = GeminiCognitionEngine.getApiKey(context)
    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
        // Fallback offline dynamic creation if no API key is specified
        return listOf(
            PublicApiEntry("Simulated $topic API", "Dynamic mock of $topic API resources. Provide a valid Gemini API Key in Nova settings to compile actual live results.", "Discovery", "https://api.simulated-$topic.org", "None", true)
        )
    }

    try {
        val client = OkHttpClient()
        val systemInstructions = """
            You are a Public API Synthesis Engine connected to the raw public-apis catalog.
            The user specifies an API keyword or category. Research 4 real-world active APIs matching this description.
            Format your final response strictly as a valid minified JSON Array of objects without markdown formatting or codeblocks.
            Each object must contain keys EXACTLY like this:
            {
               "title": "API Name",
               "description": "API Description",
               "category": "Category name",
               "url": "Documentation URL",
               "auth": "None" / "apiKey" / "OAuth",
               "supportsHttps": true
            }
            Do not provide any text explanation or notes before or after. Under no circumstances should you generate malformed JSON.
        """.trimIndent()

        val gUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val reqBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Find 4 high-quality active APIs about: $topic")
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstructions)
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = reqBodyJson.toString().toRequestBody(mediaType)
        val request = Request.Builder().url(gUrl).post(body).build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseStr = response.body?.string().orEmpty()
                val rootJson = JSONObject(responseStr)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    var text = candidates.getJSONObject(0)
                        .optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.getJSONObject(0)
                        ?.optString("text")
                        .orEmpty().trim()
                    
                    // Strip potential markdown wrapping blocks e.g. ```json
                    if (text.startsWith("```")) {
                        text = text.replace(Regex("^```json\\s*"), "")
                            .replace(Regex("```$"), "")
                            .trim()
                    }

                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val ob = arr.getJSONObject(i)
                        resultList.add(
                            PublicApiEntry(
                                title = ob.optString("title", "Unknown API"),
                                description = ob.optString("description", "No description generated."),
                                category = ob.optString("category", "Synthesized"),
                                url = ob.optString("url", "https://github.com/public-apis/public-apis"),
                                auth = ob.optString("auth", "None"),
                                supportsHttps = ob.optBoolean("supportsHttps", true)
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (resultList.isEmpty()) {
        resultList.add(PublicApiEntry("Public APIs Companion", "Error synthesizing online APIs, fallback static registry entry. Check connectivity parameters.", "Debug", "https://github.com/public-apis/public-apis", "None", true))
    }
    return resultList
}
