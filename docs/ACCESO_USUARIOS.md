# Registro, acceso y demostración en teléfono

Versión 1.1.0, 24 de octubre de 2026.

## Empezar

1. Encender la VM DebianServer y conectar el teléfono a una red que alcance `192.168.18.76`.
2. Instalar `BilleteraX-debug.apk` actualizado sobre la app existente. Conservar los datos y la misma firma; no desinstalar para actualizar.
3. En el engranaje de la pantalla de acceso verificar `http://192.168.18.76:8080/`.
4. Pulsar **Crear cuenta**. Escribir nombre (2–60 caracteres), usuario (3–30, letras latinas sin tildes, números, punto, guion o guion bajo) y contraseña (al menos 8 caracteres, máximo 72 bytes UTF-8). Confirmar la contraseña.
5. No se pide correo. Un usuario duplicado se rechaza; el acceso por usuario ignora mayúsculas/minúsculas.
6. La cuenta nueva comienza en **$0.00** y tiene su propio número `CTA…`, visible y copiable en Inicio y Perfil. Un registro nuevo no hereda el dinero de CTA001/CTA002.
7. Depositar $10 de laboratorio. Comprobar saldo e historial; cerrar y reabrir la app para comprobar persistencia de sesión y movimientos.
8. En **Perfil → Apariencia** seleccionar Sistema, Claro u Oscuro. La selección persiste al reabrir.
9. **Cerrar sesión** permite registrar/entrar como otro usuario. Al volver al primero reaparece su saldo e historial.

El teléfono físico ya había sido probado con la versión previa. Esta actualización se verificó en emulador; instalar este APK en el teléfono es el paso de comprobación del usuario. La app usa HTTP permitido en la compilación debug para esta red de laboratorio.

## Dónde se guardan los datos

La misma H2 de Debian, `/var/lib/billeterax/wallet.mv.db`, guarda:

| Tabla | Contenido |
|---|---|
| `wallet_users` | Usuario normalizado, nombre, hash BCrypt de contraseña y relación con cuenta |
| `wallet_sessions` | Hash SHA-256 de la sesión, usuario y caducidad |
| `accounts` | Número de cuenta, titular, saldo y versión |
| `ledger_entries` | Asientos de movimientos |
| `wallet_operations` | UUID y resultado para idempotencia |

Android mantiene la copia de movimientos y pendientes en Room (`billeterax.db`). Guarda la sesión cifrada mediante Android Keystore; no guarda la contraseña. El servidor conserva usuarios tras reiniciar el servicio. El token original no se guarda en texto en H2.

No abrir otra instancia H2 contra el archivo mientras el servicio lo usa ni borrar la base para reiniciar una demostración. No mostrar hashes de contraseña o sesiones durante la exposición. La API y los comprobantes permiten demostrar los datos de cada cuenta sin exponer credenciales.

## Demostrar una transferencia en tiempo real

1. Abrir el usuario A en el teléfono y copiar su número de cuenta.
2. En Postman importar la colección y el entorno Debian. Completar localmente `username/password` del mismo A; ejecutar **00B Iniciar sesión**. Esto guarda `tokenA/sourceAccount`.
3. Completar `displayNameB/usernameB/passwordB` y ejecutar **00C Registrar emisor de prueba**, o **00D** si B ya existe. Guarda `tokenB/senderAccount`.
4. Ejecutar **08B Recargar emisora $20** y **09 DEMO: B envía $20 a A**.
5. Observar en Android la entrada y el saldo anterior +$20 sin pulsar Actualizar. Comparar el UUID del comprobante con Postman.

El login en Postman abre otra sesión, no cierra la del teléfono. Al cerrar una sesión se revoca esa sesión y su WebSocket.

## Comprobar Debian

Desde PowerShell:

```powershell
curl.exe http://192.168.18.76:8080/actuator/health
ssh root@192.168.18.76
```

Dentro de Debian:

```bash
systemctl is-active billeterax
systemctl is-enabled billeterax
ls -lh /var/lib/billeterax/wallet.mv.db
```

Se espera UP, active, enabled y la existencia del archivo. Para comprobar persistencia, anotar saldo/cuenta, reiniciar solo BilleteraX con `systemctl restart billeterax`, esperar UP y volver a entrar con el mismo usuario. No equivale a reiniciar la VM completa.

## Actualización ya realizada

El 24 de octubre se instaló el backend nuevo con respaldo previo del JAR, configuración y H2 en `/var/lib/billeterax/upgrade-backup.fwbtsemx`. Se verificó conservación de CTA001/CTA002 y su historial, registro/login, WebSocket, sesión y contraseña después de reiniciar el servicio, y revocación al cerrar sesión. Informe: `docs/evidence/debian-auth-upgrade.json`.

No repetir el instalador para abrir la app cada día. Encender Debian y abrir BilleteraX es suficiente.
