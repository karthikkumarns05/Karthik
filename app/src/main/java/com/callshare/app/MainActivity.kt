package com.callshare.app

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

data class RecordingItem(
    val id: String,
    val fileUrl: String,
    val createdAt: Long,
    val duration: String = "",
    val senderUid: String = ""
)

@Composable
fun ShineApp(context: Context) {

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    val database = remember {
        FirebaseDatabase.getInstance().reference
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

    var dashboardVisible by remember {
        mutableStateOf(false)
    }

    var analysingEnabled by remember {
        mutableStateOf(false)
    }

    /*
     * Restore saved state and authenticate anonymously.
     */
    LaunchedEffect(Unit) {

        val savedRole =
            preferences.getString("role", null)

        val savedCode =
            preferences.getString("pairingCode", null)

        val savedDashboard =
            preferences.getBoolean(
                "dashboardVisible",
                false
            )

        val savedAnalysing =
            preferences.getBoolean(
                "analysingEnabled",
                false
            )

        role = savedRole
        pairingCode = savedCode ?: ""
        connected = !savedCode.isNullOrEmpty()
        dashboardVisible = savedDashboard
        analysingEnabled = savedAnalysing

        status =
            if (connected) {
                "Paired"
            } else {
                "Ready"
            }

        if (auth.currentUser != null) {

            authenticated = true

        } else {

            auth.signInAnonymously()
                .addOnSuccessListener {
                    authenticated = true
                    status = "Ready"
                }
                .addOnFailureListener { error ->
                    status =
                        "Firebase connection failed: ${error.message}"
                }
        }
    }

    /*
     * Sender watches for Receiver unpairing.
     */
    LaunchedEffect(
        role,
        pairingCode,
        connected
    ) {

        if (
            role != "sender" ||
            pairingCode.isEmpty() ||
            !connected
        ) {
            return@LaunchedEffect
        }

        database
            .child("pairingCodes")
            .child(pairingCode)
            .addValueEventListener(
                object : ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {

                        val firebaseStatus =
                            snapshot
                                .child("status")
                                .getValue(String::class.java)

                        if (
                            firebaseStatus == "unpaired"
                        ) {

                            preferences.edit()
                                .putString(
                                    "role",
                                    "sender"
                                )
                                .remove("pairingCode")
                                .putBoolean(
                                    "dashboardVisible",
                                    false
                                )
                                .apply()

                            pairingCode = ""
                            connected = false
                            dashboardVisible = false
                            status = "Ready"
                        }
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }
                }
            )
    }

    if (!authenticated) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text = "Shine",
                style =
                    MaterialTheme.typography.headlineLarge,
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
     * Dashboard
     */
    if (
        role != null &&
        dashboardVisible
    ) {

        DashboardScreen(
            role = role!!,
            pairingCode = pairingCode,
            connected = connected,
            analysingEnabled = analysingEnabled,
            database = database,
            uid = auth.currentUser!!.uid,

            onStartAnalysing = {

                preferences.edit()
                    .putBoolean(
                        "analysingEnabled",
                        true
                    )
                    .apply()

                analysingEnabled = true

                val intent =
                    Intent(
                        context,
                        CallRecordingService::class.java
                    ).apply {

                        action =
                            CallRecordingService
                                .ACTION_START_MONITORING

                        putExtra(
                            "senderUid",
                            auth.currentUser!!.uid
                        )

                        putExtra(
                            "pairingCode",
                            pairingCode
                        )
                    }

                try {

                    context.startForegroundService(
                        intent
                    )

                } catch (
                    exception: Exception
                ) {

                    status =
                        "Unable to start recording service"
                }
            },

            onUnpair = {

                /*
                 * Receiver unpair.
                 *
                 * IMPORTANT:
                 * Receiver remains on Dashboard.
                 */
                if (
                    role == "receiver" &&
                    pairingCode.isNotEmpty()
                ) {

                    val oldCode =
                        pairingCode

                    database
                        .child("pairingCodes")
                        .child(oldCode)
                        .updateChildren(
                            mapOf(
                                "status" to "unpaired"
                            )
                        )
                        .addOnCompleteListener {

                            preferences.edit()
                                .putString(
                                    "role",
                                    "receiver"
                                )
                                .remove("pairingCode")
                                .putBoolean(
                                    "dashboardVisible",
                                    true
                                )
                                .apply()

                            pairingCode = ""
                            connected = false
                            dashboardVisible = true
                            status = "Ready to pair"
                        }
                }
            },

            /*
             * Receiver temporarily opens pairing screen.
             */
            onPairWithSender = {

                dashboardVisible = false

                preferences.edit()
                    .putBoolean(
                        "dashboardVisible",
                        false
                    )
                    .apply()
            }
        )

        return
    }

    /*
     * No role selected.
     */
    if (role == null) {

        RoleSelectionScreen(

            onSenderSelected = {

                role = "sender"

                dashboardVisible = false

                preferences.edit()
                    .putString(
                        "role",
                        "sender"
                    )
                    .putBoolean(
                        "dashboardVisible",
                        false
                    )
                    .apply()
            },

            onReceiverSelected = {

                role = "receiver"

                dashboardVisible = false

                preferences.edit()
                    .putString(
                        "role",
                        "receiver"
                    )
                    .putBoolean(
                        "dashboardVisible",
                        false
                    )
                    .apply()
            }
        )

        return
    }

    /*
     * Sender pairing.
     */
    if (role == "sender") {

        SenderPairingScreen(
            database = database,
            uid = auth.currentUser!!.uid,

            onConnected = { code ->

                preferences.edit()
                    .putString(
                        "role",
                        "sender"
                    )
                    .putString(
                        "pairingCode",
                        code
                    )
                    .putBoolean(
                        "dashboardVisible",
                        true
                    )
                    .apply()

                pairingCode = code
                connected = true
                dashboardVisible = true
                status = "Paired"
            }
        )

    } else {

        /*
         * Receiver pairing.
         *
         * Every result eventually returns to Dashboard
         * unless pairing succeeds.
         */
        ReceiverPairingScreen(

            database = database,
            uid = auth.currentUser!!.uid,

            onConnected = { code ->

                preferences.edit()
                    .putString(
                        "role",
                        "receiver"
                    )
                    .putString(
                        "pairingCode",
                        code
                    )
                    .putBoolean(
                        "dashboardVisible",
                        true
                    )
                    .apply()

                pairingCode = code
                connected = true
                dashboardVisible = true
                status = "Paired"
            },

            onReturnToDashboard = {

                preferences.edit()
                    .putString(
                        "role",
                        "receiver"
                    )
                    .putBoolean(
                        "dashboardVisible",
                        true
                    )
                    .apply()

                dashboardVisible = true

                status =
                    if (
                        pairingCode.isNotEmpty()
                    ) {
                        "Paired"
                    } else {
                        "Not connected"
                    }
            }
        )
    }
}

@Composable
fun RoleSelectionScreen(
    onSenderSelected: () -> Unit,
    onReceiverSelected: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Shine",
            style =
                MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text("Connect two phones securely")

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
        mutableStateOf(
            "Generate a pairing code"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Shine — Sender",
            style =
                MaterialTheme.typography.headlineMedium,
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
                            "createdAt" to
                                ServerValue.TIMESTAMP
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
                style =
                    MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(status)

            LaunchedEffect(pairingCode) {

                database
                    .child("pairingCodes")
                    .child(pairingCode)
                    .addValueEventListener(
                        object :
                            ValueEventListener {

                            override fun onDataChange(
                                snapshot: DataSnapshot
                            ) {

                                val receiverUid =
                                    snapshot
                                        .child(
                                            "receiverUid"
                                        )
                                        .getValue(
                                            String::class.java
                                        )

                                val firebaseStatus =
                                    snapshot
                                        .child("status")
                                        .getValue(
                                            String::class.java
                                        )

                                if (
                                    !receiverUid
                                        .isNullOrEmpty() &&
                                    firebaseStatus ==
                                    "connected"
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

@Composable
fun ReceiverPairingScreen(
    database: DatabaseReference,
    uid: String,
    onConnected: (String) -> Unit,
    onReturnToDashboard: () -> Unit
) {

    var enteredCode by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf(
            "Enter Sender's code"
        )
    }

    var finished by remember {
        mutableStateOf(false)
    }

    fun returnToDashboard() {

        if (!finished) {

            finished = true

            onReturnToDashboard()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Shine — Receiver",
            style =
                MaterialTheme.typography.headlineMedium,
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
                    it.all(Char::isDigit)
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

            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = {

                if (
                    enteredCode.length != 6
                ) {

                    returnToDashboard()

                    return@Button
                }

                status = "Connecting..."

                database
                    .child("pairingCodes")
                    .child(enteredCode)
                    .get()
                    .addOnSuccessListener { snapshot ->

                        if (!snapshot.exists()) {

                            returnToDashboard()

                            return@addOnSuccessListener
                        }

                        val senderUid =
                            snapshot
                                .child("senderUid")
                                .getValue(
                                    String::class.java
                                )

                        if (
                            senderUid.isNullOrEmpty()
                        ) {

                            returnToDashboard()

                            return@addOnSuccessListener
                        }

                        val existingReceiver =
                            snapshot
                                .child("receiverUid")
                                .getValue(
                                    String::class.java
                                )

                        val currentStatus =
                            snapshot
                                .child("status")
                                .getValue(
                                    String::class.java
                                )

                        if (
                            !existingReceiver.isNullOrEmpty() &&
                            existingReceiver != uid &&
                            currentStatus == "connected"
                        ) {

                            returnToDashboard()

                        } else {

                            database
                                .child("pairingCodes")
                                .child(enteredCode)
                                .updateChildren(
                                    mapOf(
                                        "receiverUid" to uid,
                                        "status" to "connected"
                                    )
                                )
                                .addOnSuccessListener {

                                    if (!finished) {

                                        finished = true

                                        onConnected(
                                            enteredCode
                                        )
                                    }
                                }
                                .addOnFailureListener {

                                    returnToDashboard()
                                }
                        }
                    }
                    .addOnFailureListener {

                        returnToDashboard()
                    }
            },

            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = {
                returnToDashboard()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Dashboard")
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(status)
    }
}

@Composable
fun DashboardScreen(
    role: String,
    pairingCode: String,
    connected: Boolean,
    analysingEnabled: Boolean,
    database: DatabaseReference,
    uid: String,
    onStartAnalysing: () -> Unit,
    onUnpair: () -> Unit,
    onPairWithSender: () -> Unit
) {

    val isReceiver =
        role == "receiver"

    var recordings by remember {
        mutableStateOf(
            emptyList<RecordingItem>()
        )
    }

    /*
     * Receiver recording history.
     *
     * Recordings are stored by Receiver UID,
     * not pairing code.
     */
    LaunchedEffect(
        isReceiver,
        uid
    ) {

        if (!isReceiver) {
            return@LaunchedEffect
        }

        database
            .child("recordings")
            .child(uid)
            .addValueEventListener(
                object :
                    ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {

                        val loaded =
                            mutableListOf<RecordingItem>()

                        for (
                            child in
                            snapshot.children
                        ) {

                            val id =
                                child.key
                                    ?: continue

                            loaded.add(
                                RecordingItem(
                                    id = id,

                                    fileUrl =
                                        child
                                            .child(
                                                "fileUrl"
                                            )
                                            .getValue(
                                                String::class.java
                                            )
                                            ?: "",

                                    createdAt =
                                        child
                                            .child(
                                                "createdAt"
                                            )
                                            .getValue(
                                                Long::class.java
                                            )
                                            ?: 0L,

                                    duration =
                                        child
                                            .child(
                                                "duration"
                                            )
                                            .getValue(
                                                String::class.java
                                            )
                                            ?: "",

                                    senderUid =
                                        child
                                            .child(
                                                "senderUid"
                                            )
                                            .getValue(
                                                String::class.java
                                            )
                                            ?: ""
                                )
                            )
                        }

                        recordings =
                            loaded.sortedByDescending {
                                it.createdAt
                            }
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }
                }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text = "Shine",
                    style =
                        MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (isReceiver)
                        "Receiver"
                    else
                        "Sender"
                )
            }

            if (isReceiver) {

                Text(
                    if (connected)
                        "🟢 Connected"
                    else
                        "🔴 Not connected"
                )
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        /*
         * RECEIVER DASHBOARD
         */
        if (isReceiver) {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "Connection",
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        if (connected)
                            "Connected to Sender"
                        else
                            "Not connected"
                    )

                    if (connected) {

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Text(
                            "Pairing code: $pairingCode"
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            /*
             * Recording history remains visible
             * even after unpairing.
             */
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "🎵 Received Calls",
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    if (recordings.isEmpty()) {

                        Text(
                            "No recordings yet."
                        )

                    } else {

                        Text(
                            "${recordings.size} recording(s)"
                        )

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        LazyColumn {

                            items(
                                recordings,
                                key = {
                                    it.id
                                }
                            ) { recording ->

                                RecordingRow(
                                    recording
                                )
                            }
                        }
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            /*
             * Pair button is shown when disconnected.
             */
            if (!connected) {

                Button(
                    onClick =
                        onPairWithSender,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Pair with Sender"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )
            }

            /*
             * Unpair button only when connected.
             */
            if (connected) {

                OutlinedButton(
                    onClick = onUnpair,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Disconnect / Unpair"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )
            }

        } else {

            /*
             * SENDER DASHBOARD
             */
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "🔍 Analysing",
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        "Your call recordings will be analysed here."
                    )

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )

                    Button(
                        onClick =
                            onStartAnalysing,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "Start Analysing"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Text(
                        if (analysingEnabled)
                            "🟢 Automatic call recording enabled"
                        else
                            "Recording is not enabled yet."
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )
        }

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Settings",
                    style =
                        MaterialTheme.typography.titleLarge,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "Shine settings will be added here."
                )
            }
        }
    }
}

@Composable
fun RecordingRow(
    recording: RecordingItem
) {

    val context =
        LocalContext.current

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
    ) {

        Column(
            modifier =
                Modifier.padding(14.dp)
        ) {

            Text(
                text = "📞 Recording",
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            if (recording.createdAt > 0) {

                val date =
                    SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                    ).format(
                        Date(
                            recording.createdAt
                        )
                    )

                Text(date)
            }

            if (
                recording.duration.isNotEmpty()
            ) {

                Text(
                    "Duration: ${recording.duration}"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            if (
                recording.fileUrl.isNotEmpty()
            ) {

                Button(
                    onClick = {

                        try {

                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        recording.fileUrl
                                    )
                                )
                            )

                        } catch (
                            _: Exception
                        ) {
                        }
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "▶ Play Recording"
                    )
                }

            } else {

                Text(
                    "Recording file is not available."
                )
            }
        }
    }
}
