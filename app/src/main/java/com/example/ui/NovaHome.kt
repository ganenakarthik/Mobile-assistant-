package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovaHome(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasksStream.collectAsStateWithLifecycle()
    val inventory by viewModel.inventoryStream.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(sharedPrefs.getString("user_name", "Kartik") ?: "Kartik") }

    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "user_name") {
                userName = prefs.getString("user_name", "Kartik") ?: "Kartik"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // High fidelity active tab: ORB, DIALOGUE, MEMORY, SETTINGS
    var activeSubPage by remember { mutableStateOf("ORB") }

    // Live digital clock to keep screen vibrant and moving every single second (no static screens)
    var currentTimeString by remember { mutableStateOf("") }
    var greetingContextString by remember { mutableStateOf("SYSTEM NOMINAL") }
    
    // Smooth custom context calculation
    LaunchedEffect(Unit) {
        while (true) {
            val format = SimpleDateFormat("h:mm a", Locale.US)
            currentTimeString = format.format(Date())
            
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            greetingContextString = when (hour) {
                in 0..4 -> "NIGHT SECURE PROTOCOLS ACTIVE"
                in 5..11 -> "MORNING FREQUENCY CALIBRATED"
                in 12..16 -> "MIDDAY POWER RESERVE ENCLAVE"
                in 17..21 -> "EVENING INTEL MATRIX ALIGNED"
                else -> "LATE NIGHT SHIELD COGNITION ACTIVE"
            }
            delay(1000)
        }
    }

    // Handles native back button to fallback to the Home screen elegantly
    if (activeSubPage != "ORB") {
        BackHandler {
            if (activeSubPage == "SETTINGS") {
                activeSubPage = "MEMORY"
            } else {
                activeSubPage = "ORB"
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack),
        topBar = {
            NovaMockupHeader(
                activePage = activeSubPage,
                userName = userName,
                onBack = {
                    if (activeSubPage == "SETTINGS") {
                        activeSubPage = "MEMORY"
                    } else {
                        activeSubPage = "ORB"
                    }
                },
                onMenuClick = {
                    activeSubPage = "SETTINGS"
                },
                onProfileClick = {
                    activeSubPage = "SETTINGS"
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding()) // Pad only top, letting bottom content flow beautifully
                .fillMaxSize()
                .background(SpaceBlack)
        ) {
            // Dynamic drifting background grid/lines to emphasize alive HUD experience
            DriftingHUDGrid()

            // Single persistent instance of VoiceAssistantPanel to preserve STT, settings, and dialogue states
            VoiceAssistantPanel(
                viewModel = viewModel,
                tasksList = tasks,
                itemsList = inventory,
                activeTabForced = activeSubPage,
                modifier = Modifier.fillMaxSize(),
                onActiveTabChange = { activeSubPage = it }
            )

            // Modern Floating Bottom Navigation Bar overlaid on the edge-to-edge grid background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                NovaLuxuryFloatingNav(
                    activePage = if (activeSubPage == "SETTINGS") "MEMORY" else activeSubPage,
                    onPageSelected = { activeSubPage = it }
                )
            }
        }
    }
}

/**
 * Animated moving background guidelines for Nothing OS high-tech HUD atmosphere
 */
@Composable
fun DriftingHUDGrid() {
    val infiniteTransition = rememberInfiniteTransition(label = "grid_drift_transition")
    
    val driftOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Create spectacular deep cosmic ambient background glow
                val radialAmbient = Brush.radialGradient(
                    colors = listOf(
                        CyberCyan.copy(alpha = 0.08f),
                        TechTeal.copy(alpha = 0.05f),
                        SpaceBlack
                    ),
                    center = Offset(size.width / 2f, size.height * 0.8f),
                    radius = size.width * 1.1f
                )
                drawRect(brush = radialAmbient)

                val step = 60.dp.toPx()
                val offset = driftOffset
                
                // Draw thin luxurious horizontal coordinates
                var y = offset % step
                while (y < size.height) {
                    drawLine(
                        color = BorderSlate.copy(alpha = 0.09f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }

                // Draw vertical coordinates
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = BorderSlate.copy(alpha = 0.09f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
            }
    )
}

/**
 * Specialized Mockup Header conforming exactly to the visual reference mockups
 */
@Composable
fun NovaMockupHeader(
    activePage: String,
    userName: String = "Kartik",
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(SpaceBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        if (activePage == "ORB") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Hamburger Menu and NOVA Capsule Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Main System Sidebar Override",
                            tint = PureWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Rounded high-tech capsule "NOVA"
                    Box(
                        modifier = Modifier
                            .background(TechCard, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NOVA",
                            color = PureWhite,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Notification Bell with Badge and Dynamic Profile avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Visual Notifications Stack",
                            tint = PureWhite,
                            modifier = Modifier.size(24.dp)
                        )
                        // Tiny red neon notification trigger badge
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(GlowingRed, CircleShape)
                                .align(Alignment.TopEnd)
                        )
                    }

                    // Avatar Circle dynamic with active status ring
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, CyberCyan, CircleShape)
                            .clickable { onProfileClick() }
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0052D4),
                                        Color(0xFF4364F7),
                                        Color(0xFF6FB1FC)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.firstOrNull()?.toString()?.uppercase(Locale.ROOT) ?: "K",
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        // Active green status dot at the bottom-right corner of avatar
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(SageGreen, CircleShape)
                                .border(1.dp, SpaceBlack, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        } else {
            // Screen Back Arrow, Centered Title, and action buttons
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Return Frame Matrix",
                        tint = PureWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val headerTitleText = when (activePage) {
                    "DIALOGUE" -> "Chat with Nova"
                    "BROWSER" -> "Nova Core Browser"
                    "COGNITION" -> "AI Capability Manager"
                    "APIS" -> "Public APIs Hub"
                    "MEMORY" -> "Memory & Preferences"
                    "SETTINGS" -> "Settings & Permissions"
                    else -> ""
                }

                Text(
                    text = headerTitleText,
                    color = PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center
                )

                // Adaptive trailing icons for specific modules
                if (activePage == "DIALOGUE") {
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Console Dialogue Options",
                            tint = PureWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

/**
 * Premium Nothing UI/Aesthetic floating glassmorphic nav bar with responsive micro feedback
 */
@Composable
fun NovaLuxuryFloatingNav(
    activePage: String,
    onPageSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Safely handles bottom navigation insets
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val bottomGradient = Brush.verticalGradient(
            colors = listOf(
                CyberSlate.copy(alpha = 0.95f),
                SpaceBlack.copy(alpha = 0.98f)
            )
        )

        Row(
            modifier = Modifier
                .background(bottomGradient, RoundedCornerShape(26.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val menuItems = listOf(
                NavigationNavItem("ORB", Icons.Default.Home, "Home"),
                NavigationNavItem("DIALOGUE", Icons.Default.Send, "Chat"),
                NavigationNavItem("BROWSER", Icons.Default.Search, "Browser"),
                NavigationNavItem("COGNITION", Icons.Default.Build, "Cognition"),
                NavigationNavItem("MEMORY", Icons.Default.Person, "Memory")
            )

            menuItems.forEach { item ->
                val isSelected = activePage == item.id
                
                // Color animators for custom Luxury touch response
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) CyberCyan else CharcoalMuted,
                    animationSpec = tween(200),
                    label = "textColorNav"
                )
                
                val scaleFactor by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "scaleNav"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPageSelected(item.id) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .drawBehind {
                                        if (isSelected) {
                                            drawCircle(
                                                color = CyberCyan.copy(alpha = 0.12f),
                                                radius = size.minDimension / 0.8f
                                            )
                                        }
                                    }
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 12.dp, height = 2.dp)
                                    .background(CyberCyan, RoundedCornerShape(1.dp))
                            )
                        } else {
                            Text(
                                text = item.label,
                                color = iconColor,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavigationNavItem(
    val id: String,
    val icon: ImageVector,
    val label: String
)
