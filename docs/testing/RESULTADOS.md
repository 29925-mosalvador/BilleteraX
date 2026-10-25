# Resultados verificables

Actualizado: 24 de octubre de 2026 (America/Guayaquil). La siguiente tabla corresponde a 1.1.0; las secciones históricas posteriores conservan las evidencias de la versión anterior.

## Compilación real en GitHub Actions y publicación

La ejecución https://github.com/29925-mosalvador/BilleteraX/actions/runs/40153217533 finalizó con success en ambos jobs: backend Java 21 (pruebas y bootJar) y Android (APK, compilación de instrumentación, pruebas unitarias y lint). Commit de código comprobado: 322cdd743ffe5894805da40c70a9ac1aa81fef1b. Las modificaciones posteriores de documentación no cambian Android/backend. No confundir compilar instrumentación en CI con ejecutarla en un dispositivo.

El primer intento de Actions falló porque sdkmanager no estaba en PATH. Se corrigió usando su ruta bajo ANDROID_HOME y se repitió el workflow completo con resultado aprobado. Se conservó el código funcional.

Publicación: https://github.com/29925-mosalvador/BilleteraX/releases/tag/v1.1.0. APK, JAR, instrucciones y SHA256SUMS.txt están uploaded, con tamaños y hashes iguales a los archivos preparados localmente. El APK de Releases mantiene la firma debug local verificada; el APK de CI usa la firma temporal del runner. Evidencia estructurada: docs/evidence/github-publication.json.

## Registro, sesiones y temas: versión 1.1.0

| Comprobación | Resultado real | Evidencia en docs/evidence |
|---|---|---|
| Backend | 22 aprobadas, 1 omitida, 0 fallos | auth-backend-build.log y auth-backend-tests/ |
| Autenticación | 8 aprobadas; registro, login, Unicode, aislamiento, duplicados concurrentes, expiración y revocación de socket | UserAuthenticationTest XML |
| Android unitarias | 2 aprobadas | auth-android-unit-tests/ |
| Instrumentación local | 4 aprobadas: Room, flujo distribuido, usuarios y sesión/tema cifrados | auth-android-instrumentation.log |
| Android contra Debian | 2 aprobadas; usuarios, depósito, transferencia entrante WebSocket y Room | auth-android-debian-instrumentation.log |
| UI real en Pixel | Registro, depósito, login inválido/válido, reapertura, tema y cambio de cuenta aprobados | auth-ui-verification.json, auth-login/wallet/profile PNG |
| Compilación Android y lint | BUILD SUCCESSFUL; 0 errores, 20 avisos de dependencias/KTX/KSP | auth-android-build.log, auth-lint.xml |
| Despliegue Debian final | Respaldo, hash, datos antiguos, REST/WS, registro/login, persistencia tras reinicio y logout aprobados | debian-auth-upgrade.json |

El caso PersistenceRestartTest se omite explícitamente en el aislamiento Windows cuando NIO declara H2 no escribible al reabrir. No equivale a aprobado. La persistencia se verificó en Debian real al reiniciar el servicio, incluyendo cuenta, contraseña y sesión del usuario; la VM completa no se reinició. Respaldo final: `/var/lib/billeterax/upgrade-backup.fwbtsemx`. El hash del JAR final coincide con el desplegado.

Los mensajes con tildes del servidor se corrigieron después de detectarlos en la UI. El caso de login comprueba el texto UTF-8 y el registro conserva un nombre con tilde. El APK usa la firma debug existente y versión 1.1.0/código 2. Las cuentas nuevas no heredan los saldos de CTA001/CTA002. El APK nuevo está probado en emulador; su instalación en el teléfono físico sigue siendo un paso del usuario.

## Evidencias anteriores al 24 de octubre (histórico)

## Backend

`WalletIntegrationTest`: 14 pruebas aprobadas, cero fallos. `PersistenceRestartTest`: una prueba aprobada, cero fallos. Los XML originales se conservan en `docs/evidence/backend-tests/`.

| Caso | Resultado observado |
|---|---|
| Saldo, depósito, retiro y snapshot | Correctos mediante HTTP y servicio transaccional |
| Fondos insuficientes | HTTP 422; sin cambios de saldo ni asientos |
| Transferencia entre cuentas | Débito y crédito consistentes; $100/$50 pasan a $120/$30 |
| Destino inexistente | Rechazo y rollback |
| Servicios y contrato inválido | Pago persistido como Servicio Básico; contrato inválido rechazado |
| Montos inválidos y autotransferencia | HTTP 400 |
| Autorización | HTTP 401/403 al usar credencial inválida o cuenta ajena |
| Reintento de UUID | Mismo comprobante; un solo efecto; contenido distinto rechazado |
| 30 depósitos concurrentes con mismo UUID | Un único depósito de $5 y un asiento |
| 30 retiros concurrentes | Diez retiros de $10, saldo cero y sin sobregiro |
| 40 transferencias en sentidos opuestos | Total conservado, sin deadlock; 80 asientos |
| WebSocket real | Solo la cuenta autorizada recibe el evento; el asiento ya existe al recibirlo |
| Rollback y WebSocket | La operación fallida no genera evento monetario |
| Salud y administración | Health disponible; actuator/env no expuesto |
| Reinicio completo de Spring Boot con H2 en archivo | Saldo, historial e idempotencia se conservan al cerrar y reabrir el servidor |

La prueba de reinicio usa una base temporal exclusiva. Un primer intento encontró un problema de permisos al limpiar el directorio temporal de Windows; se corrigió ubicándolo en `backend/build/tmp/tests`. La ejecución posterior completa pasó.

## Android

`MoneyTest`: dos pruebas unitarias aprobadas. Verifican conversión exacta, suma en centavos, importes negativos de comprobantes y rechazo de entradas inválidas.

Se ejecutaron en un AVD separado Android 17 dos pruebas de instrumentación, ambas aprobadas:

1. `RoomPersistenceTest`: escritura, cierre y reapertura de SQLite; conserva historial, saldo y operación pendiente; no duplica IDs y aísla servidores/cuentas.
2. `DistributedFlowTest`: Retrofit → Spring Boot en Windows → H2 → respuesta → Room; transferencia recibida por WebSocket; conciliación repetida sin duplicados.

`instrumentation.log` contiene `OK (2 tests)`. Esa ejecución inicial precede al último ajuste de cierre/cancelación del ViewModel y a las reglas de backup. La repetición sobre la APK actual contra Debian también pasó; ver el apartado Android–Debian y `android-debian-instrumentation.log`.

## Interfaz realmente utilizada

Se instalaron y abrieron los APK en el emulador. Se operaron los controles reales mediante la jerarquía accesible de Android. No se usó una maqueta.

| Paso | Saldo CTA001 observado |
|---|---:|
| Inicio después de la instrumentación | $121.23 |
| Depositar $10 desde el diálogo | $131.23 |
| Retirar $5 desde el diálogo | $126.23 |
| Transferir $2 desde el diálogo | $124.23 |
| Pagar agua $3, contrato AGUA-12345 | $121.23 |
| Intentar retirar $999999 | Rechazo por fondos insuficientes |
| Transferencia externa de $20 desde CTA002 | $141.23 sin pulsar Actualizar |

Ver `ui-smoke.json` y `android-online.png`. La captura fue inspeccionada: tarjeta de saldo, estado en línea, cuatro acciones, comprobantes y colores de historial visibles. La revisión puntual de AndroidRuntime no mostró errores en esa ejecución; esto no sustituye una campaña prolongada de rendimiento o ANR.

## Compilación y revisión final

El proyecto incluye wrappers, JAR y APK. La comprobación Android final ejecutó las 90 tareas solicitadas, sin reutilizar resultados de tareas: `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --rerun-tasks`. Terminó con BUILD SUCCESSFUL; lint reporta 0 errores y 17 avisos: 15 de nuevas versiones, uno de migración opcional a KSP y uno de uso de KTX. Los logs finales y el informe lint se entregan en `docs/evidence/`. Se corrigieron el escape de la ruta SDK en local.properties y las reglas explícitas de transferencia/backup de Android anteriores y posteriores a Android 12. Se mantuvieron las versiones compatibles fijadas; no se ocultaron los avisos. La compilación del APK de pruebas no implica una nueva ejecución en dispositivo.

También se analizaron sintácticamente los scripts PowerShell. Se inició `start-local.ps1 -FreshDemo` en el puerto 8082 y se consultó por HTTP el saldo inicial de CTA001: $100.00. La base se creó en una ruta nueva, sin borrar datos anteriores. El despliegue Debian se ejecutó posteriormente y está documentado abajo.

Se corrigió además una exclusión excesiva de Git: `**/data/` ocultaba el paquete Android con Room y Repository. La regla se acotó a `backend/data/` y se verificó que las fuentes Android aparecen como archivos disponibles para versionar.

## Despliegue real Debian del 17 de octubre de 2026

`debian-deployment-before-auth.json` registra la ejecución del instalador desde el PowerShell del usuario: Debian 13.7, Java 21.0.12.1, IP manual 192.168.18.76/24 por puente. El servicio quedó activo/habilitado bajo el usuario billeterax. El JAR transferido conserva su SHA-256 y el script POSIX pasó `sh -n` en Debian.

Se comprobaron salud HTTP desde Windows, snapshots autenticados CTA001/CTA002, rechazo 401 sin credencial, conexión WebSocket autenticada y persistencia de saldos/historial al reiniciar el servicio. El agente volvió a comprobar salud 200/UP, saldo sin credencial 401 y actuator/env 404. El reinicio completo de la VM todavía no se realizó.

Se adaptó DistributedFlowTest para recibir serverUrl/tokenA/tokenB por argumentos, y opcionalmente guardar la conexión de la app mediante Keystore después de aprobar. La compilación `assembleDebugAndroidTest` pasó: 56 tareas, 5 ejecutadas y 51 al día; log `android-debian-test-build.log`. El verificador `deployment/scripts/verify-android-debian.py` completó después la instrumentación y la prueba UI de caída/reapertura/reintento.

## Corrección de firma Android del 17 de octubre

El primer intento contra Debian falló al instalar el APK (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), antes de ejecutar pruebas o detener el backend. El APK instalado y el de instrumentación coincidían con la clave debug de Android Studio; el APK principal anterior estaba firmado con la clave aislada de `.tools/android-home`.

Se corrigió el directorio de firma predeterminado de `build.ps1` y se recompilaron ambos APK con la clave existente del usuario. Compilación correcta (74 tareas, 3 ejecutadas) e instalación `-r` de ambos APK con `Success`. Se compararon SHA-256 de la base Room y WAL antes/después: idénticos. Evidencias: `android-signing-rebuild.log`, `android-signing-fix.json` y resultado fallido original `android-debian-signature-failure.json`. La repetición siguiente pasó.

## Android–Debian y recuperación: aprobado

El usuario ejecutó el verificador el 17 de octubre de 2026 a las 21:44–21:45 de Guayaquil. Se inspeccionaron el JSON, log y capturas reales. El JSON registra UTC del 18 de octubre; las capturas también muestran la zona UTC del emulador.

| Comprobación | Resultado y evidencia |
|---|---|
| Room y flujo distribuido contra Debian | `android-debian-instrumentation.log`: `OK (2 tests)`, 1.259 s; Retrofit, recibo, Room y transferencia entrante por WebSocket |
| Reapertura sin servidor | `android-debian-offline.png`: último saldo $121.23 e historial conservados, estado reconectando |
| UUID pendiente tras cerrar Android | `android-debian-pending.png`: `5b60497d-4dfc-4e64-8465-15418b248fb3`; reapertura conservó el mismo UUID |
| Reintento al recuperar Debian | Un depósito de $1.00, un único asiento; replay del UUID respondió `replayed=true` y no cambió saldo/version |
| Estado final | `android-debian-recovered.png`: WebSocket en línea, CTA001 $122.23, tres movimientos; JSON registra versión 3 |
| Salud posterior | Consulta adicional del agente: HTTP 200, `status=UP`; app en ejecución |

`android-debian-verification.json` tiene estado `passed` y las seis comprobaciones verdaderas. La conexión de la app quedó guardada mediante Android Keystore. Las capturas offline, pendiente y recuperada se revisaron visualmente. Esta prueba detuvo/reinició únicamente el servicio y creó movimientos simulados; no demuestra todavía el reinicio completo de Debian ni la pérdida de una respuesta después del commit.

## Pruebas que siguen pendientes

| Prueba | Motivo y procedimiento |
|---|---|
| Perder respuesta monetaria después del commit | La caída antes del envío y su recuperación ya pasaron; falta impedir la respuesta de una operación confirmada y comprobar conciliación/reintento |
| Sincronizar movimientos perdidos durante una caída | Código de conciliación implementado; falta demostrar el escenario completo de caída |
| Retiro y servicios desde UI contra Debian | Ambos formularios pasaron contra Windows; en Debian se verificaron depósito y transferencia |
| Apagado y reinicio completo de Debian | systemd y reinicio del servicio ya verificados; falta reiniciar la VM |
| Rendimiento sostenido y ANR | Falta una sesión prolongada con Android Studio Profiler/StrictMode |
| Publicación del repositorio académico | Repositorio Git local inicializado; sin remoto configurado ni publicación realizada |

No asignar una nota de la rúbrica a partir de este archivo. La defensa puede usar las evidencias Debian aprobadas y debe distinguir los casos pendientes.

## Banca web — 25 de octubre de 2026

Suite backend ampliada: 30 pruebas aprobadas y 1 omitida (PersistenceRestartTest bajo el aislamiento Windows ya documentado). Cliente web: 6 pruebas aprobadas, incluidos céntimos exactos, validación de destino, sesión en cabeceras, contenido mixto y UUID pendiente aislado por cuenta/servidor.

Navegador real: registro/login/logout, contraseña inválida, cuatro operaciones, fondos insuficientes, transferencia entrante automática y búsqueda. Cuenta local temporal: saldo final $18.00, 5 movimientos; temas claro/oscuro y diseño escritorio/teléfono sin desbordamiento horizontal. La recarga conserva sesión/tema. Evidencia: `docs/evidence/web-verification.json`, `web-light.jpg`, `web-dark-mobile.jpg`.

Debian actualizado con 11 comprobaciones aprobadas: datos previos conservados, persistencia tras reiniciar servicio, página web, preflight CORS y pase WebSocket de un solo uso. Evidencia fija `docs/evidence/debian-web-upgrade.json`. Verificación pública de Pages/túnel pendiente hasta registrar evidencia adicional. No se reemplaza el cliente Android ni Room.

Prueba pública completada: GitHub Pages → HTTPS/Cloudflare → Debian y WSS autenticado, registro/login/logout, recarga con datos conservados y las cuatro operaciones. Dos cuentas de laboratorio nuevas; cuenta principal final $2.00 y cinco movimientos, con transferencia entrante visible sin actualizar. CI backend y Android aprobados en la ejecución 40165182591; Pages aprobado en 40165182589. Evidencia: `web-public-verification.json` y `web-public.jpg`.

Se corrigió además la vista al cambiar de cuenta: durante la carga no muestra totales ni recuentos anteriores. Se comprobó localmente con un proxy que demora cuatro segundos el snapshot; aparecen indicadores de carga y luego saldo cero/historial vacío para la segunda cuenta. Esta corrección del cliente requiere publicar el archivo web y actualizar la copia incluida en el JAR de Debian.
