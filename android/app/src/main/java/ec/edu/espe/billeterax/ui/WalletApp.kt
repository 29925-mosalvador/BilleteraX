package ec.edu.espe.billeterax.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import ec.edu.espe.billeterax.data.LocalTransaction
import ec.edu.espe.billeterax.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun time(raw: String) = runCatching {
    DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
}.getOrDefault(raw)
private fun title(type: String): String = when(type) {
    "DEPOSIT" -> "Depósito"; "WITHDRAW" -> "Retiro"; "TRANSFER_IN" -> "Transferencia recibida"
    "TRANSFER_OUT" -> "Transferencia enviada"; "SERVICE" -> "Servicio Básico"; else -> type
}
private fun icon(type: String): ImageVector = when(type) {
    "DEPOSIT", "TRANSFER_IN" -> Icons.Rounded.SouthWest
    "SERVICE" -> Icons.Rounded.ReceiptLong
    else -> Icons.Rounded.NorthEast
}
@Composable internal fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Text("BilleteraX", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
@Composable internal fun ThemeToggle(state: WalletState, model: WalletViewModel) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    IconButton(onClick = { model.setAppearance(if (dark) Appearance.LIGHT else Appearance.DARK) }) {
        Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, if (dark) "Activar modo claro" else "Activar modo oscuro")
    }
}
@Composable fun WalletApp(model: WalletViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.started(); onStopOrDispose { model.stopped() } }
    WalletTheme(state.appearance) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (state.user == null) AuthScreen(state, model)
            else key(state.user!!.id) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "wallet") {
                    composable("wallet") { Dashboard(state, model, { nav.navigate("profile") { launchSingleTop = true } }) }
                    composable("profile") { ProfileScreen(state, model, { nav.popBackStack() }) }
                }
            }
        }
    }
}
@Composable private fun BottomNavigation(profile: Boolean, home: () -> Unit, settings: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        NavigationBarItem(selected = !profile, onClick = home, icon = { Icon(Icons.Rounded.SpaceDashboard, null) }, label = { Text("Inicio") })
        NavigationBarItem(selected = profile, onClick = settings, icon = { Icon(Icons.Rounded.PersonOutline, null) }, label = { Text("Perfil") })
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Dashboard(state: WalletState, model: WalletViewModel, profile: () -> Unit) {
    var operation by remember { mutableStateOf<Operation?>(null) }
    var detail by remember { mutableStateOf<LocalTransaction?>(null) }
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { BrandMark() }, actions = {
            ThemeToggle(state, model)
            IconButton(onClick = model::refresh, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "Sincronizar") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
    }, bottomBar = { BottomNavigation(false, {}, profile) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hola, ${state.user?.displayName?.substringBefore(' ') ?: ""}", style = MaterialTheme.typography.headlineSmall)
                Text("Tu dinero, siempre a la vista", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } }
            item {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF203C91), Color(0xFF194F72))), RoundedCornerShape(28.dp))) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color(0xFFB6CFFF), modifier = Modifier.size(18.dp))
                                Text("MI BILLETERA", color = Color(0xFFCEDBFF), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                            }
                            Text("USD", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (state.fresh) "Saldo disponible" else "Último saldo sincronizado", color = Color(0xFFD3DFF8), fontSize = 13.sp)
                        val amount = state.balance?.let { Money.display(it.cents) } ?: "—"
                        Text(amount, color = Color.White, fontSize = if (amount.length > 13) 26.sp else 38.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(state.config.account, color = Color(0xFFD3DFF8), fontSize = 13.sp)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(state.config.account)) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.ContentCopy, "Copiar número de cuenta", tint = Color(0xFFD3DFF8), modifier = Modifier.size(17.dp))
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = .15f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(7.dp).background(if (state.connection == Connection.CONNECTED) Color(0xFF82E8CA) else Color(0xFFFFD18A), CircleShape))
                            Text("WebSocket · ${state.connection.label}", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (!state.fresh && state.balance != null) item {
                Notice("Puedes consultar tu historial local. El saldo puede haber cambiado en el servidor.", false)
            }
            if (state.connectionError != null) item { Text(state.connectionError, color = colors.error, style = MaterialTheme.typography.bodySmall) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Action("Depositar", "Añade a tu saldo", Icons.Rounded.Add, Modifier.weight(1f), incomeColor(), state.ready && !state.busy && state.pending == null) { operation = Operation.DEPOSIT }
                        Action("Retirar", "Usa tu dinero", Icons.Rounded.Remove, Modifier.weight(1f), colors.primary, state.ready && !state.busy && state.pending == null) { operation = Operation.WITHDRAW }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Action("Transferir", "Envía a otra cuenta", Icons.AutoMirrored.Rounded.Send, Modifier.weight(1f), colors.primary, state.ready && !state.busy && state.pending == null) { operation = Operation.TRANSFER }
                        Action("Pagar servicios", "Tus pagos, al día", Icons.Rounded.ReceiptLong, Modifier.weight(1f), colors.tertiary, state.ready && !state.busy && state.pending == null) { operation = Operation.SERVICE }
                    }
                }
            }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.message?.let { message -> item { Notice(message, !state.successful) { model.dismissMessage() } } }
            state.pending?.let { pending -> item {
                Card(colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.HourglassTop, null, tint = colors.onTertiaryContainer)
                        Text("Resultado pendiente de verificar", fontWeight = FontWeight.Bold, color = colors.onTertiaryContainer)
                        Text("Aún no se confirmó esta operación. Verificarla conserva el mismo identificador y evita movimientos duplicados.", style = MaterialTheme.typography.bodySmall, color = colors.onTertiaryContainer)
                        Text(pending.operationId, style = MaterialTheme.typography.labelSmall, color = colors.onTertiaryContainer)
                        Button(onClick = model::retry, enabled = !state.busy) { Text("Verificar / reintentar") }
                    }
                }
            } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Movimientos", style = MaterialTheme.typography.titleLarge)
                Text("${state.transactions.size} registros", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            } }
            if (state.transactions.isEmpty()) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.ReceiptLong, null, modifier = Modifier.size(36.dp), tint = colors.primary)
                        Text("Tu historia empieza aquí", fontWeight = FontWeight.SemiBold)
                        Text("Tus movimientos aparecerán en este espacio.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            items(state.transactions, key = { it.id }) { entry ->
                Card(onClick = { detail = entry }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val color = if (entry.amountCents > 0) incomeColor() else expenseColor()
                        Box(Modifier.size(44.dp).background(color.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon(entry.type), null, tint = color) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title(entry.type), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(time(entry.occurredAt), color = colors.onSurfaceVariant, fontSize = 11.sp)
                            Text("Confirmado", color = color, fontSize = 10.sp)
                        }
                        Text((if (entry.amountCents > 0) "+" else "") + Money.display(entry.amountCents), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            state.balance?.let { balance -> item { Text("Última sincronización: ${time(balance.syncedAt)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) } }
        }
    }
    operation?.let { op -> OperationDialog(op, state, { operation = null }) { amount, dest, service, contract -> model.execute(op, amount, dest, service, contract); operation = null } }
    detail?.let { entry -> ReceiptDialog(entry) { detail = null } }
}
@Composable private fun Action(label: String, subtitle: String, symbol: ImageVector, modifier: Modifier, tint: Color, enabled: Boolean, click: () -> Unit) {
    OutlinedCard(onClick = click, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(38.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(symbol, null, tint = tint, modifier = Modifier.size(22.dp)) }
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
@Composable private fun Notice(message: String, error: Boolean, dismiss: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (error) colors.errorContainer else colors.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(message, color = if (error) colors.onErrorContainer else colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (dismiss != null) TextButton(onClick = dismiss) { Text("Entendido") }
        }
    }
}
@Composable private fun ReceiptDialog(entry: LocalTransaction, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(icon(entry.type), null) }, title = { Text("Comprobante") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title(entry.type), fontWeight = FontWeight.SemiBold)
            Text(Money.display(entry.amountCents), style = MaterialTheme.typography.headlineSmall, color = if (entry.amountCents > 0) incomeColor() else expenseColor())
            Text(time(entry.occurredAt)); HorizontalDivider()
            Text("Cuenta: ${entry.accountId}")
            entry.counterparty?.let { Text("Contraparte: $it") }; entry.service?.let { Text("Servicio: $it") }; entry.contract?.let { Text("Contrato: $it") }
            Text("Saldo después: ${Money.display(entry.balanceAfterCents)}")
            Text("ID: ${entry.id}", style = MaterialTheme.typography.bodySmall)
            Text("Operación: ${entry.operationId}", style = MaterialTheme.typography.bodySmall)
            Text("Confirmado", color = incomeColor())
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Cerrar") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileScreen(state: WalletState, model: WalletViewModel, home: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    Scaffold(containerColor = colors.background, topBar = { TopAppBar(title = { Text("Mi perfil", fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = home) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)) }, bottomBar = { BottomNavigation(true, home, {}) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(76.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text(state.user?.displayName?.take(1)?.uppercase() ?: "", color = colors.onPrimaryContainer, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Text(state.user?.displayName ?: "", style = MaterialTheme.typography.titleLarge)
                Text("@${state.user?.username ?: ""}", color = colors.onSurfaceVariant)
            } }
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tu número de cuenta", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text(state.config.account, fontWeight = FontWeight.SemiBold)
                        Text("Compártelo para recibir transferencias", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(state.config.account)) }) { Icon(Icons.Rounded.ContentCopy, "Copiar número de cuenta") }
                }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Apariencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Elige cómo quieres ver tu billetera", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Appearance.entries.forEach { appearance -> FilterChip(selected = state.appearance == appearance, onClick = { model.setAppearance(appearance) }, label = { Text(appearance.label) }) }
                }
            } }
            item { HorizontalDivider(color = colors.outlineVariant) }
            item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Conexión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.config.url, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Para cambiar de servidor, cierra sesión y abre la configuración de conexión.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } }
            item { OutlinedButton(onClick = model::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión")
            } }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Text("Billetera de demostración · ESPE", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
        }
    }
}

@Composable private fun OperationDialog(op: Operation, state: WalletState, dismiss: () -> Unit, submit: (String, String?, String?, String?) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf(if(state.config.account == "CTA001") "CTA002" else "") }
    var service by rememberSaveable { mutableStateOf("ELECTRICITY") }
    var contract by rememberSaveable { mutableStateOf("") }
    val validAmount = runCatching { Money.cents(amount) }.isSuccess
    val valid = validAmount && (op != Operation.TRANSFER || (destination.matches(Regex("CTA[0-9]{3,12}")) && destination != state.config.account)) &&
        (op != Operation.SERVICE || contract.matches(Regex("[A-Za-z0-9-]{6,20}")))
    AlertDialog(onDismissRequest = dismiss, title = { Text(op.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cuenta ${state.config.account} · USD", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Monto") }, prefix = { Text("$") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), supportingText = { Text("Mayor que 0 · máximo 2 decimales") })
            if(op == Operation.TRANSFER) OutlinedTextField(value = destination, onValueChange = { destination = it.uppercase() }, label = { Text("Cuenta destino") }, singleLine = true)
            if(op == Operation.SERVICE) {
                Text("Servicio", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ELECTRICITY" to "Luz", "WATER" to "Agua", "INTERNET" to "Internet").forEach { (key, label) ->
                        FilterChip(selected = service == key, onClick = { service = key }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(value = contract, onValueChange = { contract = it }, label = { Text("Código de contrato") }, singleLine = true,
                    supportingText = { Text("6–20 letras, números o guiones") })
            }
            Text("La operación se confirma únicamente con la respuesta del servidor.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { Button(onClick = { submit(amount, destination, service, contract) }, enabled = valid && !state.busy) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

