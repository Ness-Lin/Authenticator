package com.example.authenticator

import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.feature.enrollment.api.ConfirmEnrollment
import com.example.authenticator.feature.enrollment.api.EnrollmentResult
import com.example.authenticator.feature.enrollment.api.ManualEnrollmentRequest
import com.example.authenticator.feature.enrollment.api.PreparationResult
import com.example.authenticator.feature.enrollment.api.UriEnrollmentRequest
import com.example.authenticator.feature.enrollment.ui.QrScanner
import com.example.authenticator.feature.security.api.SecuritySessionState
import com.example.authenticator.feature.token.api.TokenResult
import com.example.authenticator.feature.token.api.TokenValue
import com.example.authenticator.ui.theme.AuthenticatorTheme
import com.example.authenticator.wiring.createAppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph by lazy { createAppGraph(applicationContext) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            AuthenticatorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthenticatorHome(
                        graph = graph,
                        modifier = Modifier.padding(innerPadding),
                        onLanguageSelected = { language ->
                            LanguageManager.setLanguage(this@MainActivity, language)
                            recreate()
                        },
                    )
                }
            }
        }
    }

    override fun onStop() {
        graph.securitySession.lock()
        super.onStop()
    }
}

@Composable
private fun AuthenticatorHome(
    graph: com.example.authenticator.wiring.AppGraph,
    modifier: Modifier = Modifier,
    onLanguageSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val securityState by graph.securitySession.state.collectAsState()
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AccountSummary>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showAddMethod by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var languageChoice by remember { mutableStateOf(LanguageManager.selectedLanguage(context)) }
    var showScanner by remember { mutableStateOf(false) }
    var qrPreparation by remember { mutableStateOf<com.example.authenticator.feature.enrollment.api.Preparation?>(null) }
    var issuer by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var tokens by remember { mutableStateOf<Map<String, TokenValue>>(emptyMap()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val invalidInput = stringResource(com.example.authenticator.R.string.invalid_input)
    val addFailed = stringResource(com.example.authenticator.R.string.add_failed)
    val scanInvalid = stringResource(com.example.authenticator.R.string.scan_invalid)
    val cameraPermissionMessage = stringResource(com.example.authenticator.R.string.scan_camera_permission)
    val cameraUnavailableMessage = stringResource(com.example.authenticator.R.string.scan_no_camera)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner = true else error = cameraPermissionMessage
    }

    LaunchedEffect(Unit) {
        if (securityState == SecuritySessionState.Locked) {
            graph.securitySession.unlock()
        }
    }
    LaunchedEffect(securityState, query) {
        if (securityState == SecuritySessionState.Unlocked) {
            graph.tokenService.observeAccounts(query).collect { accounts = it }
        }
    }
    LaunchedEffect(securityState) {
        if (securityState != SecuritySessionState.Unlocked) {
            tokens = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(COUNTDOWN_REFRESH_MILLIS)
        }
    }
    LaunchedEffect(accounts, securityState) {
        while (securityState == SecuritySessionState.Unlocked) {
            val next = accounts.associate { item ->
                item.id.value to when (val result = graph.tokenService.generate(item.id)) {
                    is TokenResult.Success -> result.value
                    else -> null
                }
            }.mapNotNull { (id, token) -> token?.let { id to it } }.toMap()
            tokens = next
            delay(1000)
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(com.example.authenticator.R.string.home_title))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { languageChoice = LanguageManager.selectedLanguage(context); showLanguage = true }) {
                    Text(stringResource(com.example.authenticator.R.string.language_action))
                }
                Button(onClick = { showAddMethod = true }, enabled = securityState == SecuritySessionState.Unlocked) {
                    Text(stringResource(com.example.authenticator.R.string.action_add))
                }
            }
        }
        when (securityState) {
            SecuritySessionState.Unlocked -> {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(com.example.authenticator.R.string.search_label)) })
                if (accounts.isEmpty()) Text(stringResource(com.example.authenticator.R.string.empty_accounts))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(accounts, key = { it.id.value }) { account -> AccountCard(account, tokens[account.id.value], nowMillis) } }
            }
            SecuritySessionState.Authenticating -> CircularProgressIndicator()
            else -> Button(onClick = { scope.launch { graph.securitySession.unlock() } }) { Text(stringResource(com.example.authenticator.R.string.action_unlock)) }
        }
    }

    if (showLanguage) AlertDialog(
        onDismissRequest = { showLanguage = false },
        title = { Text(stringResource(com.example.authenticator.R.string.language_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LanguageOption(
                label = stringResource(com.example.authenticator.R.string.language_system),
                selected = languageChoice == LanguageManager.SYSTEM,
                onClick = { languageChoice = LanguageManager.SYSTEM },
            )
            LanguageOption(
                label = stringResource(com.example.authenticator.R.string.language_chinese),
                selected = languageChoice == LanguageManager.CHINESE,
                onClick = { languageChoice = LanguageManager.CHINESE },
            )
            LanguageOption(
                label = stringResource(com.example.authenticator.R.string.language_english),
                selected = languageChoice == LanguageManager.ENGLISH,
                onClick = { languageChoice = LanguageManager.ENGLISH },
            )
        } },
        confirmButton = { Button(onClick = {
            showLanguage = false
            if (languageChoice != LanguageManager.selectedLanguage(context)) onLanguageSelected(languageChoice)
        }) { Text(stringResource(com.example.authenticator.R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { showLanguage = false }) { Text(stringResource(com.example.authenticator.R.string.action_cancel)) } },
    )

    if (showAddMethod) AlertDialog(
        onDismissRequest = { showAddMethod = false },
        title = { Text(stringResource(com.example.authenticator.R.string.add_method_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                showAddMethod = false
                error = null
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showScanner = true
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(com.example.authenticator.R.string.action_scan_qr)) }
            TextButton(onClick = { showAddMethod = false; showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(com.example.authenticator.R.string.action_manual)) }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showAddMethod = false }) { Text(stringResource(com.example.authenticator.R.string.action_cancel)) } },
    )

    if (showScanner) Dialog(
        onDismissRequest = { showScanner = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                QrScanner(
                    onResult = { raw ->
                        scope.launch {
                            showScanner = false
                            error = null
                            val prepared = graph.enrollmentService.prepareQr(UriEnrollmentRequest(raw))
                            when (prepared) {
                                is PreparationResult.Ready -> qrPreparation = prepared.preparation
                                else -> error = scanInvalid
                            }
                        }
                    },
                    onUnavailable = { showScanner = false; error = cameraUnavailableMessage },
                )
                TextButton(onClick = { showScanner = false }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Text(stringResource(com.example.authenticator.R.string.action_cancel))
                }
            }
        }
    }

    qrPreparation?.let { preparation ->
        AlertDialog(
            onDismissRequest = { graph.enrollmentService.discard(preparation.draftId); qrPreparation = null },
            title = { Text(stringResource(com.example.authenticator.R.string.scan_confirm_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(com.example.authenticator.R.string.scan_confirm_account, preparation.accountName))
                if (preparation.issuer.isNotBlank()) Text(stringResource(com.example.authenticator.R.string.scan_confirm_issuer, preparation.issuer))
            } },
            confirmButton = { Button(onClick = {
                scope.launch {
                    val result = graph.enrollmentService.confirm(ConfirmEnrollment(preparation.draftId, preparation.revision, false, null))
                    when (result) {
                        EnrollmentResult.Saved -> qrPreparation = null
                        else -> { error = addFailed; qrPreparation = null }
                    }
                }
            }) { Text(stringResource(com.example.authenticator.R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { graph.enrollmentService.discard(preparation.draftId); qrPreparation = null }) { Text(stringResource(com.example.authenticator.R.string.action_cancel)) } },
        )
    }

    if (error != null && !showAdd && !showAddMethod && qrPreparation == null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringResource(com.example.authenticator.R.string.scan_title)) },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(com.example.authenticator.R.string.action_cancel)) } },
        )
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false; error = null },
        title = { Text(stringResource(com.example.authenticator.R.string.add_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(issuer, { issuer = it }, label = { Text(stringResource(com.example.authenticator.R.string.issuer_label)) })
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(com.example.authenticator.R.string.account_label)) })
            OutlinedTextField(secret, { secret = it }, label = { Text(stringResource(com.example.authenticator.R.string.secret_label)) })
            error?.let { Text(it) }
        } },
        confirmButton = { Button(onClick = {
            scope.launch {
                val prepared = graph.enrollmentService.prepareManual(ManualEnrollmentRequest(issuer, name, secret))
                when (prepared) {
                    is PreparationResult.Ready -> {
                        val result = graph.enrollmentService.confirm(ConfirmEnrollment(prepared.preparation.draftId, prepared.preparation.revision, false, null))
                        when (result) {
                            EnrollmentResult.Saved -> { showAdd = false; issuer = ""; name = ""; secret = "" }
                            else -> error = addFailed
                        }
                    }
                    else -> error = invalidInput
                }
            }
        }) { Text(stringResource(com.example.authenticator.R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(com.example.authenticator.R.string.action_cancel)) } },
    )
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun AccountCard(account: AccountSummary, token: TokenValue?, nowMillis: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(if (account.issuer.isBlank()) account.accountName else "${account.issuer} - ${account.accountName}")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = token?.code ?: "------",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                token?.let { TotpCountdown(it, nowMillis) }
            }
        }
    }
}

/** Displays the fraction of the current TOTP period that remains. */
@Composable
private fun TotpCountdown(token: TokenValue, nowMillis: Long) {
    val periodMillis = (token.validUntilMillis - token.validFromMillis).coerceAtLeast(1L)
    val remainingMillis = (token.validUntilMillis - nowMillis).coerceIn(0L, periodMillis)
    val progress = remainingMillis.toFloat() / periodMillis.toFloat()
    CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.size(32.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
        strokeWidth = 3.dp,
    )
}

private const val COUNTDOWN_REFRESH_MILLIS = 100L
