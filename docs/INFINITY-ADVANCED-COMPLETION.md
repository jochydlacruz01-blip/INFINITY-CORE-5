# INFINITY ADVANCED — entrega y aceptación

## Alcance implementado en esta entrega

- Agrega un único panel que consume las capacidades existentes de notificaciones, métricas del dispositivo, conectividad y preferencias del escritorio.
- Muestra memoria y almacenamiento cuando Android permite obtenerlos. CPU se mantiene como "No disponible": la API estándar no ofrece una medición app-scoped fiable.
- Permite abrir los ajustes oficiales de Android para Wi-Fi, Bluetooth, pantalla, sonido y acceso a notificaciones; no simula controles que Android no expone.
- Mantiene el feed de notificaciones existente, con modo privado activado por defecto y sin persistir títulos ni cuerpos cuando la protección está activa.
- Permite seleccionar los cinco temas que ya soporta el escritorio: midnight, ocean, violet, emerald y sunset.
- Incluye una automatización de bajo riesgo: abrir una aplicación instalada solo mediante acción manual, con regla habilitada, consentimiento explícito, validación de paquete, cooldown de 60 segundos y límite de diez ejecuciones por hora.
- Genera un informe compartible con métricas actuales y metadatos no sensibles. No inventa históricos ni exporta contenido de notificaciones.

## Límites deliberados de seguridad

- No hay ejecución automática en segundo plano, Accessibility Service, cambios silenciosos de ajustes, acciones de red ni acceso a archivos desde la automatización inicial.
- El usuario puede revocar el consentimiento o deshabilitar la regla desde el propio panel.
- La lista de notificaciones permanece en el almacén existente; ADVANCED no crea un segundo feed.
- La apertura de ajustes es delegada a Android y puede no estar disponible en todos los fabricantes.
- La telemetría puede aparecer como no disponible si Android o el dispositivo no ofrecen el dato.

## Verificación de software

La aceptación de software requiere que el workflow `PROJECT INFINITY - Pull Request Verification` complete correctamente compilación Kotlin, pruebas unitarias, verificaciones de seguridad y Android Lint. La prueba Windows Companion es una validación separada y no sustituye las pruebas Android.

## Aceptación física pendiente

Antes de considerar el módulo completamente aceptado en producto, validar en teléfono y tablet reales:

1. Cambiar cada tema y confirmar que escritorio, ventanas y dock se actualizan sin reiniciar.
2. Abrir Wi-Fi, Bluetooth, pantalla, sonido y ajustes de notificaciones; volver a INFINITY sin cierre inesperado.
3. Autorizar y revocar Notification Listener; comprobar conexión, reconexión, abrir y descartar notificaciones.
4. Activar modo privado y confirmar que no aparecen títulos ni mensajes en el feed ni en el informe.
5. Ejecutar una aplicación válida con consentimiento; probar paquete inexistente, regla desactivada, revocación del consentimiento, cooldown y límite horario.
6. Compartir el informe y comprobar que no contiene texto de notificaciones, identificadores privados ni credenciales.
7. Repetir en orientación vertical/horizontal y en pantalla de teléfono/tablet.

No se habilita CONNECT remoto ni transferencia de archivos por esta entrega. Tampoco se declara un APK generado: los workflows de esta etapa son source-only.
