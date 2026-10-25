# Relación con la rúbrica del profesor

Fuente: `PROYECTO FINAL DE LA UC 1.docx`, proporcionado por el usuario. La rúbrica suma 20 puntos; esta matriz relaciona implementación y evidencia, sin atribuir una calificación.

| Criterio, máximo | Implementación | Evidencia existente | Validación que falta |
|---|---|---|---|
| Room, 4 | Entidades, DAO, SQLite, Flow, comprobantes y saldo persistentes; claves por servidor/cuenta | Instrumentación actual aprobada; UI conservó saldo, historial y UUID al reabrir offline contra Debian | Comprobar eventos recibidos durante una desconexión |
| REST, 4 | Depósito, retiro, transferencia y servicio; errores de fondos/datos/autorización | Pruebas HTTP y cuatro diálogos contra Windows; depósito, transferencia y replay contra Debian | Retiro/servicios desde UI contra Debian; respuesta perdida después del commit |
| WebSocket, 4 | Túnel real autenticado, eventos después del commit, conciliación automática | Transferencia entrante en instrumentación Debian y UI reconectada tras recuperar servicio | Movimientos producidos mientras el cliente está desconectado; reinicio completo de VM |
| Concurrencia, 4 | viewModelScope/Main, IO, suspend, Flow/StateFlow, Mutex; bloqueos y transacciones servidor | Pruebas paralelas de duplicados, sobregiros y transferencias; recuperación con UUID sin duplicados en Debian | Perfilado prolongado y pérdida de respuesta posterior al commit |
| UI/UX, 4 | Compose/Material 3, registro/login, perfil, cuatro acciones, comprobantes, estados y temas Sistema/Claro/Oscuro | Cuatro formularios operados; capturas Debian offline, pendiente y recuperada revisadas | Revisión en dispositivo del evaluador |

## Entrega académica

- Código fuente Android y backend: implementado y organizado.
- APK: compilado, con firma de depuración para laboratorio; la versión de distribución requiere firma propia.
- Repositorio: Git local preparado. Falta configurar y publicar un remoto para obtener el enlace académico.
- Defensa: guía técnica y colección Postman incluidas. Fecha indicada: 29 de octubre de 2026.
- Informe APA 7: deliberadamente pendiente, conforme a la solicitud de no redactarlo todavía.
- Debian/VirtualBox: desplegado y conectado a Android. Servicio activo/habilitado, reinicio del servicio y recuperación aprobados; reinicio completo de VM pendiente.

Evidencias actuales: `docs/evidence/android-debian-verification.json`, `android-debian-instrumentation.log` y capturas asociadas. Fecha local: 17 de octubre de 2026; JSON y emulador registran UTC.

## Actualización del 24 de octubre

Registro/login sin correo y apariencia son mejoras solicitadas por el usuario. Se mantienen Android/Kotlin, Room, REST, WebSocket, corrutinas y backend Spring Boot en Debian. Usuarios, contraseñas con hash y sesiones se guardan en la H2 existente; Room sigue cumpliendo su función local. Esto no sustituye los criterios del profesor ni implica una nota. Los resultados nuevos están separados en `RESULTADOS.md`; el APK 1.1.0 aún debe instalarse en el teléfono del usuario para comprobar esta versión allí.
