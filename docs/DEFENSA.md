# Guía técnica de defensa

Defensa indicada en el documento académico: 29 de octubre de 2026. Preparar Android visible junto a Postman y una terminal Debian. Los datos y pagos son simulados.

Actualización 1.1.0 del 24 de octubre: iniciar con registro/login sin correo. Cada usuario nuevo tiene cuenta propia con saldo cero. Mostrar Perfil, copiar cuenta y elegir Claro/Oscuro. El backend conserva usuarios y sesiones en H2. Ver `ACCESO_USUARIOS.md` y `testing/RESULTADOS.md` para resultados actuales. Las cifras de CTA001/CTA002 en las evidencias del 17 de octubre son históricas; no corresponden automáticamente a los usuarios nuevos.

## Guion para exponer en 7–10 minutos

1. **Objetivo, 1 minuto.** «BilleteraX es una billetera de laboratorio. Android permite consultar y solicitar operaciones; un backend Spring Boot en Debian valida y confirma los movimientos. Son datos y pagos simulados».
2. **Arquitectura y bases, 2 minutos.** Explicar Android/Kotlin/Compose, REST para solicitudes, WebSocket para avisos de cambios y Room para persistencia local. Debian ejecuta Spring Boot y guarda H2 en disco. Android consulta la API; no abre directamente la base del servidor.
3. **Operación normal y validación, 2 minutos.** Registrar o entrar con un usuario y mostrar su número de cuenta y saldo; depositar un importe pequeño, comprobar recibo e historial; intentar retirar más que el saldo y demostrar rechazo sin cambios.
4. **Actualización entrante, 1 minuto.** Desde Postman enviar $20 de un usuario B a A y observar actualización en Android sin tocar Actualizar. Explicar que el evento WebSocket dispara conciliación REST y Room actualiza la UI mediante Flow.
5. **Desconexión y recuperación, 2–3 minutos.** Ejecutar la secuencia de abajo: saldo local, UUID pendiente, reapertura y reintento sin duplicados.
6. **Pruebas y límites, 1 minuto.** Mostrar evidencias y distinguir pruebas aprobadas de pendientes. No presentar esta billetera académica como servicio bancario real.

## Dónde están las bases de datos

| Base | Ubicación y contenido |
|---|---|
| H2 del servidor | Dentro de DebianServer: `/var/lib/billeterax/wallet.mv.db`. Guarda usuarios (`wallet_users`), sesiones (`wallet_sessions`), cuentas (`accounts`), asientos (`ledger_entries`) e IDs de operaciones (`wallet_operations`). Es la fuente de los saldos confirmados. |
| SQLite gestionada por Room | Dentro del Pixel, usuario Android 0: `/data/user/0/ec.edu.espe.billeterax/databases/billeterax.db`. Tablas `balances`, `transactions`, `pending_operations`. Guarda la copia local por servidor/cuenta y comandos cuyo resultado falta verificar. |

La ruta H2 está fijada en `backend/src/main/resources/application-debian.yml`. El backend usa H2 embebido: Java abre ese archivo, y systemd ejecuta el servicio como usuario `billeterax`. El archivo queda dentro del disco virtual de Debian. El JAR es `/opt/billeterax/billeterax.jar`; no es la base de datos.

Para mostrar el servicio y la existencia del archivo, conectar desde PowerShell:

```powershell
ssh root@192.168.18.76
```

Dentro de esa sesión Debian:

```bash
systemctl is-active billeterax
systemctl is-enabled billeterax
ls -lh /var/lib/billeterax/wallet.mv.db
```

Esperado: `active`, `enabled` y el archivo. Esto comprueba servicio/configuración/archivo; los saldos y operaciones se comprueban mediante la app y la API. La consola web H2 está deshabilitada. No hace falta habilitarla para la exposición.

Para ver Room con filas y columnas: abrir la app, ir a Android Studio → View → Tool Windows → App Inspection → Database Inspector, seleccionar `ec.edu.espe.billeterax` y expandir `billeterax.db`. Activar Live updates permite observar los cambios. Fuente: [documentación oficial de Android](https://developer.android.com/studio/inspect/database).

Consultas Room de solo lectura: sustituir CTA_NUMERO_ACTUAL por el número copiado desde la app. Los importes locales se guardan en centavos:

```sql
SELECT ownerName, cents / 100.0 AS saldo, syncedAt
FROM balances
WHERE scope = 'http://192.168.18.76:8080/|CTA_NUMERO_ACTUAL';

SELECT operationId, type, amountCents / 100.0 AS importe, status
FROM transactions
WHERE scope = 'http://192.168.18.76:8080/|CTA_NUMERO_ACTUAL'
ORDER BY occurredAt DESC;

SELECT operationId, route
FROM pending_operations
WHERE scope = 'http://192.168.18.76:8080/|CTA_NUMERO_ACTUAL';
```

La tercera consulta debe mostrar una fila durante una operación pendiente y quedar vacía después de confirmarla/conciliarla. Filtrar por scope evita mezclar pruebas anteriores contra Windows.

## Preparación y comprobación rápida

- Encender DebianServer y el teléfono/Pixel. En acceso verificar URL `http://192.168.18.76:8080/`, entrar como A y esperar WebSocket en línea.
- Anotar el saldo real; los nuevos registros empiezan en cero. No borrar bases para preparar la exposición.
- Desde Windows ejecutar:

```powershell
curl.exe http://192.168.18.76:8080/actuator/health
```

Esperado: `{"status":"UP"}`. Health demuestra que responde el servicio; no reemplaza comprobar movimientos, persistencia y recuperación.

Importar colección y entorno Debian de `postman/`. Completar localmente username/password del usuario A de Android y ejecutar **00B Iniciar sesión**: guarda tokenA/sourceAccount. Crear B con **00C** o entrar con **00D**, usando sus variables terminadas en B. Ejecutar **08B Recargar emisora $20** y después **09 DEMO: B envía $20 a A**. No exportar valores privados. Mantener reuseOperationId=false para operaciones nuevas. No hacen falta credenciales de la configuración SSH ni tokens copiados de Debian.

| Demostración | Acción | Resultado esperado |
|---|---|---|
| Depósito | Depositar $10 desde Android | Saldo anterior + $10, un comprobante nuevo |
| Retiro | Retirar $5 | Saldo anterior − $5, un comprobante nuevo |
| Transferencia | Enviar $2 de A a B | A baja $2 y B sube $2; comprobar ambas cuentas |
| Servicio simulado | Pagar $3, servicio Agua, contrato AGUA-12345 | Saldo baja $3, comprobante con categoría de servicio |
| Validación | Solicitar retiro de $999999 | Rechazo; saldo e historial monetario no cambian |
| REST | Postman `02 Saldo de la cuenta actual`, `07 Historial`, `08 Conciliar` | Datos del servidor coherentes con Android sincronizado |
| Persistencia | Cerrar y reabrir la app; después reiniciar solo servicio | Saldo e historial conservados |

Retiro y servicio ya se probaron contra Windows; ensayarlos contra Debian antes de presentarlos como validados en ese entorno.

## Demostración de recepción

1. Anotar el saldo actual de A y verificar que B disponga al menos de $20. Conservar las bases existentes.
2. Mostrar en Debian `systemctl status billeterax` y `java -version`. Probar health desde Windows.
3. Abrir Android con el usuario A y la URL verificada de Debian. Esperar WebSocket en línea y saldo sincronizado.
4. Importar Postman y seleccionar el entorno Debian con URL y credenciales reales locales.
5. Ejecutar `09 DEMO: B envía $20 a A` una sola vez.
6. Sin tocar Android, observar el saldo previo + $20 y `Transferencia recibida`. Abrir comprobante y comparar UUID con el servidor.
7. Explicar REST para comandos, H2 como autoridad, evento después del commit, snapshot REST, transacción Room y Flow hasta Compose.

## Demostración de desconexión

1. Guardar captura de saldo e historial y un comprobante.
2. Ejecutar `sudo systemctl stop billeterax` en la VM autorizada.
3. Esperar detección de desconexión (ping y timeout; no es instantánea).
4. Mostrar el historial Room y la etiqueta del último saldo sincronizado.
5. Intentar depositar un importe pequeño. Mostrar el error y la operación pendiente; no afirmar éxito.
6. Reiniciar la app sin borrar sus datos. Confirmar que conserva historia y UUID pendiente.
7. Ejecutar `sudo systemctl start billeterax` y esperar reconexión automática.
8. Verificar/reintentar la operación pendiente: conserva su UUID y se procesa a lo sumo una vez.
9. Comparar saldo e historial; repetir sincronización y comprobar que no duplica asientos.
10. Reiniciar la VM de forma controlada y verificar `systemctl is-enabled billeterax`, inicio automático y mismos saldos.

Los pasos 1–9 ya pasaron en la prueba automatizada contra Debian. El paso 10 es una validación pendiente, que conviene ensayar antes de la exposición. Si la app concilia automáticamente el UUID al recuperar conexión, mostrar que desapareció el pendiente y apareció un solo asiento; no crear otro depósito para intentar repetirlo.

## Repetición controlada para demostrar idempotencia

En Postman, elegir `03 Depositar` y un monto pequeño. Con `reuseOperationId=false`, enviar una vez y anotar operationId, saldo y comprobante. Cambiar `reuseOperationId=true` y repetir exactamente la misma solicitud, sin modificar cuenta ni importe. Esperado: `replayed=true`, mismo comprobante, saldo sin otro incremento y un solo asiento. Al terminar devolver `reuseOperationId=false` para solicitudes nuevas.

Frase para explicar: «El identificador representa la intención de pago. Si repito la misma intención, el servidor devuelve su resultado anterior y no vuelve a aplicar el movimiento». Este mecanismo no demuestra por sí solo que ya se haya probado la pérdida de respuesta después del commit: ese escenario continúa pendiente.

## Evidencias que se pueden mostrar

- `docs/evidence/android-debian-instrumentation.log`: `OK (2 tests)` de Room y flujo distribuido actual.
- `docs/evidence/android-debian-verification.json`: estado `passed`, seis comprobaciones y saldo final $122.23.
- Capturas `android-debian-offline.png`, `android-debian-pending.png`, `android-debian-recovered.png`.
- `docs/evidence/debian-deployment.json`: servicio, REST, WebSocket y persistencia tras reiniciar servicio.
- `docs/testing/RESULTADOS.md`: 15 pruebas backend y 2 unitarias Android, además de los casos anteriores, con fechas y alcance.

Para repetir toda la prueba Android–Debian, ejecutar desde PowerShell:

```powershell
& 'C:\Users\matsa\AppData\Local\Programs\Python\Python312\python.exe' 'C:\Proyectos\BilleteraX\deployment\scripts\verify-android-debian.py'
```

Requiere introducir la contraseña de root y dejar libre el emulador. Crea movimientos simulados y detiene temporalmente solo BilleteraX. No es necesario repetirla para utilizar la app.

## Explicaciones para la evaluación

- Room: mostrar Entity, DAO, RoomDatabase, Flow, esquema exportado y prueba de reapertura.
- REST: demostrar las cuatro operaciones y respuesta controlada de fondos insuficientes.
- WebSocket: mostrar handshake autenticado, evento posterior al commit y actualización sin botón.
- Concurrencia: mostrar el orden de bloqueos, `@Transactional`, BigDecimal, UUID persistente y pruebas paralelas.
- Android: mostrar Dispatchers.IO, viewModelScope/Main, Mutex, cancelación y estado reactivo.
- Distribución: apagar el servicio independiente; Android sigue funcionando con Room y recupera conexión.

No presentar pruebas pendientes como aprobadas. Consultar `testing/RESULTADOS.md` y completar evidencias Debian antes de defender. Esta guía técnica no sustituye el informe APA 7 que se redactará después.
