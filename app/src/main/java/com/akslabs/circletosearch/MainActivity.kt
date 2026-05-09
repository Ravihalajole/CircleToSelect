package com.akslabs.circletosearch

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akslabs.circletosearch.ui.theme.CircleToSearchTheme
import com.akslabs.circletosearch.ui.components.AccessibilityDisclosureDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Toast.makeText(this, "Double tap status bar or use bubble to start.", Toast.LENGTH_LONG).show()
        
        setContent {
            CircleToSearchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("home") }
                    
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            "settings" -> com.akslabs.circletosearch.ui.OverlaySettingsScreen(
                                onBack = { currentScreen = "home" }
                            )
                            else -> SetupScreen(
                                onSettingsClick = { currentScreen = "settings" }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var isDefaultAssistant by remember { mutableStateOf(isDefaultAssistant(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    
    // Refresh status when returning to app
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                isDefaultAssistant = isDefaultAssistant(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Circle to Extract",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                     Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Extract text & entities from your screen instantly, 100% offline via ML Kit.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Accessibility Section
            SectionLabel(
                text = "REQUIRED:", 
                color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            
            if (isAccessibilityEnabled) {
                StatusCard(
                    title = "Accessibility Service Active",
                    subtitle = "Double tap status bar to launch",
                    icon = Icons.Default.CheckCircle,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            } else {
                ActionCard(
                    title = "Enable Accessibility",
                    subtitle = "Required for screen capture and gestures.",
                    icon = Icons.Default.Accessibility,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = { showAccessibilityDisclosure = true }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Assistant Section
            SectionLabel(text = "OPTIONAL: For Home Button Trigger", color = MaterialTheme.colorScheme.secondary)

            if (isDefaultAssistant) {
                StatusCard(
                    title = "Default Assistant Set",
                    subtitle = "Hold Home or swipe up to trigger",
                    icon = Icons.Default.CheckCircle,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            } else {
                ActionCard(
                    title = "Set as Default Assistant",
                    subtitle = "Trigger via system assistant gestures.",
                    icon = Icons.Default.TouchApp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Customization Section
            SectionLabel(text = "CUSTOMIZATION", color = MaterialTheme.colorScheme.secondary)
            BubbleSwitch(context)
            
            Spacer(modifier = Modifier.height(32.dp))

            // Privacy Footer
            Text(
                text = "Privacy First.\nOn-device recognition ensures your data never leaves your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showAccessibilityDisclosure = false
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }
}

@Composable
fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
fun StatusCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, containerColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, containerColor: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedComponentName = android.content.ComponentName(context, CircleToSearchAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabledServicesSetting.split(':').any {
        android.content.ComponentName.unflattenFromString(it) == expectedComponentName
    }
}

fun isDefaultAssistant(context: android.content.Context): Boolean {
    val assistant = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
    val component = android.content.ComponentName(context, CircleToSearchVoiceService::class.java)
    return assistant == component.flattenToString()
}

@Composable
fun BubbleSwitch(context: android.content.Context) {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("bubble_enabled", false)) }

    ListItem(
        headlineContent = { Text("Floating Bubble") },
        supportingContent = { Text("Trigger extraction with a floating button") },
        trailingContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = { 
                    isEnabled = it
                    prefs.edit().putBoolean("bubble_enabled", it).apply()
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
