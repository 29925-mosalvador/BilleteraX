# Publicación y uso desde GitHub

Repositorio: https://github.com/29925-mosalvador/BilleteraX

## Qué entrega GitHub

El repositorio contiene Android Studio, backend Spring Boot, Postman, despliegue Debian y pruebas. Esto atiende el requisito de enlace al código del documento del profesor. Las versiones de Releases incluyen el APK instalable y el JAR; GitHub también permite descargar las fuentes de cada versión.

GitHub Pages aloja contenido estático; no ejecuta este APK ni mantiene Spring Boot y H2 como servidor permanente. El proyecto conserva Android/Kotlin y el backend en Debian/VirtualBox. Descargar el APK no cambia la dirección de Debian: el dispositivo debe alcanzar http://192.168.18.76:8080/ en la red de laboratorio. No se abrieron puertos del router ni se trasladó la base.

## Descargar e instalar

Abrir Releases y descargar BilleteraX-1.1.0.apk. Instalar sobre la versión existente, con Debian encendido. Crear usuario o entrar con uno existente; las cuentas nuevas empiezan en cero. La firma de ese APK es la misma de la entrega local verificada. SHA256SUMS.txt permite comprobar los archivos descargados.

## Compilación y pruebas automáticas

El workflow .github/workflows/build.yml se ejecuta al subir cambios a main, abrir una propuesta hacia main o iniciarlo manualmente desde Actions.

- Backend: Java 21, pruebas y bootJar; conserva JAR e informes como artefactos.
- Android: SDK 36.1/Build Tools 35.0.0, APK de app y de pruebas, unitarias y lint; conserva resultados y un APK de CI.
- Las pruebas de instrumentación se compilan en ese workflow, pero necesitan dispositivo y backend para ejecutarse. La evidencia real contra Debian se mantiene en docs/evidence.

En Actions abrir una ejecución aprobada y descargar sus artefactos. La firma del APK de CI es temporal, generada por el runner. Para actualizar la instalación existente conservando datos, usar el APK de Releases verificado; no desinstalar para resolver diferencias de firma. Las claves privadas de Android Studio no se suben a GitHub.

## Ejecutar el código descargado

Clonar el repositorio o descargar las fuentes, abrir android/ en Android Studio y usar JDK 21 y el SDK requerido. Debian puede seguir sirviendo el backend ya instalado. Para desarrollo con backend Windows, seguir README.md y cambiar la URL desde el engranaje de acceso.

No se versionan H2/SQLite reales, contraseñas, archivos .env, local.properties, cachés ni claves de firma. Los ejemplos dev contienen únicamente credenciales públicas de laboratorio. No se asignó una licencia nueva sin indicación del propietario.

## Exposición

Mostrar el repositorio, el APK de Releases y las ejecuciones de Actions. Después demostrar la aplicación móvil junto a Postman: el envío desde el segundo usuario debe aparecer en Android mediante WebSocket, con comprobante guardado en Room. La publicación del código no sustituye esa demostración ni el informe APA 7 pendiente.
