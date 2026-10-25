# Acceso a BilleteraX desde Internet

## Estado de esta ampliación

El código del auxiliar público y del agente Debian está implementado y probado en un entorno local con dos procesos Java reales. **Todavía falta aprovisionar Render, instalar este JAR en Debian y fijar la dirección pública en los clientes.** No se anuncia un nuevo enlace operativo hasta completar esas comprobaciones. El servicio Debian existente y su base de datos no fueron modificados durante estas pruebas.

## Funcionamiento

```text
Web de GitHub Pages / Android
          │ HTTPS + WebSocket seguro
          ▼
Auxiliar público Java en Render — cola temporal en memoria
          ▲
          │ Debian consulta mensajes por HTTPS saliente
          ▼
Spring Boot en Debian / VirtualBox
          │
          ▼
H2: /var/lib/billeterax/wallet.mv.db
```

Debian ejecuta registro, login, consultas y movimientos usando los servicios transaccionales existentes. El auxiliar recibe únicamente esos mensajes tipados; no reenvía URLs, puertos o comandos arbitrarios y no permite acceder a SSH ni a H2. No se utilizan túneles de red, redirecciones de puertos, cambios del router ni una segunda base bancaria.

Debian inicia automáticamente la conexión saliente cuando arranca el servicio systemd. Una vez configurado, el usuario solo enciende la VM; no necesita mantener una terminal o programa auxiliar en Windows. Windows debe estar encendido porque aloja VirtualBox.

La disponibilidad del principal se comprueba con mensajes periódicos: tras unos seis segundos sin Debian, el auxiliar rechaza accesos y operaciones con HTTP 503 y cierra los WebSockets. La interfaz estática de GitHub puede cargar aunque Debian esté apagado; no puede iniciar sesión ni confirmar movimientos. Android mantiene la consulta local de Room, como exige el profesor.

## Aprovisionar una vez

1. Conectar Render con la cuenta del propietario. Crear el servicio desde `render.yaml`, plan **free**, Dockerfile `gateway/Dockerfile`, contexto raíz. No se necesita una base Render ni un disco persistente. No seleccionar planes de pago.
2. Conservar la dirección HTTPS fija asignada al servicio. El Blueprint genera `GATEWAY_AGENT_TOKEN`, una clave privada para el agente, distinta de las contraseñas de los usuarios. No publicarla en GitHub ni enviarla por chat.
3. Compilar el backend actual y ejecutar el instalador en PowerShell:

```powershell
& 'C:\Users\matsa\AppData\Local\Programs\Python\Python312\python.exe' 'C:\Proyectos\BilleteraX\deployment\scripts\deploy-debian.py' --skip-packages --gateway-url 'https://DIRECCION-ASIGNADA-POR-RENDER'
```

Introducir la contraseña SSH y la clave del auxiliar únicamente cuando las solicite la terminal. El instalador transfiere la clave por SFTP, respalda la configuración y conserva las credenciales y la base existentes. La guarda solo donde el servicio la necesita: `/etc/billeterax/billeterax.env`, permisos root:billeterax 0640. Se mantiene `GATEWAY_ENABLED=false` hasta hacer esta configuración; las instalaciones anteriores continúan funcionando por LAN.

4. El instalador comprueba HTTPS, CORS de Pages, las mismas cuentas y WSS. Detiene brevemente solo BilleteraX para comprobar el rechazo del login público, vuelve a iniciarlo en un bloque `finally` y comprueba la recuperación. Dejar libre la app durante esta prueba. La evidencia queda en `docs/evidence/debian-deployment.json`, sin claves ni contraseñas.
5. Después de verificar el servicio real, fijar `web/dist/config.js` con `{apiBase:"https://DIRECCION-REAL",managed:true}` y publicar Pages. Ese modo ignora las direcciones antiguas guardadas y los parámetros `servidor`, y oculta el botón de configurar conexión. El visitante solo ve registro/login. La web servida directamente por el auxiliar ya configura su mismo origen automáticamente.
6. Fijar esa misma dirección HTTPS como valor predeterminado de Android y generar un APK con la firma existente. No desinstalar ni borrar Room. Revisar las operaciones pendientes antes de cambiar una configuración guardada de servidor; no migrarlas silenciosamente a otra URL.

Los pasos 5–6 siguen pendientes mientras no exista una dirección real verificada. No reemplazarla por una dirección inventada.

## Pruebas reproducibles

Con Java 21 y Python con `requests` y `websocket-client`:

```powershell
$env:JAVA_HOME = 'C:\ruta\jdk-21'
.\backend\gradlew.bat -p backend test bootJar
.\backend\gradlew.bat -p gateway test bootJar
python deployment\scripts\verify-public-access-local.py
```

El verificador exige libres 8088 y 8089 y crea procesos propios con una H2 en memoria desechable. No conecta a la VM ni modifica el router. Comprueba las cuatro operaciones, aislamiento, WebSocket web/nativo, eventos del principal al navegador, idempotencia, respuesta perdida después del commit, reinicio del auxiliar, revocación y caída/recuperación del principal. Se ejecuta también en GitHub Actions. `--keep-running` conserva esos procesos solo para revisar la interfaz local; detener el verificador al terminar.

`/actuator/health` comprueba el proceso público; `/api/status` indica si Debian responde. Que el auxiliar esté UP no demuestra que Debian esté encendido.

## Alcance y límites

El auxiliar no guarda cuentas, contraseñas, sesiones ni balances en una base propia. Procesa solicitudes y respuestas temporalmente en RAM; la comunicación pública y con Debian usa HTTPS. Solo la clave del agente permite intercambiar mensajes. El principal autoriza cada operación con las sesiones almacenadas en su H2. Las colas y conexiones tienen límites y el acceso de registro/login tiene limitación de intentos.

Si se pierde una respuesta o se reinicia el auxiliar, el cliente conserva el UUID de la operación y debe comprobar la misma solicitud; no crear otro UUID ni darla por rechazada. La idempotencia sigue en Debian. Las notificaciones se concilian por snapshot para tolerar duplicados o interrupciones.

Render Free es para demostraciones. Puede suspenderse después de 15 minutos sin tráfico y tardar alrededor de un minuto en arrancar; tiene límites mensuales. No es una promesa de disponibilidad para producción. Referencias oficiales: [plan gratuito](https://render.com/docs/free), [Blueprint](https://render.com/docs/blueprint-spec), [WebSocket](https://render.com/docs/websocket).

La banca es una simulación académica: los depósitos y pagos no procesan dinero real. Se conserva Android/Kotlin + Room y Spring Boot/H2 en Debian, conforme al proyecto del profesor.
