# Debian Server y VirtualBox

Backend actualizado el 24 de octubre de 2026, activo/habilitado en `http://192.168.18.76:8080/`. Evidencia fija de esta actualización: `docs/evidence/debian-auth-upgrade.json`. La configuración vigente es puente, IP manual `192.168.18.76/24`, interfaz `enp0s3`, gateway `192.168.18.1`, NetworkManager `Wired connection 1`.

## Uso diario y acceso de usuarios

Encender DebianServer y abrir BilleteraX 1.1.0 en el teléfono/emulador. Registrarse o iniciar sesión con usuario y contraseña, sin correo ni tokens manuales. Cada usuario tiene una cuenta nueva con saldo cero. Guía: `docs/ACCESO_USUARIOS.md`.

El usuario eligió administración por `ssh root@192.168.18.76` con contraseña. No cambiar SSH ni generar llaves para el uso diario. El servicio Java corre como el usuario restringido `billeterax`. Debian 13.7 y Java 21.0.12.1 están verificados. La contraseña SSH no es una contraseña de la app ni de H2.

- H2 persistente: `/var/lib/billeterax/wallet.mv.db`.
- Usuarios y sesiones: tablas `wallet_users` y `wallet_sessions`, junto a cuentas y movimientos existentes.
- JAR: `/opt/billeterax/billeterax.jar`.
- Entorno: `/etc/billeterax/billeterax.env`.
- Respaldo previo a esta actualización: `/var/lib/billeterax/upgrade-backup.fwbtsemx` (H2, JAR y configuración).

La actualización conservó CTA001/CTA002 y sus asientos; no los vincula automáticamente a usuarios recién registrados. Las credenciales estáticas antiguas permanecen para compatibilidad de laboratorio; Android actual no las pide.

## Actualizar nuevamente, solo cuando cambie el backend

Compilar y ejecutar desde PowerShell:

```powershell
& 'C:\Users\matsa\AppData\Local\Programs\Python\Python312\python.exe' 'C:\Proyectos\BilleteraX\deployment\scripts\deploy-debian.py' --skip-packages
```

Pide contraseña sin guardarla y verifica el host contra `known_hosts`. Requiere Paramiko en `.tools/python-debian`, requests y websocket-client. `--check-local` revisa dependencias/artefactos sin conectarse. Omitir `--skip-packages` solo si se necesita instalar Java/curl.

El instalador detiene BilleteraX, respalda H2/JAR/entorno, instala el JAR y restablece el servicio. Verifica hash, salud, datos antiguos, registro/login, WebSocket y persistencia de cuenta, contraseña y sesión tras reiniciar solo el servicio; después comprueba revocación. Crea un usuario de comprobación con saldo cero y contraseña aleatoria no guardada. El informe de la ejecución va a `docs/evidence/debian-deployment.json`; una ejecución posterior puede reemplazarlo. La evidencia aprobada del 24 de octubre se conserva aparte como `debian-auth-upgrade.json`.

## Pruebas Android actuales

Con ambos APK instalados y un solo emulador/dispositivo conectado:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am instrument -w -r -e serverUrl http://192.168.18.76:8080/ -e class ec.edu.espe.billeterax.RegisteredUserFlowTest ec.edu.espe.billeterax.test/androidx.test.runner.AndroidJUnitRunner
```

La prueba usa cuentas nuevas y movimientos simulados, sin contraseña root ni tokens manuales. No detener el servicio durante esta ejecución. El resultado esperado es `OK (2 tests)`. Ver los resultados realmente ejecutados en `docs/testing/RESULTADOS.md`.

`verify-android-debian.py` es el verificador histórico de la UI anterior a 1.1.0 (CTA001 con token). No utilizarlo como verificador de la pantalla de acceso nueva. Sus evidencias del 17 de octubre siguen documentando recuperación offline e idempotencia de aquella versión. El reinicio completo de VM sigue pendiente.

## Referencia manual e historial del plan NAT

Los apartados siguientes describen el plan anterior y la alternativa Host-Only. La configuración vigente elegida por el usuario es **puente**; no aplicar sus comandos de reenvío NAT a la VM actual. Para instalación manual en puente, usar puerto SSH 22, IP 192.168.18.76 y `SERVER_ADDRESS` con esa IP verificada.

## 1 Inspeccionar antes de crear

En Windows:

```powershell
VBoxManage list vms
VBoxManage list runningvms
VBoxManage list hostonlyifs
VBoxManage list dhcpservers
ipconfig
Get-NetRoute -AddressFamily IPv4
```

Reutilizar `DebianServer`: configuración observada Debian 13 de 64 bits, 8 GB RAM, 8 vCPU, disco virtual de 20 GB y un adaptador NAT. La versión instalada se confirmará con `/etc/os-release` por SSH; la etiqueta de VirtualBox no basta para verificarla. No crear otra VM ni cambiar adaptadores de otros proyectos. El escritorio gráfico instalado es compatible con el backend; el servicio no depende de una sesión gráfica.

Revisar el espacio actual en Windows y con `df -h` en Debian antes de instalar paquetes. La lectura de espacio libre de la inspección inicial ya es histórica.

## 2 Red para la VM existente: NAT con puertos locales

Conservar el adaptador NAT de `DebianServer`. Configurar estos reenvíos TCP con IP anfitrión **127.0.0.1**, sin abrir puertos en el router:

| Nombre | IP anfitrión | Puerto anfitrión | IP invitado | Puerto invitado |
|---|---|---:|---|---:|
| billeterax-ssh | 127.0.0.1 | 2222 | Vacío, usa el invitado NAT | 22 |
| billeterax-http | 127.0.0.1 | 18080 | Vacío, usa el invitado NAT | 8080 |

En VirtualBox: Configuración → Red → Adaptador 1 → Reenvío de puertos. Alternativa PowerShell para una VM **encendida**:

```powershell
& 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe' controlvm 'DebianServer' natpf1 'billeterax-ssh,tcp,127.0.0.1,2222,,22'
& 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe' controlvm 'DebianServer' natpf1 'billeterax-http,tcp,127.0.0.1,18080,,8080'
```

Si está apagada, usar `modifyvm 'DebianServer' --nat-pf1 '...'` en lugar de `controlvm ... natpf1`. Si ya existen reglas con esos nombres, inspeccionarlas antes de añadir o cambiar nada. Comprobar también puertos ocupados. No eliminar reglas ajenas.

En Debian, obtener la IP real del adaptador NAT con `ip -br address` e `ip route`; usarla como `SERVER_ADDRESS`. No presuponer que sea 10.0.2.15. No usar 127.0.0.1 dentro de Debian para este servicio, porque el reenvío llega por su interfaz NAT.

Ruta prevista: emulador `10.0.2.2:18080` → Windows `127.0.0.1:18080` → NAT VirtualBox → Debian `IP_NAT:8080`. Windows consulta `http://127.0.0.1:18080/actuator/health`. Esta ruta debe probarse; no equivale a una conexión Host-Only.

### Alternativa Host-Only, solo si se elige posteriormente

Adaptador 1 NAT para instalar paquetes. Adaptador 2 Host-Only para Windows ↔ Debian. Referencia propuesta: 192.168.56.0/24, Windows .1 y Debian .10. No asignar .10 sin comprobar rutas, concesiones DHCP, otras VMs, vecinos y posibles duplicados; un ping sin respuesta no prueba que una IP esté libre.

En Debian identificar nombres reales con `ip -br address` e `ip route`. La interfaz NAT conserva DHCP y la única ruta por defecto. En la Host-Only se configura la IP verificada, sin segunda puerta de enlace. Ejemplo si usa ifupdown y la interfaz real es enp0s8:

```text
auto enp0s8
iface enp0s8 inet static
    address 192.168.56.10/24
```

Aplicar mediante el gestor real de la VM (ifupdown, NetworkManager o systemd-networkd). No instalar otro gestor ni sobrescribir `/etc/network/interfaces` a ciegas. Mantener la consola VirtualBox abierta al cambiar la red. Registrar las direcciones efectivamente asignadas en `docs/REDES.md`.

## 3 Java y SSH

Desde la consola Debian con usuario administrador:

```bash
sudo apt update
sudo apt install openjdk-21-jre-headless openssh-server curl
java -version
sudo systemctl enable --now ssh
ip -br address
```

Debian 13 dispone de Java 21. Si se utiliza otra versión, verificar sus paquetes antes de cambiar repositorios. Configurar autenticación SSH por clave, comprobarla en otra sesión y solo entonces endurecer acceso por contraseña. Conservar `PermitRootLogin no`. Verificar la huella SSH por un canal confiable y no deshabilitar su comprobación.

## 4 Transferir y preparar el servicio

Compilar en Windows y transferir desde la carpeta raíz:

```powershell
scp -P 2222 .\backend\build\libs\billeterax.jar vboxuser@127.0.0.1:~/billeterax.jar
scp -P 2222 -r .\deployment vboxuser@127.0.0.1:~/deployment
ssh -p 2222 vboxuser@127.0.0.1
```

En Debian:

```bash
sudo sh ~/deployment/debian/install.sh ~/billeterax.jar
# La primera ejecución crea el archivo de ejemplo y termina sin iniciar.
sudo nano /etc/billeterax/billeterax.env
```

Sustituir todos los valores REPLACE. `SERVER_ADDRESS` debe ser la IP NAT verificada para la ruta elegida en esta VM; el proceso escucha solo en ella. Si se elige Host-Only, usar su IP verificada. Crear dos credenciales aleatorias diferentes y una contraseña para H2. Por ejemplo `openssl rand -hex 32` genera un valor local; no pegar valores reales en el repositorio ni en el informe. La contraseña de inicio de sesión de Debian no se usa como token ni contraseña de H2.

```bash
sudo sh ~/deployment/debian/install.sh ~/billeterax.jar
sudo systemctl status billeterax --no-pager
sudo journalctl -u billeterax -n 100 --no-pager
curl http://IP_DEBIAN:8080/actuator/health
```

El servicio usa un usuario sin login, conserva H2 en `/var/lib/billeterax`, configura reinicio tras fallo con 10 s de espera y registra logs en journald. El instalador respalda el JAR anterior al actualizar; nunca borra la base. Antes de cambiar de versión, detener el servicio y realizar una copia consistente del directorio de datos con permisos restringidos. No copiar una H2 abierta como si fuese un backup consistente.

## 5 Firewall y conectividad

Inspeccionar `sudo nft list ruleset` y el firewall existente. En la ruta NAT elegida, los reenvíos escuchan únicamente en loopback de Windows. No sustituir 127.0.0.1 por 0.0.0.0. Si Debian filtra tráfico, comprobar qué dirección de origen presenta VirtualBox antes de integrar reglas en el gestor existente; no vaciar reglas, desactivar el firewall ni abrir NAT del router hacia internet.

En Windows ejecutar `deployment/scripts/check-network.ps1 -DebianIP 127.0.0.1 -SshPort 2222 -ApiPort 18080`. Verificar SSH y HTTP por separado. En Configuración de BilleteraX usar `http://10.0.2.2:18080/`, la cuenta elegida y su token generado dentro de Debian. El APK no requiere recompilación para cambiar estos datos.

Solo para la alternativa Host-Only: probar primero la URL Debian en Configuración. Si no es enrutable desde el emulador, se puede usar un túnel iniciado en Windows; no iniciarlo si el puerto 18080 ya está ocupado por el reenvío NAT:

```powershell
ssh -N -L 127.0.0.1:18080:IP_DEBIAN:8080 administrador@IP_DEBIAN
```

Entonces el emulador usa `http://10.0.2.2:18080/`; REST y WebSocket viajan al backend Debian. Verificar que el puerto no esté ocupado antes de usarlo. Mantener abierta esa sesión. Documentar el túnel como parte de la topología real, sin afirmar conectividad Host-Only directa.

Para un teléfono físico, valorar una interfaz puente en una red privada con autorización y firewall restringido a su IP. Verificar alcance antes de cambiar SERVER_ADDRESS. No usar 10.0.2.2 desde el teléfono.

## 6 Operación y reinicio

```bash
sudo systemctl start billeterax
sudo systemctl stop billeterax
sudo systemctl restart billeterax
sudo systemctl is-enabled billeterax
sudo journalctl -u billeterax -f
```

Detener únicamente este servicio para la demostración de desconexión. Comprobar Room, error de nueva operación y recuperación. Después, reiniciar la VM de forma controlada y verificar el arranque automático del servicio, el saldo persistente, la conexión del emulador y ausencia de duplicados. Guardar evidencia real de cada paso.

## Diagnóstico

- `Connection refused`: revisar servicio, IP de escucha y puerto con `ss -ltnp`.
- Timeout: revisar rutas, firewall y adaptadores antes de cambiar código.
- HTTP 401/403: verificar que la credencial corresponde a la cuenta; no imprimirla en logs.
- WebSocket desconectado pero REST disponible: revisar `/ws`, proxy/túnel y handshake Bearer.
- H2 bloqueada: comprobar que solo existe un proceso usando el archivo; no borrar archivos para desbloquear.
- Servicio no arranca: `journalctl`, Java 21, variables no vacías y permisos de `/var/lib/billeterax`.
- Room conserva registros de una demo anterior: usar otra URL/puerto de laboratorio o limpiar solo los datos de la instalación de prueba con autorización. No usar esto durante la prueba de persistencia.

Referencia de los comandos de red: [manual oficial de VBoxManage 7.2](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/vboxmanage.html). La instalación actual usa la ruta NAT descrita como propuesta hasta verificar conectividad; no confundir la guía con evidencia de despliegue.
