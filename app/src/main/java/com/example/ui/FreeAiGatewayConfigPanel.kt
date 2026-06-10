package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun FreeAiGatewayConfigPanel(
    context: Context,
    sharedPrefs: android.content.SharedPreferences,
    modifier: Modifier = Modifier
) {
    var pollinationsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("pollinations_enabled", true)) }
    var activeFreeProvider by remember { mutableStateOf(sharedPrefs.getString("free_ai_provider", "POLLINATIONS") ?: "POLLINATIONS") }
    
    var selectedPollinationsModel by remember { mutableStateOf(sharedPrefs.getString("pollinations_model", "openai") ?: "openai") }
    var showPollinationsModelDropdown by remember { mutableStateOf(false) }

    var selectedMirexaModel by remember { mutableStateOf(sharedPrefs.getString("mirexa_model", "deepseek-v3") ?: "deepseek-v3") }
    var showMirexaModelDropdown by remember { mutableStateOf(false) }

    var selectedLlm7Model by remember { mutableStateOf(sharedPrefs.getString("llm7_model", "mistral-small-3.1-24b-instruct-2503") ?: "mistral-small-3.1-24b-instruct-2503") }
    var showLlm7ModelDropdown by remember { mutableStateOf(false) }

    var customProxyBase by remember { mutableStateOf(sharedPrefs.getString("custom_proxy_base", "https://link.fuckicoding.com/v1") ?: "https://link.fuckicoding.com/v1") }
    var customProxyModel by remember { mutableStateOf(sharedPrefs.getString("custom_proxy_model", "gpt-4o-mini") ?: "gpt-4o-mini") }
    var customProxyKey by remember { mutableStateOf(sharedPrefs.getString("custom_proxy_key", "") ?: "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TechCard, RoundedCornerShape(14.dp))
            .border(1.2.dp, if (pollinationsEnabled) CyberCyan.copy(alpha = 0.5f) else BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("UNLIMITED FREE AI GATEWAY", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                Text("Keyless, server-synchronized free API channels", color = CharcoalMuted, fontSize = 9.sp)
            }
            Switch(
                checked = pollinationsEnabled,
                onCheckedChange = { checked ->
                    pollinationsEnabled = checked
                    sharedPrefs.edit().putBoolean("pollinations_enabled", checked).apply()
                    if (checked) {
                        Toast.makeText(context, "Free AI Channels Activated", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
            )
        }

        if (pollinationsEnabled) {
            // Provider Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceBlack, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val providers = listOf("POLLINATIONS", "MIREXA", "LLM7", "CUSTOM_PROXY")
                providers.forEach { prov ->
                    val active = activeFreeProvider == prov
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (active) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (active) CyberCyan.copy(alpha = 0.4f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                activeFreeProvider = prov
                                sharedPrefs.edit().putString("free_ai_provider", prov).apply()
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (prov == "CUSTOM_PROXY") "CUSTOM" else prov,
                            color = if (active) CyberCyan else PureWhite.copy(alpha = 0.5f),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Specific Provider Settings
            when (activeFreeProvider) {
                "POLLINATIONS" -> {
                    Text(
                        "Direct integration with Pollinations.ai text cluster. Unlimited free requests.",
                        color = CharcoalMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Model selection dropdown
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceBlack, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { showPollinationsModelDropdown = !showPollinationsModelDropdown }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Selected Model", color = CharcoalMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                Text(selectedPollinationsModel, color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (showPollinationsModelDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showPollinationsModelDropdown) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val pollinationsModels = listOf(
                                "openai",
                                "qwen-coder",
                                "deepseek-v3",
                                "mistral",
                                "gemini-2.5-flash-lite",
                                "claude-haiku-4.5",
                                "claude-sonnet-4.5",
                                "perplexity-sonar",
                                "kimi-k2-thinking",
                                "amazon-nova-micro"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(TechCard, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                pollinationsModels.forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sharedPrefs.edit().putString("pollinations_model", m).apply()
                                                selectedPollinationsModel = m
                                                showPollinationsModelDropdown = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m, color = if (selectedPollinationsModel == m) CyberCyan else PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        if (selectedPollinationsModel == m) {
                                            Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "MIREXA" -> {
                    Text(
                        "Connects directly with Mirexa keyless Open Source node. (Powered by deepseek & qwen)",
                        color = CharcoalMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Model selection dropdown
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceBlack, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { showMirexaModelDropdown = !showMirexaModelDropdown }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Selected Model", color = CharcoalMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                Text(selectedMirexaModel, color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (showMirexaModelDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showMirexaModelDropdown) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val mirexaModels = listOf(
                                "deepseek-v3",
                                "gpt-4.1",
                                "gpt-4.1-mini",
                                "qwen2.5-coder-32b",
                                "llama-4-scout-17b",
                                "phi-4"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(TechCard, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                mirexaModels.forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sharedPrefs.edit().putString("mirexa_model", m).apply()
                                                selectedMirexaModel = m
                                                showMirexaModelDropdown = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m, color = if (selectedMirexaModel == m) CyberCyan else PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        if (selectedMirexaModel == m) {
                                            Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "LLM7" -> {
                    Text(
                        "Direct integration with LLM7 API core. Fast processing clusters.",
                        color = CharcoalMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Model selection dropdown
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpaceBlack, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { showLlm7ModelDropdown = !showLlm7ModelDropdown }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                    Text("Selected Model", color = CharcoalMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(selectedLlm7Model, color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (showLlm7ModelDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showLlm7ModelDropdown) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val llm7Models = listOf(
                                "mistral-small-3.1-24b-instruct-2503",
                                "codestral-2501",
                                "deepseek-r1-0528",
                                "gpt-4.1-nano-2025-04-14",
                                "grok-3-mini-high",
                                "qwen2.5-coder-32b-instruct"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(TechCard, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                llm7Models.forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sharedPrefs.edit().putString("llm7_model", m).apply()
                                                selectedLlm7Model = m
                                                showLlm7ModelDropdown = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m, color = if (selectedLlm7Model == m) CyberCyan else PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        if (selectedLlm7Model == m) {
                                            Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "CUSTOM_PROXY" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Configure any compatible OpenAI/custom proxy gateway instantly:",
                            color = PureWhite,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        // Base URL Textfield
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("API Base URL (OpenAI Spec)", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            OutlinedTextField(
                                value = customProxyBase,
                                onValueChange = {
                                    customProxyBase = it
                                    sharedPrefs.edit().putString("custom_proxy_base", it).apply()
                                },
                                placeholder = { Text("https://link.fuckicoding.com/v1", color = CharcoalMuted, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderSlate.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Model String Textfield
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Model ID / String", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            OutlinedTextField(
                                value = customProxyModel,
                                onValueChange = {
                                    customProxyModel = it
                                    sharedPrefs.edit().putString("custom_proxy_model", it).apply()
                                },
                                placeholder = { Text("gpt-4o-mini", color = CharcoalMuted, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderSlate.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Key Textfield
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Bearer Token / Key (Optional)", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            OutlinedTextField(
                                value = customProxyKey,
                                onValueChange = {
                                    customProxyKey = it
                                    sharedPrefs.edit().putString("custom_proxy_key", it).apply()
                                },
                                placeholder = { Text("Enter token if required (defaults to free-key)", color = CharcoalMuted, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderSlate.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Quick-load presets buttons
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("QUICK CHANNELS PRESETS", color = CharcoalMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            
                            val presets = listOf(
                                Triple("FuckICoding Proxy", "https://link.fuckicoding.com/v1", "chatgpt-4o-mini"),
                                Triple("Mirexa Scout", "https://mirexa.vercel.app/v1", "llama-4-scout-17b"),
                                Triple("Heck API Core", "https://heck.ai/api/v1", "chatgpt-4o-mini"),
                                Triple("Unclose AI Client", "https://uncloseai.com/v1", "gpt-4o-mini")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.take(2).forEach { (label, base, mod) ->
                                    TextButton(
                                        onClick = {
                                            customProxyBase = base
                                            customProxyModel = mod
                                            sharedPrefs.edit()
                                                .putString("custom_proxy_base", base)
                                                .putString("custom_proxy_model", mod)
                                                .apply()
                                            Toast.makeText(context, "$label loaded!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(CyberCyan.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                            .border(1.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    ) {
                                        Text(label, color = CyberCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.drop(2).forEach { (label, base, mod) ->
                                    TextButton(
                                        onClick = {
                                            customProxyBase = base
                                            customProxyModel = mod
                                            sharedPrefs.edit()
                                                .putString("custom_proxy_base", base)
                                                .putString("custom_proxy_model", mod)
                                                .apply()
                                            Toast.makeText(context, "$label loaded!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(CyberCyan.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                            .border(1.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    ) {
                                        Text(label, color = CyberCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
