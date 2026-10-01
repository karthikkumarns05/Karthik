package com.callshare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppMode { SENDER, RECEIVER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CallShareApp() }
    }
}

@Composable
fun CallShareApp() {
    var mode by remember { mutableStateOf<AppMode?>(null) }
    var paired by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when {
                mode == null -> RoleScreen { mode = it }
                !paired -> PairingScreen(mode!!) { paired = true }
                else -> HomeScreen(mode!!)
            }
        }
    }
}

@Composable
private fun RoleScreen(onModeSelected: (AppMode) -> Unit) {
    Column(
        Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("CallShare", style = MaterialTheme.typography.headlineLarge)
        Text("Consent-based call recording and automatic sharing.")
        Text("Choose this device's role")
        Button(onClick = { onModeSelected(AppMode.SENDER) }, Modifier.fillMaxWidth()) { Text("Sender") }
        OutlinedButton(onClick = { onModeSelected(AppMode.RECEIVER) }, Modifier.fillMaxWidth()) { Text("Receiver") }
    }
}

@Composable
private fun PairingScreen(mode: AppMode, onPaired: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (mode == AppMode.SENDER) "Pair a receiver" else "Pair a sender",
            style = MaterialTheme.typography.headlineMedium
        )
        Text("Use a one-time pairing code. No username or password is required.")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Pairing code") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = code.length >= 4,
            onClick = onPaired,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pair device") }
        Text("Recording and sharing must be disclosed to the people on the call.")
    }
}

@Composable
private fun HomeScreen(mode: AppMode) {
    Column(
        Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (mode == AppMode.SENDER) "Sender" else "Receiver",
            style = MaterialTheme.typography.headlineLarge
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (mode == AppMode.SENDER) "Default receiver: Paired device" else "Paired sender: Connected")
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mode == AppMode.SENDER)
                        "Supported recordings will be uploaded automatically."
                    else
                        "New recordings will appear here automatically."
                )
            }
        }
        Text("Recordings", style = MaterialTheme.typography.titleLarge)
        Text("No recordings yet.")
    }
}
