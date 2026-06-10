package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

// Data representation for AI Capabilities
data class AiCapability(
    val name: String,
    val description: String,
    val isConnected: Boolean,
    val type: String,
    val speed: String
)

// Data representation for the Hidden System AI Registry
data class RegistryEngine(
    val provider: String,
    val capability: String,
    val status: String,
    val cost: String,
    val latency: String,
    val connectionType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaCognitionPanel(
    modifier: Modifier = Modifier
) {
    var showHiddenRegistry by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("ALL") }

    val capabilities = remember {
        listOf(
            AiCapability("Search AI", "Scrapes internet indexes using background browser core", true, "Deep Search", "Fast (140ms)"),
            AiCapability("Reasoning AI", "Advanced linguistic logic, reasoning, and context comprehension", true, "Cognition", "Intense (480ms)"),
            AiCapability("Vision AI", "On-screen frame parsing, pixel reading, and HUD capturing", true, "Multimodal", "Variable"),
            AiCapability("Speech-to-Text", "Whisper and offline Android audio stream decoding", true, "Transcription", "Realtime"),
            AiCapability("Text-to-Speech", "Neural natural synthesizer voice generation pipelines", true, "Voice Engine", "Streamed"),
            AiCapability("Web Search", "Background browser scraper query, parsing, and data retrieval", true, "Automated Browse", "Scaped"),
            AiCapability("Local Automation", "Local Accessibility Service framework screen interaction agent", true, "Android Service", "Direct")
        )
    }

    val registryEngines = remember {
        listOf(
            RegistryEngine("Gemini Engine", "Reasoning / Vision", "Active", "0.00$ (Free API)", "340 ms", "Secure Client SSL"),
            RegistryEngine("Nova Scraper Agent", "Web Search", "Active", "Network Overhead (Free)", "1.2s avg", "Background Browser"),
            RegistryEngine("OpenRouter Core", "Language Fallback", "Connected", "System Key", "560 ms", "API Gateway"),
            RegistryEngine("Google TTS SDK", "Voice Synthesis", "Live", "On-Device", "30 ms", "Direct Framework Bind"),
            RegistryEngine("Accessibility Core", "Automation", "Live", "System permission", "10 ms", "AccessSvc Native")
        )
    }

    val routerDecisionLogs = remember {
        listOf(
            "Query Decided: 'find free ai APIs and save them' -> Decided: [Web Search via Browser] + [Notepad Save]. Status: SUCCESS",
            "Query Decided: 'what matches are happening right now' -> Decided: [Background Scrape Browser]. Status: COMPLETED",
            "Query Decided: 'take a screen shot' -> Decided: [Accessibility Screen Capture Pipeline]. Status: COMPLETED",
            "Query Decided: 'what are my stored notes' -> Decided: [SQLite Database Read Query]. Status: COMPLETED"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp), // Clear navigation overlapping safely
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status overview Dashboard Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TechCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, BorderSlate.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cognition_dashboard_banner")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NOVA COGNITION HEARTBEAT",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(SageGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, SageGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓ COGNITION NOMINAL",
                                color = SageGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Large Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Capabilities", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("7 / 7 Online", color = PureWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        VerticalDivider(color = BorderSlate.copy(alpha = 0.2f), modifier = Modifier.height(34.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default Engine", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("Gemini System AI", color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 2. Capabilities Section Header with filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI CAPABILITY MANAGER",
                    color = PureWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
                
                // Toggle Button to swap into Registry View
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showHiddenRegistry) CyberCyan.copy(alpha = 0.15f) else TechCard)
                        .border(1.dp, if (showHiddenRegistry) CyberCyan else BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { showHiddenRegistry = !showHiddenRegistry }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showHiddenRegistry) "SHOW SYSTEM MAP" else "SHOW DETAILED REGISTRY",
                        color = if (showHiddenRegistry) CyberCyan else PureWhite,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showHiddenRegistry) {
            // Layer 1 - Hidden Registry Map representation
            item {
                Text(
                    text = "L1 — HIDDEN CENTRAL AI ENGINE REGISTRY",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            items(registryEngines) { engine ->
                RegistryEngineCard(engine)
            }
        } else {
            // Main user-facing system capability manager
            items(capabilities) { cap ->
                CapabilityCard(cap)
            }
        }

        // 3. Layer 3 Tool Router Decisions Log Panel
        item {
            Text(
                text = "L3 — COGNITIVE TOOL ROUTER TRACE LOG (LIVE)",
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TechCard.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    routerDecisionLogs.forEach { logLine ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "▶",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = logLine,
                                color = PureWhite,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityCard(cap: AiCapability) {
    // Elegant pulsing animation for the connection indicators
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_indicator_active")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = TechCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capability_card_${cap.name.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = cap.name,
                        color = PureWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    // Capsule with Capability subclass type
                    Box(
                        modifier = Modifier
                            .background(SubCyan.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = cap.type.uppercase(),
                            color = CyberCyan,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = cap.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            // High-fidelity glowing radio indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(alphaPulse)
                        .clip(CircleShape)
                        .background(CyberCyan)
                )

                Text(
                    text = "Connected",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun RegistryEngineCard(engine: RegistryEngine) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TechCard.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active Indicator",
                        tint = SageGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = engine.provider,
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = engine.connectionType,
                    color = CharcoalMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(color = BorderSlate.copy(alpha = 0.1f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CAPABILITY", color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text(engine.capability, color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LATENCY", color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text(engine.latency, color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CONSUMPTION COST", color = TextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text(engine.cost, color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
