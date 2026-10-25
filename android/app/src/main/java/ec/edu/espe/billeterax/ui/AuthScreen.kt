package ec.edu.espe.billeterax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AuthScreen(state: WalletState, model: WalletViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val validUser = username.trim().matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,29}"))
    val valid = if (register) validUser && name.trim().length in 2..60 && password.length >= 8 && password.toByteArray().size <= 72 && password == confirmation
        else username.isNotBlank() && password.isNotEmpty()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.weight(1f))
            ThemeToggle(state, model)
            IconButton(onClick = { serverDialog = true }, enabled = !state.busy) { Icon(Icons.Rounded.Tune, "Configurar servidor") }
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.size(68.dp).background(Brush.linearGradient(listOf(colors.primary, colors.secondary)), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(if (register) Icons.Rounded.PersonAdd else Icons.Rounded.AccountBalanceWallet, null, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(if (register) "Tu cuenta, tu espacio" else "Qué bueno verte", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Crea tu usuario y empieza con tu nueva billetera." else "Ingresa para tener tus movimientos siempre a mano.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().background(colors.surfaceVariant, RoundedCornerShape(16.dp)).padding(4.dp)) {
            listOf(false to "Iniciar sesión", true to "Crear cuenta").forEach { (selected, label) ->
                if (register == selected) FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(label) }
                else TextButton(onClick = { register = selected; password = ""; confirmation = ""; model.dismissMessage() }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (register) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(username, { username = it }, label = { Text("Usuario") }, leadingIcon = { Icon(Icons.Rounded.PersonOutline, null) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            supportingText = if (register) ({ Text("3–30 letras, números, punto o guion") }) else null)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Ocultar contraseña" else "Mostrar contraseña") } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (register) ({ Text("Al menos 8 caracteres") }) else null)
        if (register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar contraseña") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = confirmation.isNotEmpty() && confirmation != password,
                supportingText = if (confirmation.isNotEmpty() && confirmation != password) ({ Text("Las contraseñas no coinciden") }) else null)
        }
        state.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = if (state.successful) colors.secondary else colors.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { model.authenticate(register, name, username, password) }, enabled = valid && state.ready && !state.busy,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(if (register) "Crear mi cuenta" else "Entrar a mi billetera", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            Text("Tu sesión, en tu dispositivo", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(28.dp))
        Text("Billetera de demostración · ESPE", Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 16.dp),
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
    if (serverDialog) {
        var url by remember(state.config.url) { mutableStateOf(state.config.url) }
        AlertDialog(onDismissRequest = { serverDialog = false }, icon = { Icon(Icons.Rounded.Dns, null) }, title = { Text("Conectar con tu servidor") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Usa la dirección del servidor de tu billetera.")
                OutlinedTextField(url, { url = it }, label = { Text("URL del servidor") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            } }, confirmButton = { TextButton(onClick = { model.saveServer(url); serverDialog = false }) { Text("Guardar servidor") } },
            dismissButton = { TextButton(onClick = { serverDialog = false }) { Text("Cancelar") } })
    }
}
