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
import androidx.compose.runtime.LaunchedEffect
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ShineApp()
                }
            }
        }
    }
}

@Composable
fun ShineApp() {

    val auth = remember { FirebaseAuth.getInstance() }
    val database = remember { FirebaseDatabase.getInstance().reference }

    var authenticated by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf<String?>(null) }

    var pairingCode by remember { mutableStateOf("") }
    var enteredCode by remember { mutableStateOf("") }

    var status by remember { mutableStateOf("Connecting to Shine...") }
    var connected by remember { mutableStateOf(false) }

    var senderUid by remember { mutableStateOf<String?>(null) }

    /*
     * Firebase anonymous authentication.
     * No username, password or OTP is required.
     */
    LaunchedEffect(Unit) {

        if (auth.currentUser != null) {
            authenticated = true
            status = "Ready"
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    authenticated = true
                    status = "Ready"
                }
                .addOnFailureListener { error ->
                    status = "Firebase connection failed: ${error.message}"
                }
        }
    }

    if (!authenticated) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Shine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = status)
        }

        return
    }

    if (role == null) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Shine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Connect two phones securely"
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    role = "sender"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sender")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    role = "receiver"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Receiver")
            }
        }

        return
    }

    if (role == "sender") {

        SenderScreen(
            database = database,
            uid = auth.currentUser!!.uid,
            pairingCode = pairingCode,
            connected = connected,
            status = status,
            onCodeGenerated = { code ->
                pairingCode = code
            },
            onConnected = {
                connected = true
                status = "Receiver connected"
            }
        )

    } else {

        ReceiverScreen(
            database = database,
            uid = auth.currentUser!!.uid,
            enteredCode = enteredCode,
            connected = connected,
            status = status,
            onCodeChanged = {
                enteredCode = it
            },
            onConnected = {
                connected = true
                status = "Connected to Sender"
            }
        )
    }
}

@Composable
fun SenderScreen(
    database: DatabaseReference,
    uid: String,
    pairingCode: String,
    connected: Boolean,
    status: String,
    onCodeGenerated: (String) -> Unit,
    onConnected: () -> Unit
) {

    var localStatus by remember { mutableStateOf(status) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Shine — Sender",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (pairingCode.isEmpty()) {

            Button(
                onClick = {

                    val code = (100000..999999).random().toString()

                    val data = hashMapOf<String, Any>(
                        "senderUid" to uid,
                        "status" to "waiting",
                        "createdAt" to ServerValue.TIMESTAMP
                    )

                    database.child("pairingCodes")
                        .child(code)
                        .setValue(data)
                        .addOnSuccessListener {

                            onCodeGenerated(code)
                            localStatus = "Waiting for Receiver..."
                        }
                        .addOnFailureListener { error ->
                            localStatus =
                                "Failed to create code: ${error.message}"
                        }
                }
            ) {
                Text("Generate Pairing Code")
            }

        } else {

            Text(
                text = pairingCode,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(localStatus)

            Spacer(modifier = Modifier.height(20.dp))

            if (connected) {
                Text(
                    text = "🟢 Connected",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            /*
             * Listen for Receiver joining this code.
             */
            LaunchedEffect(pairingCode) {

                val reference =
                    database.child("pairingCodes").child(pairingCode)

                reference.addValueEventListener(
                    object : ValueEventListener {

                        override fun onDataChange(snapshot: DataSnapshot) {

                            val receiverUid =
                                snapshot.child("receiverUid")
                                    .getValue(String::class.java)

                            if (!receiverUid.isNullOrEmpty()) {

                                localStatus = "Receiver connected"

                                onConnected()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            localStatus =
                                "Connection error: ${error.message}"
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ReceiverScreen(
    database: DatabaseReference,
    uid: String,
    enteredCode: String,
    connected: Boolean,
    status: String,
    onCodeChanged: (String) -> Unit,
    onConnected: () -> Unit
) {

    var localStatus by remember { mutableStateOf(status) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Shine — Receiver",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = enteredCode,
            onValueChange = {
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    onCodeChanged(it)
                }
            },
            label = {
                Text("Enter Sender Code")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {

                if (enteredCode.length != 6) {
                    localStatus = "Enter a 6-digit code"
                    return@Button
                }

                localStatus = "Connecting..."

                val reference =
                    database.child("pairingCodes").child(enteredCode)

                reference.get()
                    .addOnSuccessListener { snapshot ->

                        if (!snapshot.exists()) {

                            localStatus = "Invalid pairing code"
                            return@addOnSuccessListener
                        }

                        val senderUid =
                            snapshot.child("senderUid")
                                .getValue(String::class.java)

                        if (senderUid.isNullOrEmpty()) {

                            localStatus = "Invalid pairing data"
                            return@addOnSuccessListener
                        }

                        val updates = hashMapOf<String, Any>(
                            "receiverUid" to uid,
                            "status" to "connected"
                        )

                        reference.updateChildren(updates)
                            .addOnSuccessListener {

                                localStatus = "Connected to Sender"

                                onConnected()
                            }
                            .addOnFailureListener { error ->

                                localStatus =
                                    "Connection failed: ${error.message}"
                            }
                    }
                    .addOnFailureListener { error ->

                        localStatus =
                            "Unable to find code: ${error.message}"
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(localStatus)

        if (connected) {

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "🟢 Connected",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
