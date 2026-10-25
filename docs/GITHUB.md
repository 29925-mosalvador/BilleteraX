# Publicación y uso desde GitHub

Repositorio: [29925-mosalvador/BilleteraX](https://github.com/29925-mosalvador/BilleteraX).

## Abrir y descargar

- [Banca web](https://29925-mosalvador.github.io/BilleteraX/): registro/login normal desde Internet, sin configurar conexión.
- [Versión 1.2.0](https://github.com/29925-mosalvador/BilleteraX/releases/tag/v1.2.0): APK instalable, JAR Java 21, instrucciones y SHA256SUMS.txt.

Encender DebianServer en VirtualBox. El servicio conecta automáticamente con el auxiliar HTTPS fijo en Render Free; no hay que mantener una terminal en Windows. Debian conserva la única base bancaria en `/var/lib/billeterax/wallet.mv.db`. Sin Debian no se puede iniciar sesión ni confirmar operaciones; Pages puede cargar la interfaz estática y Android mantiene Room para consulta local.

GitHub Pages aloja la interfaz; no ejecuta Spring Boot ni H2. Render recibe mensajes de negocio en memoria y Debian ejecuta los servicios existentes. No hay túneles, puertos abiertos en el router ni una segunda base bancaria. Ver [Acceso desde Internet](ACCESO_INTERNET.md).

## Instalar Android

Descargar `BilleteraX-1.2.0.apk` desde la versión publicada. Instalar sobre la app existente **sin desinstalar ni borrar datos**. Conserva la firma de la entrega local anterior. Las cuentas nuevas comienzan con saldo cero y el dinero es simulado.

Una instalación nueva usa `https://billeterax-access.onrender.com/`. Una actualización respeta la URL anterior: resolver primero operaciones pendientes en ese servidor y después, si se desea acceso público, guardar esa URL desde el engranaje de acceso e iniciar sesión otra vez. Las cuentas H2 son las mismas; el caché Room se aísla por cuenta y dirección del servidor.

El APK de GitHub Actions tiene una firma temporal diferente: usar el de Releases para actualizar la instalación del usuario. Las claves privadas de Android Studio nunca se publican. `SHA256SUMS.txt` permite comprobar los archivos.

## Compilación y pruebas automáticas

El workflow `.github/workflows/build.yml` se ejecuta al cambiar fuentes relevantes en main, abrir una propuesta hacia main o iniciarlo manualmente desde Actions.

- Backend: Java 21, pruebas y JAR.
- Auxiliar: pruebas HTTP/WebSocket, JAR e integración con dos procesos Java reales y una H2 desechable.
- Android: SDK 36.1/Build Tools 35.0.0, APK de app y de instrumentación, pruebas unitarias y lint.
- Pages: pruebas del cliente web y publicación de `web/dist` con `.github/workflows/pages.yml`.

Código de acceso público verificado en [CI 40174054842](https://github.com/29925-mosalvador/BilleteraX/actions/runs/40174054842) y [Pages 40174054777](https://github.com/29925-mosalvador/BilleteraX/actions/runs/40174054777). Las pruebas de instrumentación se compilan en CI, pero su ejecución requiere dispositivo y backend. La compilación nueva no se presenta como una ejecución nueva en teléfono.

## Ejecutar el código descargado

Clonar el repositorio o descargar las fuentes, abrir `android/` en Android Studio y usar JDK 21 y el SDK requerido. Debian puede seguir sirviendo el backend instalado. Para desarrollo con backend Windows, seguir README.md y cambiar la URL desde el engranaje de acceso.

No se versionan H2/SQLite reales, contraseñas, archivos .env, local.properties, cachés ni claves de firma. Los ejemplos dev contienen únicamente credenciales públicas de laboratorio. No se asignó una licencia nueva sin indicación del propietario.

## Exposición

Mostrar repositorio, APK y Actions. Abrir la banca desde Internet con Debian encendido; demostrar las mismas cuentas en Android y web y una transferencia entrante sin Actualizar. Consultar saldo/historial por LAN en Debian y por HTTPS público: deben coincidir. Detener solo BilleteraX impide el login público y reiniciarlo recupera el acceso; el instalador registra esa prueba. La guía [WEB.md](WEB.md) desarrolla la demostración.

Esta ampliación no sustituye la demostración móvil con Room ni el informe APA 7 pendiente. Render Free es un alojamiento de demostración con límites y posible demora al arrancar; no se exige un plan de pago.
