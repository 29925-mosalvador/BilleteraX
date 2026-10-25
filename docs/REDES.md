# Inventario y red observada

Actualización del 17 de octubre de 2026, zona America/Guayaquil. La tabla inicial se conserva como registro histórico; el inventario actual de Debian aparece más abajo.

| Elemento | Evidencia observada |
|---|---|
| Carpeta solicitada | C:/Proyectos/BilleteraX no contenía proyecto previo; se creó el proyecto |
| Windows Ethernet | 192.168.18.7, máscara 255.255.255.0, puerta de enlace 192.168.18.1 |
| Java original en PATH | Oracle Java 8, insuficiente para el proyecto |
| Java para estas compilaciones | Temurin OpenJDK 21 portable, ZIP oficial verificado con SHA-256 |
| Gradle | Distribución 8.13 disponible; wrappers generados para backend y Android |
| Android SDK existente | Plataformas 34 y 36.1; Build Tools 35.0.0, 36.1.0 y 37.0.0 |
| Android Studio | Presente en registro de aplicaciones; no utilizado para compilar por GUI |
| Emulador existente | Pixel_10_Pro_XL; se creó un AVD separado para esta tarea |
| VirtualBox | No encontrado en PATH, rutas estándar ni registro consultado |
| Debian y SSH | Sin una VM o dirección Debian accesible identificada |
| Red Host-Only | No apareció en ipconfig |

## Estado actual de Debian y ruta elegida

VirtualBox 7.2.20 está instalado. `DebianServer` está registrada y se observó encendida con GNOME. El usuario eligió administración mediante root por contraseña. El instalador confirmó Debian 13.7 y 13 GB libres en la raíz. La VM tiene 8 GB RAM, 8 vCPU y disco virtual de 20 GB.

El usuario cambió el adaptador de NAT a **puente**. El instalador confirmó `enp0s3`, IP manual `192.168.18.76/24`, gateway `192.168.18.1` y perfil NetworkManager `Wired connection 1`. No se verificó la reserva/exclusión DHCP en el router. Windows dispone de Host-Only 192.168.56.1/24, pero esa no es la red elegida.

La ruta vigente es directa por la red local; el plan anterior de reenvíos NAT 2222/18080 queda descartado para esta configuración:

| Acceso | Ruta y estado |
|---|---|
| SSH desde Windows | 192.168.18.76:22; root autenticado mediante instalador ejecutado por el usuario |
| HTTP/WebSocket desde Windows | 192.168.18.76:8080; salud, REST y handshake autenticado verificados |
| Android emulador | http://192.168.18.76:8080/; instrumentación REST/WebSocket y UI de recuperación aprobadas |

Se recibió `SSH-2.0-OpenSSH_10.0p2 Debian-7+deb13u4` desde Windows. La IP figura en known_hosts del usuario. La política bloqueó la entrada interactiva del agente, pero el instalador ejecutado por el usuario completó autenticación, instalación y verificaciones. `docs/evidence/debian-deployment.json` registra el resultado real. El agente confirmó además HTTP 200/UP, 401 sin credencial y 404 para actuator/env.

Estado de las comprobaciones:

| Comprobación | Valor o evidencia |
|---|---|
| Nombre y versión Debian | DebianServer; Debian GNU/Linux 13.7 |
| Interfaz puente, IP y ruta por defecto | enp0s3, manual 192.168.18.76/24, gateway 192.168.18.1 |
| Windows → Debian SSH | Verificado por instalador ejecutado por usuario |
| Windows → Debian HTTP 8080 | UP; consultas autenticadas y rechazo 401 sin token |
| Emulador → Debian 8080 | Verificado: 2 pruebas, Room offline, UUID persistente, reintento sin duplicados y UI en línea; android-debian-verification.json |
| WebSocket y reinicio del servicio | Handshake autenticado y persistencia de snapshots verificados |
| Recuperación tras reiniciar VM | Pendiente; no se reinició la VM |
