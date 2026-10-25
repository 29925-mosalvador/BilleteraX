# Implementación Android

MainActivity solo inicia Compose. WalletViewModel administra estado y ciclo de vida; WalletRepository coordina REST, Room y eventos; WalletNetwork concentra Retrofit/OkHttp; WalletSocket conserva el túnel mientras la app está visible.

## Persistencia y aislamiento

WalletDatabase incluye `transactions`, `balances` y `pending_operations`. Los comprobantes tienen clave compuesta `scope,id`: scope combina URL canónica y cuenta. Así CTA001 en Windows no mezcla su historia con CTA001 de Debian. Las consultas usan Flow y la UI recibe StateFlow con `collectAsStateWithLifecycle`.

La reconciliación hace upsert por ID y conserva el historial existente. El snapshot y el saldo se aplican mediante `withTransaction`. No hay `allowMainThreadQueries` ni migración destructiva. Se exporta el esquema Room en `app/schemas/`; al modificarlo se debe incrementar la versión y agregar una migración explícita.

Una nueva operación se guarda como pendiente antes del envío. Una respuesta válida guarda su comprobante y elimina el pendiente. Una respuesta perdida conserva el UUID y muestra `Resultado pendiente de verificar`. El botón de reintento envía el mismo contenido. La conciliación también resuelve pendientes cuando encuentra su comprobante confirmado. Nunca se muestra éxito por anticipado.

## Corrutinas y concurrencia

- `viewModelScope` usa Main para publicar estados de UI; no hay I/O bloqueante en Main.
- Las funciones suspend del repositorio cambian a `Dispatchers.IO` para red, Room y conversión monetaria.
- Los Flow de Room y del socket usan IO; Retrofit y Room también administran sus ejecutores internos.
- Un Mutex evita que un snapshot antiguo sobrescriba el resultado de una operación posterior.
- Un canal conflado agrupa señales de eventos mientras ya se sincroniza.
- Cancelar el ámbito cierra el WebSocket con `awaitClose`; las cancelaciones de corrutinas no se convierten en errores de usuario.
- `LifecycleStartEffect` inicia y detiene la conexión al entrar/salir del primer plano. No se promete recepción en segundo plano ni notificaciones push del sistema.

La UI incluye registro/login sin correo, tarjeta de saldo, cuatro acciones, comprobantes e historial LazyColumn. Perfil permite copiar cuenta, cerrar sesión y elegir Sistema/Claro/Oscuro. El engranaje de acceso cambia el servidor; la cuenta y sesión vienen del login. ConfigStore cifra el token con Keystore, conserva perfil/tema y nunca guarda la contraseña. Cerrar sesión conserva Room; otra cuenta usa otro scope.

## Pruebas

`MoneyTest` comprueba exactitud y validación. `RoomPersistenceTest` inserta, cierra y reabre una base real del dispositivo; verifica persistencia, deduplicación y aislamiento. `DistributedFlowTest` necesita backend dev en 10.0.2.2:8080 y verifica Retrofit → servidor → comprobante → Room, además de transferencia entrante por WebSocket.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

La segunda orden necesita un emulador encendido y el backend local iniciado. No ejecutarla contra una base con dinero o datos reales: crea depósitos y transferencias de laboratorio.

## Pruebas de acceso 1.1.0

RegisteredUserFlowTest registra dos usuarios mediante Retrofit, comprueba login normalizado, cuentas distintas y saldo cero, depósito, transferencia entrante por WebSocket y conciliación Room. Otro caso comprueba cifrado/reapertura de ConfigStore y tema/URL al cerrar sesión. Acepta serverUrl; por defecto usa http://10.0.2.2:8085/. Para Debian ver deployment/debian/README.md. Las pruebas antiguas de DistributedFlowTest requieren sus tokens de laboratorio cuando el servidor no es el dev predeterminado.
