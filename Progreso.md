# Progreso de BilleteraX

Actualizado: 24 de octubre de 2026, BilleteraX 1.1.0 con registro/login y temas comprobados; backend final desplegado en Debian. Zona America/Guayaquil.

## Continuidad

Proyecto existente en `C:\Proyectos\BilleteraX`. Continuar editando los archivos actuales; no regenerar componentes mediante los scripts iniciales de la conversación. Arquitectura acordada: Android/Kotlin con Room → REST/WebSocket → Spring Boot/Java 21 y H2 persistente, destinado a Debian Server en VirtualBox.

## Implementación anterior al registro/login (histórico)

- Backend: depósitos, retiros, transferencias atómicas, pagos simulados, consultas y snapshot de conciliación; validaciones, BigDecimal, bloqueos ordenados e idempotencia persistente.
- Autorización por cuenta para REST y WebSocket; eventos después del commit.
- 15 pruebas backend aprobadas: 14 de integración y una de cierre/reinicio real con H2 sobre archivo.
- Android: Compose/Material 3, MVVM, Retrofit, Room, corrutinas IO/Main, StateFlow, reconexión y configuración centralizada.
- APK debug y APK de instrumentación compilados; 2 pruebas unitarias monetarias aprobadas. Consultar `docs/evidence/android-build.log` para la compilación entregada.
- 2 pruebas aprobadas en emulador: reapertura de Room y Retrofit → backend → Room con transferencia entrante WebSocket.
- UI: los cuatro formularios funcionaron; se verificaron fondos insuficientes y actualización automática por transferencia externa.
- Colección Postman, entornos Windows/Debian, scripts, unidad systemd y documentación técnica creados.
- Debian 13.7: backend activo/habilitado con Java 21; Android configurado para `http://192.168.18.76:8080/`. Instrumentación actual: 2 pruebas aprobadas. Caída del servicio, reapertura offline, UUID persistente y recuperación sin duplicados aprobados; saldo CTA001 al finalizar: $122.23.

## Correcciones de la sesión reanudada

- Se revisaron los archivos existentes y el historial; se conservaron los componentes funcionales.
- Se verificaron los últimos ajustes de cancelación y cierre de WalletViewModel mediante compilación y pruebas unitarias.
- Se corrigió `start-local.ps1`: normalización de la ruta H2. Se inició el backend con una base nueva en el puerto 8082 y respondió saldo CTA001 = 100.00 por HTTP.
- Se corrigió el escape de la ruta SDK en `local.properties` y se añadieron reglas de backup para Android anterior y posterior a Android 12.
- Se corrigió `.gitignore`: `**/data/` excluía también el código de Room; ahora solo se excluye `backend/data/`. Se comprobó que las tres clases del paquete Android `data` están disponibles para Git.
- Se documentaron los resultados y la relación con los cinco criterios del profesor en `docs/testing/`.
- La comprobación final ejecutó las 90 tareas de compilación Android, pruebas unitarias e instrumentación compilada y lint: BUILD SUCCESSFUL; 0 errores y 17 avisos de nuevas versiones/KSP/KTX. Compilar las pruebas de instrumentación no equivale a ejecutarlas en un dispositivo.
- Se consolidaron evidencias y entregables en `C:\Users\matsa\Documents\Codex\2026-10-16\files-mentioned-by-the-user-proyecto\outputs`: código ZIP, APK debug, JAR, copia de este progreso, guía y hashes SHA-256.

## Actualización del 24 de octubre completada

Se añadieron registro/login con usuario y contraseña, sin correo. Cada registro genera una cuenta propia de saldo cero. Usuarios y sesiones están en la misma H2 de Debian (`wallet_users`, `wallet_sessions`); contraseñas BCrypt y sesión guardada como hash SHA-256. Android cifra el token con Keystore y no guarda la contraseña. Logout revoca esa sesión y cierra su WebSocket; otras sesiones permanecen activas. CTA001/CTA002 y su historial se conservaron.

UI nueva: acceso, perfil, copiar cuenta, navegación Inicio/Perfil, comprobantes y dashboard renovados; temas Sistema/Claro/Oscuro persistentes. Room conserva schema 1, historial y operaciones pendientes por servidor/cuenta. APK 1.1.0, código 2, firma debug del usuario compatible; instalación -r sin borrar datos.

Pruebas actuales: backend 23 casos, 22 aprobados y uno omitido explícitamente por NIO de Windows aislado al reabrir H2; no se cuenta como aprobado. Los ocho de autenticación pasaron, incluidos concurrencia, aislamiento, expiración y revocación WebSocket. Android: dos unitarias, cuatro de instrumentación local y dos de usuarios/transferencia contra Debian aprobadas. Lint: cero errores y 20 avisos de versiones/KSP/KTX. UI real aprobada: registro con saldo cero, depósito, contraseña incorrecta, login a la misma cuenta, sesión/tema tras reiniciar proceso, cierre y cambio entre dos usuarios sin mezclar saldos.

El usuario ejecutó el instalador desde PowerShell por la restricción automática de entrada de contraseña SSH. La primera actualización pasó; se detectaron caracteres dañados en mensajes de acceso, se corrigieron y se comprobó de nuevo el JAR final. Evidencia aprobada: `docs/evidence/debian-auth-upgrade.json`, hash `29c87b1fac278986eee8cda9dab79a1a31a49926140f1f295de377b31189ce21`. Respaldo antes de la actualización final: `/var/lib/billeterax/upgrade-backup.fwbtsemx`. Incluye H2, JAR y entorno; no se borró ninguna base. El instalador verifica conservación de cuentas/historial y persistencia de usuarios, contraseña y sesión al reiniciar el servicio; logout rechaza la sesión revocada.

Evidencia: `auth-release-verification.json`, `auth-backend-tests/`, `auth-android-unit-tests/`, `auth-android-instrumentation.log`, `auth-android-debian-instrumentation.log`, `auth-ui-verification.json` y capturas `auth-*.png`. Postman y guías actualizados para registro/login. `verify-android-debian.py` corresponde a la UI antigua; no usarlo para la pantalla de acceso 1.1.0.

Se entregaron APK, fuentes, JAR, despliegue, guías y hashes actualizados en la carpeta outputs de este chat. La versión anterior funcionó en el teléfono según el usuario; el APK 1.1.0 todavía debe instalarse y comprobarse allí. El emulador quedó listo para acceder a Debian sin introducir tokens manuales.

## Siguiente tarea pendiente

1. Probar pérdida de respuesta después del commit: confirmar primero el asiento en Debian, impedir que Android reciba la respuesta y comprobar conciliación/reintento sin duplicados. La caída antes del envío y recuperación ya están demostradas; no sustituyen este caso.
2. Verificar reinicio completo de la VM e inicio automático con los mismos saldos/historial. El reinicio del servicio Debian ya se probó; no equivale a reiniciar el sistema operativo.
3. Completar movimientos recibidos durante desconexión y los formularios de retiro/servicios contra Debian (ya probados contra Windows).
4. Completar perfilado prolongado. El repositorio académico ya está publicado en GitHub. No redactar todavía el informe APA 7.

El bloqueo histórico de ADB y el error de firma quedaron superados. El Pixel del usuario ejecutó correctamente la instrumentación actual y la recuperación ante caída del servicio Debian.

## Debian: estado actual, red puente y SSH

El usuario cambió NAT por adaptador puente. El instalador confirmó `enp0s3`, dirección manual `192.168.18.76/24`, gateway `192.168.18.1` y conexión NetworkManager `Wired connection 1`. La reserva/exclusión de esa dirección en DHCP del router no se inspeccionó.

Se confirmó desde Windows el banner TCP `SSH-2.0-OpenSSH_10.0p2 Debian-7+deb13u4` en `192.168.18.76:22`. El archivo known_hosts del usuario ya contiene claves para esa dirección. El usuario autorizó conectarse como root mediante contraseña. No almacenar esa contraseña. No modificar la configuración SSH ya elegida por el usuario.

El intento de introducir la contraseña mediante `write_stdin` fue rechazado por el control automático: `approval required by policy, but AskForApproval::Granular.sandbox_approval is false`. Se concedió acceso de red/escritura con alcance de sesión, pero el rechazo persistió incluso con una nueva sesión. Las sesiones locales 92979 y 75242 quedaron esperando entrada; no se autenticaron. El usuario ejecutó el instalador preparado en su propio PowerShell y confirmó que terminó. **El despliegue sí se realizó mediante esa ejecución y quedó comprobado.**

Se preparó `deployment/scripts/deploy-debian.py` para ejecutarlo desde el PowerShell del usuario. Pide contraseña de forma interactiva, valida las claves SSH existentes, inspecciona Debian/IP/puertos, instala Java 21/curl, transfiere y compara SHA-256 del JAR, reutiliza el instalador existente, genera credenciales en Debian y conserva las existentes. Comprueba systemd, salud HTTP, REST autorizado/401, handshake WebSocket y snapshots antes/después de reiniciar solo el servicio. Guarda resultados sin credenciales en `docs/evidence/debian-deployment.json`. No reinicia la VM ni cambia SSH, red o firewall.

`docs/evidence/debian-deployment-before-auth.json` registra el despliegue inicial `passed`: Debian 13.7, Java 21.0.12.1, 13 GB disponibles en `/`, servicio habilitado/activo bajo usuario `billeterax`, JAR recibido con hash correcto y script validado con `sh -n`. Se comprobaron HTTP UP, snapshots de ambas cuentas, 401 sin credencial, handshake WebSocket y saldos/historial conservados tras reiniciar el servicio. CTA001 = 100.00 y CTA002 = 50.00 al finalizar ese despliegue. Directorio de transferencia: `/var/tmp/billeterax-deploy.jAYJqMM4`. El agente comprobó por HTTP adicionalmente salud 200/UP, saldo sin credencial 401 y actuator/env 404.

Ruta actual: Windows y emulador → `http://192.168.18.76:8080/` verificada. El plan NAT 2222/18080 de más abajo es histórico.

## Pruebas Android preparadas después del despliegue

`DistributedFlowTest` acepta `serverUrl`, `tokenA` y `tokenB` mediante argumentos de instrumentación. Las credenciales demo solo son predeterminadas para la URL dev original; otros servidores requieren tokens explícitos. El argumento opcional `configureApp=true` guarda la conexión mediante ConfigStore/Keystore después de aprobar la prueba. No se añadieron tokens reales al código.

Compilación inicial de este ajuste: `:app:assembleDebugAndroidTest`, BUILD SUCCESSFUL, 56 tareas (5 ejecutadas/51 al día), log `docs/evidence/android-debian-test-build.log`. Después se recompilaron ambos APK para corregir la firma y el usuario ejecutó `verify-android-debian.py` con éxito. Las pruebas generan movimientos simulados y conservan evidencias.

Al continuar, ADB no detectó dispositivos. El arranque por terminal del agente falló por acceso a aceleración de Windows, pese a detectar Hyper-V/WHPX. Se abrió Android Studio mediante la habilidad computer-use y se seleccionó Pixel 10 Pro XL desde Running Devices; terminó de arrancar y ADB fue utilizable. No se alteraron opciones de seguridad o virtualización de Windows ni se borraron datos.

## Firma Android corregida el 17 de octubre

El verificador falló con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` antes de ejecutar instrumentación o detener el servicio. Se conservó ese resultado en `docs/evidence/android-debian-signature-failure.json`.

Se extrajo el APK instalado en el Pixel y se compararon certificados con apksigner: la app instalada y el APK de pruebas usan la clave debug del usuario (SHA-256 `82dd2604bc2b101f8a038f6db8939d04d5dba055acb565b342221d7fab1ce0fa`); el APK principal anterior usaba la clave aislada del proyecto (`f7d60f9662570ab427802056fe71c595b0b850067ce0fe05454e208e404ad4f9`). La causa estaba en `build.ps1`, que forzaba `.tools/android-home`.

Se corrigió `build.ps1`: por defecto comparte `%USERPROFILE%\.android` con Android Studio, admite `-AndroidUserHome` explícito y compila también el APK de pruebas. `assembleDebug assembleDebugAndroidTest` terminó correctamente (74 tareas; 3 ejecutadas/71 al día). Ambos APK se instalaron con `adb install -r`, resultado `Success`. Los SHA-256 de `billeterax.db` y su WAL fueron idénticos antes/después: datos conservados. No se desinstaló la app ni se alteró código funcional.

El verificador instala ahora los APK antes de solicitar la contraseña SSH para detectar problemas locales primero. Sintaxis Python/PowerShell y `--check-local` aprobados. Evidencias: `android-signing-rebuild.log` y `android-signing-fix.json`. La repetición del usuario terminó correctamente, como se detalla a continuación.

## Android–Debian: verificación completada el 17 de octubre

Se revisaron `android-debian-verification.json` (estado `passed`, seis comprobaciones verdaderas), `android-debian-instrumentation.log` (`OK (2 tests)`, 1.259 s) y las capturas offline, pendiente y recuperada. La fecha del JSON está en UTC: inicio 18 de octubre 02:44:57, equivalente al 17 de octubre 21:44:57 en Guayaquil. El emulador también mostraba UTC; no es una ejecución de otro día local.

- Room y flujo Retrofit → Debian → Room con transferencia entrante por WebSocket: aprobados sobre la APK actual.
- Servicio detenido y app cerrada/reabierta: conservó saldo $121.23 e historial; UI indicó último saldo sincronizado y reconexión.
- Depósito de prueba de $1.00 sin servidor: quedó pendiente con UUID `5b60497d-4dfc-4e64-8465-15418b248fb3`, conservado tras otra reapertura.
- Servicio recuperado y reintento: un único asiento; repetir la misma solicitud devolvió `replayed=true` sin modificar saldo/version.
- Pantalla final: WebSocket en línea, CTA001 $122.23, versión 3 y tres movimientos. Captura `android-debian-recovered.png` revisada visualmente.
- El agente volvió a consultar health después: HTTP 200 y `status=UP`; la app seguía ejecutándose.

La conexión de CTA001 quedó guardada por ConfigStore/Android Keystore. Para usarla, mantener Debian encendido y abrir BilleteraX en el Pixel. No hace falta repetir el verificador para el uso diario; repetirlo genera nuevos movimientos simulados. No se reinició la VM ni se simuló aún pérdida de respuesta posterior al commit.

## Debian: preparación inicial del 17 de octubre (histórico NAT)

- VirtualBox 7.2.20 está instalado en `C:\Program Files\Oracle\VirtualBox`. Se identificó la VM registrada `DebianServer`, UUID `b7647cd7-25fe-41bb-badf-31aecc7423c7`, y se observó encendida con escritorio GNOME. La configuración declara Debian 13 de 64 bits, 8 GB RAM, 8 vCPU y disco virtual de 20 GB; confirmar versión y espacio dentro del invitado al conectar.
- El usuario indicó la cuenta `vboxuser`. No guardar su contraseña en archivos del proyecto, scripts, entregables ni evidencias.
- La VM tiene un adaptador NAT. Existe un adaptador Host-Only de Windows con 192.168.56.1, pero no está conectado a esta VM. Se eligió conservar NAT y añadir reenvíos locales: Windows 127.0.0.1:2222 → Debian:22 y Windows 127.0.0.1:18080 → Debian:8080. Android usará `http://10.0.2.2:18080/` después de verificar la ruta.
- **Pendiente de configurar/verificar:** en la última lectura del archivo `.vbox` no había reglas de reenvío. No se obtuvo conexión SSH; el backend aún no se instaló ni se inició en Debian.
- El controlador CLI de VirtualBox devuelve `E_ACCESSDENIED` desde el terminal aislado del agente. Se abrió la configuración mediante la interfaz gráfica, pero la ventana mostró «No responde» y el clic en reenvío de puertos agotó su tiempo. No se apagó ni se reinició forzosamente la VM.
- Se pidió al usuario instalar/activar `openssh-server` en Debian y crear los dos reenvíos mediante su PowerShell. Los comandos concretos están en `deployment/debian/README.md`. Esperar su resultado y comprobar conectividad antes de enviar archivos.
- Se corrigieron finales de línea CRLF a LF en `deployment/debian/install.sh`, la unidad systemd y ambos wrappers POSIX de Gradle. `.gitattributes` conserva LF para esos tipos de archivo. No se cambió código funcional Android ni backend.
- Se adaptó `check-network.ps1` para admitir puertos SSH/API configurables, conservando 22/8080 por defecto; análisis de sintaxis PowerShell aprobado. La plantilla de entorno ahora solicita una IP Debian verificada, compatible con NAT o Host-Only.
- Se actualizaron las guías para la VM existente y la topología elegida. El chequeo `sh -n` todavía no se ejecutó: el runtime Git Bash está bloqueado por el aislamiento de Windows. Validarlo dentro de Debian antes de instalar.
- Se preparó Paramiko localmente en `.tools/python-debian` para el acceso SSH; importación verificada. Esa carpeta está excluida de Git y de los entregables. La instalación inicial falló por permisos del temporal y se completó usando `.tools/pip-temp`.
- Se actualizó el ZIP de fuentes (93 archivos) y se creó `outputs/BilleteraX-Debian.tar.gz` con el JAR existente y la carpeta `deployment`, sin credenciales reales. Se comprobó integridad del ZIP, igualdad SHA-256 del JAR empaquetado y ausencia de CRLF en los cuatro archivos POSIX corregidos. Se actualizaron la guía, la copia de progreso y los hashes de entrega. Estas comprobaciones no sustituyen una ejecución en Linux.

Al conseguir acceso autenticado: verificar `/etc/os-release`, la IP puente y el perfil NetworkManager, rutas, espacio y Java; desplegar con `SERVER_ADDRESS=192.168.18.76` solo después de confirmar que la IP pertenece a Debian. Después probar salud, REST, WebSocket, persistencia al reiniciar servicio/VM y la conexión Android. No marcar esos pasos como realizados hasta guardar evidencia.

## Configuración de Android Studio solicitada después

El 16 de octubre de 2026, el usuario pidió configurar su equipo tras el error `Couldn't terminate previous instance of app`. Se revisaron los logs del IDE y del dispositivo. El SDK del proyecto ahora apunta al SDK original del usuario, y Android Studio usa su Java 21 de `D:\AndroidStudio\jbr`; ambos fueron verificados.

Con esta autorización se respaldó y ajustó únicamente `Pixel_10_Pro_XL`: gráficos `software` y arranque en frío. Había fallos del renderizado de System UI/lanzador y restauraciones repetidas de snapshot. Tras el nuevo arranque se confirmó SwiftShader y se ejecutaron dos cierres/reaperturas correctos de BilleteraX por ADB. La pantalla mostró saldo $100.00 y WebSocket en línea contra el backend Windows del puerto 8080. No se borraron datos del emulador.

Se añadió `deployment/scripts/restart-android-app.ps1` y la guía `docs/ANDROID_STUDIO.md`. Las evidencias son `docs/evidence/android-studio-verification.json` y `docs/evidence/android-studio-online.png`. El botón Run del IDE no fue automatizado: se verificó su ruta equivalente de cierre/arranque por ADB y la detección de los procesos por Android Studio. Repetir instrumentación sobre la APK final sigue siendo la siguiente validación de desarrollo.

## Evidencias existentes

- `backend/build/test-results/test/`: XML de 15 pruebas aprobadas.
- `android/app/build/test-results/testDebugUnitTest/`: XML de 2 pruebas aprobadas.
- `docs/evidence/android-online.png`: captura real de UI.
- Carpeta de trabajo: `C:\Users\matsa\Documents\Codex\2026-10-16\files-mentioned-by-the-user-proyecto\work`.
- `docs/evidence/`: logs de compilación, XML de pruebas, informe lint, `instrumentation.log` con OK (2 tests), `ui-smoke.json` con las cuatro operaciones y recepción automática, y captura real. La compilación final reemplaza el log fallido del intento interrumpido.
- `docs/testing/RESULTADOS.md`: explica qué versión cubre cada prueba y qué escenarios no se han demostrado.

## Herramientas y datos

- JDK 21 portable: `.tools/jdk21`.
- Android Studio usa ahora el SDK del usuario `C:\Users\matsa\AppData\Local\Android\Sdk`. La copia `.tools/sdk` permanece disponible para las tareas aisladas anteriores.
- Gradle user home: `.tools/gradle-home`; wrappers Gradle 8.13 en ambos proyectos.
- H2 de pruebas: `.tools/runtime/wallet.mv.db`; CTA001 terminó la prueba UI con 141.23 USD.
- AVD separado anterior: `work/avd/BilleteraX_Test.avd`, puertos 5580/5581. Para Android Studio se recuperó Pixel_10_Pro_XL con autorización del usuario; conservar su configuración verificada y datos.
- ADB nativo tuvo problemas al resolver el perfil Windows. `work/adb_direct.py` utiliza adb-shell para conectarse únicamente al emulador local con la clave de desarrollo existente, sin mostrarla.
- Comprobar procesos antes de iniciarlos o detenerlos; no suponer que continúan activos tras una interrupción.
- La autorización de escritura sobre el proyecto y `.android` se renovó con alcance de sesión al reanudar.

## Límites

El backend Debian, la integración Android, Room offline y recuperación con UUID sin duplicados están verificados. Siguen pendientes pérdida de respuesta posterior al commit, reinicio completo de VM y demás casos indicados arriba. No redactar todavía el informe APA 7. No publicar código o credenciales reales en servicios externos sin autorización. El cumplimiento de la rúbrica debe referenciar pruebas concretas, sin prometer una calificación.

## Cómo retomar

El usuario confirmó que en su teléfono físico funcionaron los pasos guiados: acceso a Debian, instalación/configuración, depósito, reapertura, consulta sin conexión y recuperación. Es una confirmación manual del usuario; todavía no se guardaron capturas ni instrumentación del teléfono. Siguiente demostración en ese dispositivo: transferencia CTA002 → CTA001 desde Windows y actualización automática con la app abierta, sin pulsar Actualizar.

Se amplió `docs/DEFENSA.md` a petición del usuario: guion de exposición, ubicación de H2/Room, consultas de solo lectura, comandos de comprobación, secuencia de demostración y resultados esperados. La revisión de esta guía no ejecutó nuevas operaciones ni modificó bases; conserva como pendientes los casos todavía no verificados.

Leer este archivo, `docs/testing/RESULTADOS.md` y `docs/testing/RUBRICA.md`; comprobar procesos, archivos y cambios antes de actuar. Continuar con la primera validación pendiente que permita el entorno. No ejecutar los generadores iniciales de código ni recrear la arquitectura. Actualizar este archivo después de cada avance o cambio de bloqueo.

## Publicación GitHub del 24 de octubre completada

Se revisó PROYECTO FINAL DE LA UC 1 (5).docx: pide enlace al proyecto Android Studio, APK y defensa móvil/Postman con WebSocket; no exige una app web. El usuario confirmó cuenta 29925-mosalvador y visibilidad pública. Se creó el repositorio vacío https://github.com/29925-mosalvador/BilleteraX desde la sesión iniciada por el usuario.

Se añadió workflow de compilación/pruebas Java 21 y Android, validado con actionlint. Se ampliaron exclusiones de bases locales, claves, binarios y caché Python. GitHub Pages no ejecutará Spring Boot ni APK; Debian conserva backend/H2. El APK de Releases es el archivo local probado con firma compatible. El APK de CI tiene firma temporal del runner; no usarlo para reemplazar la instalación conservando datos.

Publicación verificada: fuentes públicas en https://github.com/29925-mosalvador/BilleteraX; primer commit 7e54962. El usuario completó el push desde PowerShell porque el entorno aislado bloqueó Git Credential Manager/Git Bash. Se corrigió después la ruta de sdkmanager en el workflow mediante GitHub y se sincronizó el checkout local sin reescribir historia.

GitHub Actions aprobó backend Java 21 y Android (compilación de app y pruebas, unitarias y lint) en https://github.com/29925-mosalvador/BilleteraX/actions/runs/40153217533, commit 322cdd743ffe5894805da40c70a9ac1aa81fef1b. El primer intento falló por sdkmanager fuera del PATH; la corrección usa su ruta bajo ANDROID_HOME. Las pruebas de instrumentación se compilan en CI; las ejecuciones contra Debian son evidencias separadas.

Versión pública: https://github.com/29925-mosalvador/BilleteraX/releases/tag/v1.1.0. Incluye BilleteraX-1.1.0.apk, billeterax-1.1.0.jar, INSTRUCCIONES.md y SHA256SUMS.txt. Se comprobaron tamaños y hashes de los cuatro archivos publicados. No se subieron bases reales, contraseñas ni claves privadas. El APK de la versión conserva SHA-256 a6e0cee82b4b45babbc8ae0ceeb182f2e7aaad341c18228c5596191cdf34404b.

GitHub distribuye, compila y prueba; Android ejecuta la app y Debian mantiene Spring Boot/H2. No se abrió el backend a Internet ni se cambió la arquitectura. Siguientes pruebas académicas pendientes: reinicio completo de VM, respuesta perdida después del commit, eventos durante desconexión y perfilado prolongado; informe APA 7 sigue pendiente por solicitud previa.

## Banca web — ampliación del 24 de octubre

El usuario pidió acceso web a las mismas cuentas y probar desde GitHub; eligió preparar un túnel HTTPS de demostración. Se añadió `web/dist` como cliente estático sin dependencias, empaquetado también dentro del JAR por `processResources`. No se recrearon usuarios, cuentas ni bases. Android y Room conservan su arquitectura.

Incluye registro/login sin correo, saldo, historial/búsqueda/filtros, confirmación de depósito/retiro/transferencia/servicios, perfil/logout, modo claro/oscuro y diseño adaptable. El navegador guarda la sesión solo en sessionStorage y conserva el UUID de una operación incierta hasta comprobarla; los reintentos son explícitos.

Backend: CORS con orígenes exactos y preflight antes de autenticación; respuestas de cuentas sin caché; pase WebSocket de 30 segundos, un solo uso y ligado a sesión activa. Se conserva la autenticación por cabecera que usa Android. Nuevas pruebas: 6 integraciones web + 2 de caducidad/concurrencia. Suite total: 30 aprobadas y 1 omitida por la limitación Windows ya documentada; no contar la omitida como aprobada. Cliente web: 6 pruebas aprobadas.

Revisión real del navegador con backend local en memoria, separado de Debian: registro saldo cero, depósito 20, rechazo retiro 200, retiro 3, servicio agua 2, transferencia entrante 4 y saliente 1: saldo 18 e historial de 5 movimientos. Sesión y tema conservados al recargar; búsqueda de Agua devuelve un movimiento. No son movimientos sobre la base Debian del usuario.

Se prepararon `.github/workflows/pages.yml` y `deployment/scripts/start-web-tunnel.py`, con descarga oficial cloudflared 2026.10.3 verificada por SHA-256. El instalador Debian comprueba también página web, CORS de Pages y pase WebSocket no reutilizable. Guía: `docs/WEB.md`.

Pendiente al registrar este avance: completar revisión final de acceso/cierre de sesión, guardar commit y subirlo; activar/verificar GitHub Pages; actualizar JAR en Debian mediante el instalador; iniciar túnel y comprobar HTTPS/WSS reales hacia Debian. No anunciar acceso público ni despliegue Debian hasta tener evidencia. El APK publicado v1.1.0 no cambia.

### Continuación del 25 de octubre — revisión y despliegue Debian

Revisión final real aprobada: logout vuelve a Iniciar sesión, contraseña errónea rechazada y login posterior correcto; se repitieron las cuatro operaciones con una cuenta temporal local, transferencia entrante por WebSocket sin actualizar manualmente, saldo final 18 y 5 movimientos. Se verificó escritorio y teléfono (390 px), sin desbordamiento horizontal del documento, y persistencia de sesión/tema al recargar. Capturas `web-light.jpg` y `web-dark-mobile.jpg`; reporte `web-verification.json`.

El usuario ejecutó el instalador actualizado. `debian-web-upgrade.json` confirma 11 comprobaciones aprobadas, JAR SHA-256 2f2635d81599413a145eafe426db5ce08374ba68e4e3b8c8e87cfdcf748bbb58 y respaldo `/var/lib/billeterax/upgrade-backup.rzpqzBas`. Se conservan CTA001/CTA002 e historial; registro/login/sesión sobreviven al reinicio. Página, CORS de Pages y WebSocket de un solo uso verificados en Debian.

Se activó GitHub Pages con Source: GitHub Actions y HTTPS obligatorio. La integración GitHub ya permite publicar directamente; no pedir otro push manual si sigue disponible. Pendiente: publicar el commit, esperar el workflow y verificar el enlace público; el usuario está iniciando el túnel desde su PowerShell para controlar su duración.

### Banca web publicada y verificada — 25 de octubre

Código publicado directamente con la integración GitHub en el commit f1f993057872fd5096e0cdccf4817bef029ade7d; checkout local sincronizado con índice idéntico al árbol remoto, sin perder archivos. Web pública https://29925-mosalvador.github.io/BilleteraX/. Pages aprobó en 40165182589; CI backend y Android aprobó en 40165182591.

Se abrió un túnel temporal propio del agente y se comprobó desde la página pública: registro HTTPS contra Debian, WSS En línea, transferencia entrante sin pulsar Actualizar, depósito/retiro/servicio/transferencia, recarga, logout y login con datos conservados. Se crearon dos cuentas de laboratorio; principal final 2.00, cinco movimientos; no se usaron cuentas existentes. Se cerró su sesión al terminar. Evidencia `web-public-verification.json` y captura `web-public.jpg`. El enlace efímero no se usa como configuración permanente.

Se detectó que, al cambiar de cuenta, totales y recuento anteriores podían quedar visibles durante la consulta inicial. Se corrigió limpiando la vista al salir/entrar y protegiendo la búsqueda antes de disponer del snapshot. Prueba real local con respuesta demorada cuatro segundos: solo indicadores de carga; luego segunda cuenta con saldo cero e historial vacío. Se recompiló el JAR con SHA-256 e637fd9dcb06a909816fb40038d72f651846a990df914f2da64981ec55ef1676; se solicitó al usuario instalarlo una última vez para que también la web integrada de Debian incluya esa corrección. El API Java no cambió desde la actualización ya verificada.

Pendiente: publicar esta corrección visual y las evidencias finales; confirmar el resultado del instalador final y la dirección del túnel que mantiene el usuario. El túnel propio de verificación y los servidores locales de pruebas se deben detener al cerrar esta sesión; no detener procesos ajenos. Para futuras demostraciones, usar start-web-tunnel.py desde PowerShell del usuario y abrir el enlace que imprime.
