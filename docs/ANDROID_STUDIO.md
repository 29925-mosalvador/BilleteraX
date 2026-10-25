# Android Studio: configuración y recuperación

Comprobación inicial del 16 de octubre y actualización del 17 de octubre de 2026 en el equipo del usuario.

## Configuración actual

- Abrir `C:\Proyectos\BilleteraX\android` en Android Studio.
- SDK: `C:\Users\matsa\AppData\Local\Android\Sdk`, según `android/local.properties`.
- JDK de Gradle: `GRADLE_LOCAL_JAVA_HOME`, que resuelve a `D:\AndroidStudio\jbr` (Java 21.0.10).
- Dispositivo: `Pixel_10_Pro_XL`, conectado como `emulator-5554` durante la comprobación.
- Gráficos del AVD: `software`; SurfaceFlinger confirmó Google SwiftShader.
- Arranque del AVD: en frío (`fastboot.forceColdBoot=yes`, `fastboot.forceFastBoot=no`).

## Error observado y corrección

Android Studio había compilado e instalado BilleteraX, pero una ejecución posterior falló con `Couldn't terminate previous instance of app`. El registro del emulador mostraba fallos SIGSEGV en RenderThread de System UI y del lanzador, y cargas repetidas de `default_boot`. Una solicitud de arranque mediante ADB tampoco terminaba mientras el dispositivo estaba en ese estado.

Se respaldó `config.ini`, se cerró el AVD y se configuraron gráficos por software y arranque en frío. Se conservaron las aplicaciones y sus datos. Tras arrancar nuevamente el AVD desde Android Studio, BilleteraX abrió y sincronizó con el backend. Los resultados son compatibles con un problema de estado/renderizado del emulador; no prueban una causa única del error del IDE.

La copia anterior está en:

`C:\Users\matsa\Documents\Codex\2026-10-16\files-mentioned-by-the-user-proyecto\work\android-studio-fix\Pixel_10_Pro_XL.config.before.ini`

Para revertir estos ajustes, cerrar primero el AVD y restaurar esa copia sobre `C:\Users\matsa\.android\avd\Pixel_10_Pro_XL.avd\config.ini`.

## Ejecutar BilleteraX

La versión 1.1.0 usa registro/login. Mantener DebianServer encendido, iniciar el Pixel o conectar el teléfono y pulsar Run. En acceso, verificar servidor `http://192.168.18.76:8080/`; crear un usuario o entrar con uno existente. No introducir números de cuenta ni tokens manuales. Las cuentas nuevas comienzan en cero. Ver `docs/ACCESO_USUARIOS.md`.

Las pruebas del 17 de octubre descritas más abajo son históricas. Los resultados de autenticación actuales están en `docs/testing/RESULTADOS.md`.

### Alternativa de desarrollo con backend Windows

1. Abrir Device Manager e iniciar Pixel 10 Pro XL. Esperar a que termine el arranque.
2. Seleccionar la configuración `app` y ese dispositivo en Android Studio; pulsar Run.
3. Para operaciones contra Windows, mantener el backend local iniciado. Desde PowerShell:

```powershell
cd C:\Proyectos\BilleteraX
.\deployment\scripts\start-local.ps1 -JavaHome 'D:\AndroidStudio\jbr'
```

En el engranaje de acceso de Android guardar `http://10.0.2.2:8080/` y registrarse/iniciar sesión en ese servidor. Su H2 es independiente de Debian. El backend dev conserva su archivo entre arranques. Si ya está iniciado, no abrir una segunda instancia en el mismo puerto.

## Si vuelve a quedar una instancia bloqueada

### Si aparece INSTALL_FAILED_UPDATE_INCOMPATIBLE

El 17 de octubre se comprobó que el APK de la compilación aislada y Android Studio usaban claves debug diferentes. Se recompiló usando la clave existente de `%USERPROFILE%\.android`, y la actualización con `adb install -r` conservó la base Room (hashes de base y WAL iguales antes/después).

`deployment/scripts/build.ps1` ahora utiliza esa misma carpeta de usuario por defecto y compila juntos app y pruebas. Para una instalación que use otra clave existente, indicar `-AndroidUserHome` con su carpeta correspondiente. No cambiar la clave ni desinstalar BilleteraX para resolver este error: identificar primero el certificado instalado. Las claves privadas permanecen fuera del repositorio y de los entregables.

### Si la app no termina de cerrar

Desde la raíz del proyecto:

```powershell
.\deployment\scripts\restart-android-app.ps1 -Serial emulator-5554
```

Este comando detiene únicamente BilleteraX y vuelve a solicitar su apertura. Si Android no responde, usar Cold Boot desde Device Manager. El comando no borra datos. Los identificadores de emulador pueden cambiar; `adb devices` permite consultar el actual.

## Validación ejecutada

- ADB reconoce el dispositivo como `device`; Android informó arranque completo.
- Dos ciclos de cierre y reapertura: parada en 0.110 y 0.203 segundos; arranque en 1.497 y 1.554 segundos; ambos devolvieron `Status: ok` y un PID nuevo.
- UI observada con saldo $100.00 y `WebSocket · En línea`.
- Backend respondió `UP`; también se consultó su health desde el propio emulador mediante `10.0.2.2:8080`.
- No se observó `FATAL EXCEPTION` en el registro del proceso final durante esta comprobación.
- No se automatizó el botón Run del IDE; el cierre y lanzamiento se verificaron mediante ADB, y Android Studio detectó los procesos nuevos.

Evidencias: `docs/evidence/android-studio-verification.json` y `docs/evidence/android-studio-online.png`. Esta comprobación de entorno no sustituye la instrumentación completa ni la prueba de recuperación ante caída del servidor.
