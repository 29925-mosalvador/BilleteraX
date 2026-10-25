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

El acceso público utiliza un auxiliar HTTPS fijo en Render Free, verificado desde Internet. Debian conserva Spring Boot y la única base H2 bancaria; recoge mensajes de negocio por conexiones salientes. No se configura el router ni se utilizan túneles de red.

**Acceso verificado:** [abrir la banca](https://29925-mosalvador.github.io/BilleteraX/). La interfaz usa automáticamente `https://billeterax-access.onrender.com`; no pide configurar un servidor. La URL de Pages distribuye la interfaz; el auxiliar entrega mensajes al Debian encendido, donde se confirma cada operación.

La configuración, pruebas y límites están en [Acceso desde Internet](ACCESO_INTERNET.md). Los visitantes solo abren la página, se registran o inician sesión. No deben introducir direcciones de servidor. Para la red local sigue disponible `http://192.168.18.76:8080/`.

`deployment/scripts/start-web-tunnel.py` se conserva únicamente como herramienta de la demostración anterior. No forma parte del nuevo procedimiento. Las evidencias antiguas con túnel describen esa versión, no la disponibilidad del nuevo alojamiento.

## Publicar la interfaz en GitHub Pages

El workflow `.github/workflows/pages.yml` prueba el cliente y publica `web/dist` en [GitHub Pages](https://29925-mosalvador.github.io/BilleteraX/). `config.js` indica el origen HTTPS fijo verificado y `managed:true`. No guardar secretos del agente, contraseñas de usuarios o credenciales SSH en esa configuración pública.

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

Para la banca funcional en desarrollo, abrir la web integrada en el backend (`http://127.0.0.1:8080/` después de iniciarlo). El servidor Node 5173 solo sirve archivos: con la configuración administrada actual no reenvía el API. Para separar ambos procesos durante desarrollo, usar una copia local de `config.js` con la URL del backend y `managed:false`, y permitir explícitamente ese origen en CORS; no publicar ese cambio sobre la configuración de Pages. `backend/build.gradle.kts` copia ese mismo cliente dentro del JAR durante `processResources`; no hay una segunda implementación.

Pruebas backend: `WebAccessTest` cubre CORS, aislamiento, servicio de archivos, WebSocket del navegador y revocación; `SocketTicketsTest` cubre caducidad y consumo concurrente. Las pruebas previas de Android/REST siguen siendo aplicables y se ejecutan en CI cuando cambia `web/**`.

Referencias: [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages), [workflows de Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages), [WebSocket en el navegador](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket/WebSocket).

La versión 1.2.0 incluye APK compatible con la firma existente y JAR con esta banca web. Descargar desde Releases; el APK de Actions tiene una firma temporal diferente. Las instalaciones Android anteriores conservan la dirección de servidor guardada; cambiarla una vez a HTTPS público solo después de resolver las operaciones pendientes.
