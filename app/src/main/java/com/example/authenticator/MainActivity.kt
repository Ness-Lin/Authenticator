package com.example.authenticator

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.feature.enrollment.api.ConfirmEnrollment
import com.example.authenticator.feature.enrollment.api.EnrollmentResult
import com.example.authenticator.feature.enrollment.api.ManualEnrollmentRequest
import com.example.authenticator.feature.enrollment.api.PreparationResult
import com.example.authenticator.feature.security.api.SecuritySessionState
import com.example.authenticator.feature.token.api.TokenResult
import com.example.authenticator.ui.theme.AuthenticatorTheme
import com.example.authenticator.wiring.createAppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph by lazy { createAppGraph(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            AuthenticatorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthenticatorHome(graph, Modifier.padding(innerPadding))
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
private fun AuthenticatorHome(graph: com.example.authenticator.wiring.AppGraph, modifier: Modifier = Modifier) {
    val securityState by graph.securitySession.state.collectAsState()
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AccountSummary>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var issuer by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var codes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val invalidInput = stringResource(com.example.authenticator.R.string.invalid_input)
    val addFailed = stringResource(com.example.authenticator.R.string.add_failed)

    LaunchedEffect(Unit) { if (securityState == SecuritySessionState.Locked) graph.securitySession.unlock() }
    LaunchedEffect(securityState, query) {
        if (securityState == SecuritySessionState.Unlocked) {
            graph.tokenService.observeAccounts(query).collect { accounts = it }
        }
    }
    LaunchedEffect(accounts, securityState) {
        while (securityState == SecuritySessionState.Unlocked) {
            val next = accounts.associate { item ->
                item.id.value to when (val result = graph.tokenService.generate(item.id)) {
                    is TokenResult.Success -> result.value.code
                    else -> "------"
                }
            }
            codes = next
            delay(1000)
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(com.example.authenticator.R.string.home_title))
            Button(onClick = { showAdd = true }, enabled = securityState == SecuritySessionState.Unlocked) { Text(stringResource(com.example.authenticator.R.string.action_add)) }
        }
        when (securityState) {
            SecuritySessionState.Unlocked -> {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(com.example.authenticator.R.string.search_label)) })
                if (accounts.isEmpty()) Text(stringResource(com.example.authenticator.R.string.empty_accounts))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(accounts, key = { it.id.value }) { account -> AccountCard(account, codes[account.id.value] ?: "------") } }
            }
            SecuritySessionState.Authenticating -> CircularProgressIndicator()
            else -> Button(onClick = { scope.launch { graph.securitySession.unlock() } }) { Text(stringResource(com.example.authenticator.R.string.action_unlock)) }
        }
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
                when (val prepared = graph.enrollmentService.prepareManual(ManualEnrollmentRequest(issuer, name, secret))) {
                    is PreparationResult.Ready -> when (graph.enrollmentService.confirm(ConfirmEnrollment(prepared.preparation.draftId, prepared.preparation.revision, false, null))) {
                        EnrollmentResult.Saved -> { showAdd = false; issuer = ""; name = ""; secret = "" }
                        else -> error = addFailed
                    }
                    else -> error = invalidInput
                }
            }
        }) { Text(stringResource(com.example.authenticator.R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(com.example.authenticator.R.string.action_cancel)) } },
    )
}

@Composable
private fun AccountCard(account: AccountSummary, code: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(if (account.issuer.isBlank()) account.accountName else "${account.issuer} - ${account.accountName}"); Spacer(Modifier.height(8.dp)); Text(code) } }
}
