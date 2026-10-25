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

Por petición del usuario se está sustituyendo el acceso temporal por un auxiliar público HTTPS fijo. Debian conserva Spring Boot y la única base H2 bancaria; recoge mensajes de negocio por conexiones salientes. No se configura el router ni se utilizan túneles de red.

El código y las pruebas locales están preparados. **La activación real del alojamiento, la conexión de Debian y las direcciones predeterminadas de los clientes siguen pendientes hasta verificar el nuevo servicio público.** La URL actual de Pages distribuye la interfaz; por sí sola no ejecuta el backend.

La configuración, pruebas y límites están en [Acceso desde Internet](ACCESO_INTERNET.md). Una vez activado, los visitantes solo abren la página, se registran o inician sesión. No deben introducir direcciones de servidor. Para la red local sigue disponible `http://192.168.18.76:8080/`.

`deployment/scripts/start-web-tunnel.py` se conserva únicamente como herramienta de la demostración anterior. No forma parte del nuevo procedimiento. Las evidencias antiguas con túnel describen esa versión, no la disponibilidad del nuevo alojamiento.

## Publicar la interfaz en GitHub Pages

El workflow `.github/workflows/pages.yml` prueba el cliente y publica `web/dist` en [GitHub Pages](https://29925-mosalvador.github.io/BilleteraX/). Después de verificar el auxiliar real, `config.js` debe indicar su origen HTTPS fijo y `managed:true`. No guardar secretos del agente, contraseñas de usuarios o credenciales SSH en esa configuración pública.

El mismo cliente se empaqueta dentro del JAR Debian y del auxiliar. Este último genera su configuración de mismo origen; no hay una segunda implementación de la banca.

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

Referencias: [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages), [workflows de Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages), [WebSocket en el navegador](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket/WebSocket).

El APK v1.1.0 publicado sigue siendo compatible. El JAR de esa Release corresponde a la entrega anterior a la banca web; para el backend web usar la compilación actual de `main` o el artefacto backend del workflow actual, y el instalador de esta guía.
