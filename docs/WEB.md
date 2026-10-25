# Banca web BilleteraX

La banca web es un cliente adicional. Usa los usuarios, sesiones, cuentas, operaciones e historial del mismo Spring Boot que utiliza Android. La base H2 continúa en Debian en `/var/lib/billeterax/wallet.mv.db`; la web no crea otra base. Android conserva Room para su consulta sin conexión. Esta ampliación no sustituye los requisitos móviles del profesor.

## Funciones

- Registro sin correo e inicio de sesión con el usuario de Android.
- Saldo, cuenta copiable, historial completo con búsqueda y filtro de ingresos/salidas.
- Depósito, retiro, transferencia y pago de electricidad/agua/internet con contrato y confirmación.
- Actualización por WebSocket, reconexión y conciliación del snapshot al volver a conectar.
- Modo claro/oscuro, diseño adaptable y cierre de sesión independiente por dispositivo.
- Si se pierde la respuesta de una operación, se conserva su UUID en la pestaña. «Revisar y reintentar» envía exactamente la misma solicitud; el backend evita duplicados. No se envían operaciones pendientes automáticamente.

Los importes son simulados. Los depósitos de laboratorio añaden saldo sin una pasarela de pago.

## Arquitectura y alojamiento

```text
GitHub Pages (HTML/CSS/JS) ── HTTPS / WSS ── Cloudflare Tunnel en Windows
                                                    │ HTTP por la LAN
Android Kotlin + Room ────────────────────────── Spring Boot en Debian
                                                    │
                                               H2 persistente
```

Pages solo sirve la interfaz. Debian mantiene las cuentas y operaciones. Para la demostración, Windows y Debian deben seguir encendidos y comunicándose. El túnel no abre puertos del router y solo apunta al servicio HTTP 8080, no a SSH o H2. Su dirección cambia cada vez que se inicia y deja de funcionar al cerrar el proceso. No ofrece alojamiento permanente.

## 1. Actualizar Debian

Desde PowerShell, después de compilar el JAR actual:

```powershell
& 'C:\Users\matsa\AppData\Local\Programs\Python\Python312\python.exe' 'C:\Proyectos\BilleteraX\deployment\scripts\deploy-debian.py' --skip-packages
```

Introduce la contraseña SSH en la terminal. El instalador respalda H2/JAR/configuración, conserva cuentas e historial y verifica registro/login, WebSocket Android, página web, CORS de Pages, pase WebSocket de un solo uso y persistencia después de reiniciar el servicio. No guarda la contraseña. La evidencia queda en `docs/evidence/debian-deployment.json`.

Para probar solo en la red local, abre `http://192.168.18.76:8080/` en Windows o el teléfono conectado a la misma red.

## 2. Publicar la interfaz en GitHub Pages

Sube los cambios a `main`. En GitHub: **Settings → Pages → Build and deployment → Source: GitHub Actions**. El workflow `.github/workflows/pages.yml` prueba el JavaScript y publica únicamente `web/dist`. También se puede ejecutar desde Actions → BilleteraX - banca web en GitHub Pages → Run workflow.

Publicada y verificada en [GitHub Pages](https://29925-mosalvador.github.io/BilleteraX/). El despliegue inicial y la prueba HTTPS/WSS hacia Debian están registrados en `docs/evidence/web-public-verification.json`.

No se necesitan contraseñas, tokens de usuario ni claves SSH en GitHub Secrets. Para otro dueño/repositorio, ajustar las direcciones públicas documentadas y `WALLET_WEB_ORIGINS`.

## 3. Iniciar el túnel de la demostración

Desde tu PowerShell:

```powershell
& 'C:\Users\matsa\AppData\Local\Programs\Python\Python312\python.exe' 'C:\Proyectos\BilleteraX\deployment\scripts\start-web-tunnel.py'
```

El script descarga cloudflared 2026.10.3 desde el repositorio oficial y verifica el SHA-256 publicado antes de ejecutarlo. Lo guarda en `.tools/cloudflared`, fuera de Git; no instala un servicio. Antes de publicar, comprueba que Debian ya sirve el backend actualizado.

Cuando aparezca **ABRE LA BANCA WEB**, abre ese enlace. Incluye la dirección HTTPS del túnel, que la interfaz recuerda en ese navegador. También puedes abrir Pages y pulsar «Configurar conexión al servidor», pegar el enlace `https://….trycloudflare.com` y comprobarlo. No escribas una URL de servidor que no controles: es el destino del login.

Deja la terminal abierta. **Ctrl+C** detiene el túnel. No hace falta volver a subir archivos a GitHub cuando cambia el túnel: abre el nuevo enlace que imprime el script. La web de GitHub necesita HTTPS; un destino `http://192.168.18.76:8080` desde una página HTTPS será rechazado por contenido mixto.

## Demostración

1. Accede en la web con tu usuario de Android, o registra una cuenta nueva (saldo cero).
2. Copia su número de cuenta. Realiza un depósito de prueba desde la web y comprueba el mismo saldo en Android.
3. Deja la web abierta. Desde otra cuenta en Android o Postman, transfiere a ese número: el saldo y el historial web deben cambiar sin pulsar Actualizar.
4. Prueba retiro mayor al saldo: debe mostrar «Fondos insuficientes» sin movimiento adicional.
5. Paga un servicio con contrato válido y revisa el historial en ambos clientes.
6. Cierra sesión en la web: la sesión Android continúa activa.

## Seguridad de la sesión del navegador

La contraseña se envía al backend durante registro/login y nunca se guarda en el cliente. El token se conserva en `sessionStorage` de la pestaña, no en la URL ni en `localStorage`; cerrar la pestaña elimina esa copia. El servidor mantiene su caducidad y revoca la sesión al cerrar sesión. No se copian los datos bancarios a GitHub.

El navegador obtiene `POST /api/auth/ws-ticket` con su Bearer. Recibe un pase aleatorio de 30 segundos y un solo uso para `/ws?ticket=…`; se comprueba de nuevo que la sesión siga activa. El logout revoca también sus conexiones y pases. Android conserva la autenticación WebSocket mediante cabecera Authorization.

CORS permite el origen exacto `https://29925-mosalvador.github.io`. Para desarrollo separado, configurar una lista explícita, por ejemplo `WALLET_WEB_ORIGINS=https://29925-mosalvador.github.io,http://127.0.0.1:5173`. No usar `*`. La ruta `/BilleteraX/` no forma parte de un origen.

## Desarrollo local

`web/dist` contiene el cliente sin dependencias externas. Node 22 o posterior permite:

```text
cd web
node --test tests/core.test.js
node serve.mjs
```

Abre `http://127.0.0.1:5173` y elige el backend local en Configurar conexión. `backend/build.gradle.kts` copia ese mismo cliente dentro del JAR durante `processResources`; no hay una segunda implementación.

Pruebas backend: `WebAccessTest` cubre CORS, aislamiento, servicio de archivos, WebSocket del navegador y revocación; `SocketTicketsTest` cubre caducidad y consumo concurrente. Las pruebas previas de Android/REST siguen siendo aplicables y se ejecutan en CI cuando cambia `web/**`.

Referencias: [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages), [workflows de Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages), [Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/), [WebSocket en el navegador](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket/WebSocket).

El APK v1.1.0 publicado sigue siendo compatible. El JAR de esa Release corresponde a la entrega anterior a la banca web; para el backend web usar la compilación actual de `main` o el artefacto backend del workflow actual, y el instalador de esta guía.
