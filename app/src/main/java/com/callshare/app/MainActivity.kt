package com.callshare.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ShineApp(applicationContext)
                }
            }
        }
    }
}

@Composable
fun ShineApp(context: Context) {

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    val database = remember {
        com.google.firebase.database.FirebaseDatabase
            .getInstance()
            .reference
    }

    val preferences = remember {
        context.getSharedPreferences(
            "shine_preferences",
            Context.MODE_PRIVATE
        )
    }

    var authenticated by remember {
        mutableStateOf(false)
    }

    var role by remember {
        mutableStateOf<String?>(null)
    }

    var pairingCode by remember {
        mutableStateOf("")
    }

    var connected by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf("Connecting to Shine...")
    }

    /*
     * Restore previous pairing when Shine is opened again.
     */
    LaunchedEffect(Unit) {

        val savedRole =
            preferences.getString("role", null)

        val savedCode =
            preferences.getString("pairingCode", null)

        if (savedRole != null && savedCode != null) {

            role = savedRole
            pairingCode = savedCode
            connected = true
            status = "Paired"
        }

        /*
         * Firebase anonymous authentication.
         */
        if (auth.currentUser != null) {

            authenticated = true
            if (savedRole == null) {
                status = "Ready"
            }

        } else {

            auth.signInAnonymously()
                .addOnSuccessListener {

                    authenticated = true

                    if (savedRole == null) {
                        status = "Ready"
                    }
                }
                .addOnFailureListener { error ->

                    status =
                        "Firebase connection failed: ${error.message}"
                }
        }
    }

    /*
     * Loading screen
     */
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

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(status)
        }

        return
    }

    /*
     * If already paired, go directly to Dashboard.
     */
    if (role != null && pairingCode.isNotEmpty()) {

        DashboardScreen(
            role = role!!,
            pairingCode = pairingCode,
            connected = connected,
            onUnpair = {

                /*
                 * For now we only clear the local pairing.
                 * We will implement secure Firebase unpairing
                 * after the dashboard is tested.
                 */
                preferences.edit()
                    .remove("role")
                    .remove("pairingCode")
                    .apply()

                role = null
                pairingCode = ""
                connected = false
                status = "Ready"
            }
        )

        return
    }

    /*
     * Role selection
     */
    if (role == null) {

        RoleSelectionScreen(
            onSenderSelected = {
                role = "sender"
            },
            onReceiverSelected = {
                role = "receiver"
            }
        )

        return
    }

    /*
     * Pairing screen
     */
    if (role == "sender") {

        SenderPairingScreen(
            database = database,
            uid = auth.currentUser!!.uid,
            onConnected = { code ->

                preferences.edit()
                    .putString("role", "sender")
                    .putString("pairingCode", code)
                    .apply()

                pairingCode = code
                connected = true
                status = "Paired"
            }
        )

    } else {

        ReceiverPairingScreen(
            database = database,
            uid = auth.currentUser!!.uid,
            onConnected = { code ->

                preferences.edit()
                    .putString("role", "receiver")
                    .putString("pairingCode", code)
                    .apply()

                pairingCode = code
                connected = true
                status = "Paired"
            }
        )
    }
}


/* ============================================================
   ROLE SELECTION
   ============================================================ */

@Composable
fun RoleSelectionScreen(
    onSenderSelected: () -> Unit,
    onReceiverSelected: () -> Unit
) {

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

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Connect two phones securely"
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        Button(
            onClick = onSenderSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sender")
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = onReceiverSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Receiver")
        }
    }
}


/* ============================================================
   SENDER PAIRING
   ============================================================ */

@Composable
fun SenderPairingScreen(
    database: DatabaseReference,
    uid: String,
    onConnected: (String) -> Unit
) {

    var pairingCode by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf("Generate a pairing code")
    }

    var listenerStarted by remember {
        mutableStateOf(false)
    }

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

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        if (pairingCode.isEmpty()) {

            Button(
                onClick = {

                    val code =
                        (100000..999999)
                            .random()
                            .toString()

                    val data =
                        hashMapOf<String, Any>(
                            "senderUid" to uid,
                            "status" to "waiting",
                            "createdAt" to ServerValue.TIMESTAMP
                        )

                    database
                        .child("pairingCodes")
                        .child(code)
                        .setValue(data)
                        .addOnSuccessListener {

                            pairingCode = code
                            status =
                                "Waiting for Receiver..."
                        }
                        .addOnFailureListener { error ->

                            status =
                                "Failed: ${error.message}"
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

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(status)

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            /*
             * Listen for Receiver.
             */
            if (!listenerStarted) {

                listenerStarted = true

                LaunchedEffect(pairingCode) {

                    database
                        .child("pairingCodes")
                        .child(pairingCode)
                        .addValueEventListener(
                            object : ValueEventListener {

                                override fun onDataChange(
                                    snapshot: DataSnapshot
                                ) {

                                    val receiverUid =
                                        snapshot
                                            .child("receiverUid")
                                            .getValue(String::class.java)

                                    if (
                                        !receiverUid.isNullOrEmpty()
                                    ) {

                                        status =
                                            "Receiver connected"

                                        onConnected(
                                            pairingCode
                                        )
                                    }
                                }

                                override fun onCancelled(
                                    error: DatabaseError
                                ) {

                                    status =
                                        "Connection error"
                                }
                            }
                        )
                }
            }
        }
    }
}


/* ============================================================
   RECEIVER PAIRING
   ============================================================ */

@Composable
fun ReceiverPairingScreen(
    database: DatabaseReference,
    uid: String,
    onConnected: (String) -> Unit
) {

    var enteredCode by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf("Enter Sender's code")
    }

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

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = enteredCode,

            onValueChange = {

                if (
                    it.length <= 6 &&
                    it.all { character ->
                        character.isDigit()
                    }
                ) {
                    enteredCode = it
                }
            },

            label = {
                Text("Enter Sender Code")
            },

            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Number
                ),

            singleLine = true,

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = {

                if (enteredCode.length != 6) {

                    status =
                        "Enter a 6-digit code"

                    return@Button
                }

                status = "Connecting..."

                val reference =
                    database
                        .child("pairingCodes")
                        .child(enteredCode)

                reference
                    .get()
                    .addOnSuccessListener { snapshot ->

                        if (!snapshot.exists()) {

                            status =
                                "Invalid pairing code"

                            return@addOnSuccessListener
                        }

                        val senderUid =
                            snapshot
                                .child("senderUid")
                                .getValue(String::class.java)

                        if (
                            senderUid.isNullOrEmpty()
                        ) {

                            status =
                                "Invalid pairing data"

                            return@addOnSuccessListener
                        }

                        val updates =
                            hashMapOf<String, Any>(
                                "receiverUid" to uid,
                                "status" to "connected"
                            )

                        reference
                            .updateChildren(updates)
                            .addOnSuccessListener {

                                status =
                                    "Connected to Sender"

                                onConnected(
                                    enteredCode
                                )
                            }
                            .addOnFailureListener { error ->

                                status =
                                    "Connection failed: ${error.message}"
                            }
                    }
                    .addOnFailureListener { error ->

                        status =
                            "Unable to connect: ${error.message}"
                    }
            },

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text("Connect")
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(status)
    }
}


/* ============================================================
   DASHBOARD
   ============================================================ */

@Composable
fun DashboardScreen(
    role: String,
    pairingCode: String,
    connected: Boolean,
    onUnpair: () -> Unit
) {

    val isReceiver = role == "receiver"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        /*
         * Header
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text = "Shine",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isReceiver) "Receiver" else "Sender"
                )
            }

            /*
             * Connection status is shown ONLY on Receiver.
             */
            if (isReceiver) {
                Text(
                    text = if (connected)
                        "🟢 Connected"
                    else
                        "🔴 Offline"
                )
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * Connection card is shown ONLY on Receiver.
         */
        if (isReceiver) {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "Connection",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = if (connected)
                            "Connected to Sender"
                        else
                            "Not connected"
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Pairing code: $pairingCode"
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }

        /*
         * Main feature card
         */
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                if (!isReceiver) {

                    Text(
                        text = "🔍 Analysing",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Button(
                        onClick = {
                            /*
                             * Recording functionality
                             * will be added next.
                             */
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Analysing")
                    }

                } else {

                    Text(
                        text = "🎵 Received Calls",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Received recordings will appear here."
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            /*
                             * Playback functionality
                             * will be added next.
                             */
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No Recordings Yet")
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        /*
         * Settings
         */
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Shine settings will be added here."
                )
            }
        }

        /*
         * Unpair is available ONLY to Receiver.
         */
        if (isReceiver) {

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            OutlinedButton(
                onClick = onUnpair,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect / Unpair")
            }
        }
    }
}
