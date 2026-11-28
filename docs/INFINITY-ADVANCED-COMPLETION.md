# INFINITY ADVANCED — entrega y aceptación

## Alcance implementado en esta entrega

- Agrega un único panel que consume las capacidades existentes de notificaciones, métricas del dispositivo, conectividad y preferencias del escritorio.
- Muestra memoria, porcentaje de batería, estado de carga, temperatura de batería y estado térmico cuando Android permite obtenerlos. CPU se mantiene como "No disponible": la API estándar no ofrece una medición app-scoped fiable.
- El porcentaje de almacenamiento corresponde al volumen de datos del dispositivo, no al tamaño exclusivo ocupado por la aplicación.
- Permite abrir los ajustes oficiales de Android para Wi-Fi, Bluetooth, pantalla, sonido y acceso a notificaciones; no simula controles que Android no expone.
- Mantiene el feed de notificaciones existente, con modo privado activado por defecto y sin persistir títulos ni cuerpos cuando la protección está activa.
- Permite seleccionar los cinco temas que ya soporta el escritorio: midnight, ocean, violet, emerald y sunset.
- Incluye una automatización de bajo riesgo: abrir una aplicación instalada solo mediante acción manual, con regla habilitada, consentimiento explícito, validación de paquete, cooldown de 60 segundos y límite de diez ejecuciones dentro de una ventana móvil de 60 minutos.
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

1. Cambiar cada tema desde ADVANCED y CONTROL CENTER; confirmar que escritorio, ventanas y dock se actualizan sin reiniciar y que ambos selectores muestran la misma selección.
2. Abrir Wi-Fi, Bluetooth, pantalla, sonido y ajustes de notificaciones; volver a INFINITY sin cierre inesperado.
3. Autorizar y revocar Notification Listener; comprobar conexión, reconexión, abrir y descartar notificaciones.
4. Activar modo privado y confirmar que no aparecen títulos ni mensajes en el feed ni en el informe.
5. Ejecutar una aplicación válida con consentimiento; probar paquete inexistente, regla desactivada, revocación del consentimiento, cooldown y límite horario.
6. Compartir el informe y comprobar que no contiene texto de notificaciones, identificadores privados ni credenciales.
7. Repetir en orientación vertical/horizontal y en pantalla de teléfono/tablet.

No se habilita CONNECT remoto ni transferencia de archivos por esta entrega. Tampoco se declara un APK generado: los workflows de esta etapa son source-only.

## Estado de aceptación — 27 de noviembre de 2026

La implementación de ADVANCED se conserva en esta rama, pero la aceptación integral queda **ABIERTA**. Por decisión de planificación, las simulaciones Android y las pruebas físicas se aplazan hasta que termine la programación de todos los módulos. Este aplazamiento no significa que las pruebas hayan fallado ni que hayan sido aprobadas.

### Validaciones pendientes (no ejecutar durante la fase de programación)

- [ ] **Automatización:** en teléfono y tablet, probar ejecución manual con consentimiento; paquete inexistente; regla deshabilitada; revocación del consentimiento; cooldown de 60 segundos y límite de diez ejecuciones por ventana móvil de 60 minutos. Confirmar que no se ejecuta nada en segundo plano.
- [ ] **Privacidad de notificaciones:** activar/desactivar el modo privado y comprobar el feed existente, notificaciones nuevas y entradas ya recibidas; confirmar que títulos y cuerpos sensibles no quedan visibles ni retenidos cuando la protección está activa.
- [ ] **Permisos:** conceder y revocar Notification Listener desde Ajustes Android; comprobar conexión, reconexión, estado del permiso y recuperación del feed sin borrar datos locales ajenos al servicio.
- [ ] **Informe compartido:** compartir mediante aplicaciones reales y revisar el contenido recibido; no deben aparecer textos de notificaciones, nombres de aplicaciones, package names, identificadores, rutas, credenciales ni tokens.
- [ ] **Temas:** cambiar los cinco temas desde ADVANCED y CONTROL CENTER; confirmar coherencia inmediata en escritorio, ventanas, barras y dock, sin reiniciar.
- [ ] **Ajustes del sistema:** abrir Wi-Fi, Bluetooth, pantalla, sonido y acceso a notificaciones; regresar a INFINITY y comprobar que la actividad y el estado del panel sobreviven.
- [ ] **Orientación y tamaños:** repetir los recorridos en vertical y horizontal, en teléfono y tablet; revisar scroll, márgenes, controles accesibles y ausencia de recortes.
- [ ] **Regresión final:** ejecutar la batería instrumentada Android y la aceptación física completa después de cerrar los demás módulos.

### Evidencia requerida para cerrar

Registrar dispositivo/modelo, versión Android, build/commit probado, pasos ejecutados, resultado esperado/observado y evidencia de cada caso. No marcar esta sección como completada por una compilación, lint o pruebas unitarias solamente. Hasta entonces, ADVANCED permanece **implementado en código, aceptación Android/física pendiente**.

### Dependencia de CI

La verificación de fuente/unit tests/lint del commit actual debe confirmarse en el workflow asociado a ese SHA. Las simulaciones instrumentadas permanecen diferidas. No se declara APK generado ni listo en esta etapa source-only.
