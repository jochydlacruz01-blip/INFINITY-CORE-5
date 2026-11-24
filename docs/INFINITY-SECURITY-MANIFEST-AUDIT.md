# PACK 09 — SECURITY CORE: manifest audit gate

## Protecciones verificadas automáticamente
- Copias de seguridad de aplicación deshabilitadas.
- Tráfico HTTP sin cifrar deshabilitado.
- El manifiesto no puede habilitar debuggable.
- Cada componente Android declara explícitamente android:exported.
- Los componentes exportados requieren permiso, salvo la Activity principal con MAIN/LAUNCHER.
- El Notification Listener sólo se admite exportado con BIND_NOTIFICATION_LISTENER_SERVICE.

## Límites
Este control estático no sustituye revisión de dependencias, análisis dinámico, pruebas de permisos/revocación, revisión de datos en reposo ni pruebas en dispositivo. No certifica la aplicación como segura.
