package com.callshare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CallShareApp()
                }
            }
        }
    }
}

@Composable
fun CallShareApp() {

    var selectedRole by remember { mutableStateOf<String?>(null) }

    when (selectedRole) {
        null -> RoleSelectionScreen(
            onSender = { selectedRole = "Sender" },
            onReceiver = { selectedRole = "Receiver" }
        )

        "Sender" -> SenderScreen(
            onBack = { selectedRole = null }
        )

        "Receiver" -> ReceiverScreen(
            onBack = { selectedRole = null }
        )
    }
}

@Composable
fun RoleSelectionScreen(
    onSender: () -> Unit,
    onReceiver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "CallShare",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Choose how this phone will be used"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSender,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sender")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onReceiver,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Receiver")
        }
    }
}

@Composable
fun SenderScreen(
    onBack: () -> Unit
) {

    var pairingCode by remember { mutableStateOf<String?>(null) }
    var paired by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Sender",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (pairingCode == null) {

            Text(
                text = "Generate a pairing code for the Receiver."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    pairingCode = generatePairingCode()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Pairing Code")
            }

        } else {

            Text(
                text = "Your Pairing Code",
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = pairingCode!!,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Enter this code on the Receiver phone."
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (paired) {

                Text(
                    text = "✓ Receiver Paired",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

            } else {

                Text(
                    text = "Waiting for Receiver..."
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun ReceiverScreen(
    onBack: () -> Unit
) {

    var enteredCode by remember { mutableStateOf("") }
    var paired by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Receiver",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!paired) {

            Text(
                text = "Enter the pairing code shown on the Sender."
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = enteredCode,
                onValueChange = {
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        enteredCode = it
                    }
                },
                label = {
                    Text("Pairing Code")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (enteredCode.length == 6) {
                        paired = true
                    }
                },
                enabled = enteredCode.length == 6,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pair")
            }

        } else {

            Text(
                text = "✓ Paired Successfully",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "This Receiver is paired with the Sender."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Waiting for recordings..."
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

fun generatePairingCode(): String {
    return Random.nextInt(100000, 1000000).toString()
}
