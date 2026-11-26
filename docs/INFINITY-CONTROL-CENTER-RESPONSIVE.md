# PACK 07 — INFINITY CONTROL CENTER

## Alcance
- El panel flotante adapta su ancho al viewport disponible en lugar de asumir siempre 350 dp.
- La política define límites seguros de altura y columnas para diseños compactos y tablet.
- La integración actual aplica el ancho adaptativo al panel existente sin cambiar sus controles, preferencias ni persistencia.

## Límites
- No añade interruptores Android de Wi-Fi/Bluetooth ni permisos privilegiados.
- No altera configuraciones del sistema operativo; sólo adapta la superficie propia de INFINITY.
- La prueba unitaria valida la política de medidas; se requiere prueba visual en teléfono/tablet para confirmar comportamiento real.

## Validación
La CI del PR debe ejecutar tests Debug/Release, lint y builds Debug/Release firmados.
