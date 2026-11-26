# INFINITY BEYOND LIMITS — FASE II: PUERTA DE CIERRE

## Alcance
Esta puerta valida el escritorio Android nativo INFINITY y sus componentes locales. No incluye el transporte remoto ni el emparejamiento de INFINITY CONNECT (Fase III).

Componentes que deben integrarse sin regresiones:
- Escritorio futurista, identidad visual y adaptación teléfono/tablet.
- START: búsqueda, categorías, catálogo y apertura de aplicaciones.
- Dock: acciones, tareas, desbordamiento y tamaños compactos.
- AURORA: creación, foco, orden Z, movimiento, redimensionado, minimizar, maximizar, restaurar y cierre.
- ORBIT: tres espacios de trabajo, cambio de espacio y persistencia.
- Control Center: conectividad, brillo, volumen, accesos a ajustes y estado del dispositivo.
- ADVANCED: notificaciones, privacidad, apertura/descartado y acciones rápidas.

## Estado automatizado comprobado
Última verificación registrada para esta línea:
- START y adaptación visual: commit `83b126ad28572355bc18d32020719f4d449ffcc6`; verificación de código y workflow DEBUG completados con éxito.
- Corrección de filas de estado de Control Center: commit `0ed79857dd3a51ccb24d90a6f7e406bce7cd7848`; verificación de código y workflow DEBUG completados con éxito.
- Los workflows DEBUG generan únicamente un artefacto de prueba; no constituyen una compilación comercial ni una aprobación de publicación.

Antes del cierre se debe ejecutar de nuevo la verificación completa sobre el SHA final de esta rama, incluyendo compilación, pruebas unitarias, Android Lint, políticas de manifiesto, inventario y pruebas de integración disponibles.

## Aceptación funcional pendiente
La CI no certifica la experiencia física. Registrar evidencia para cada punto:
1. Teléfono Android: inicio, escritorio, iconos, START, Dock, ventanas y paneles en anchuras compactas.
2. Tablet Android: composición de referencia, densidad, masthead, reloj, Dock y ventanas.
3. Rotación y cambios de tamaño: no perder foco, posición, contenido ni acceso a controles.
4. ORBIT: cambiar entre Principal, Trabajo y Personal; confirmar aislamiento y persistencia.
5. AURORA: mover/redimensionar por los controles previstos; cancelar gestos sin dejar geometría corrupta; minimizar/restaurar/maximizar/cerrar.
6. START y Dock: búsqueda, categorías, apertura de apps y acceso a todas las acciones sin solapamientos.
7. Control Center y ADVANCED: verificar accesos del sistema, notificaciones, ocultación de contenido, limpieza y permisos.
8. Accesibilidad: TalkBack, etiquetas, tamaño de objetivos táctiles, contraste y navegación.
9. Estabilidad: sesiones prolongadas, memoria, consumo, errores y recuperación tras volver de Ajustes o cambiar de aplicación.

## Seguridad y límites
- Solicitar permisos solo cuando la función los necesite y explicar su finalidad.
- Mantener el contenido de notificaciones oculto por defecto y comprobar que la preferencia se aplica también al almacenamiento.
- No exponer RDP/3389 directamente a Internet.
- No activar funciones remotas ni declarar interoperabilidad Android–Windows como aprobada en esta fase.
- No generar APK Release, firmar para distribución ni publicar sin autorización expresa.

## Condición de cierre
Fase II solo puede declararse cerrada cuando:
- Todos los componentes anteriores están presentes y pasan la regresión sobre el mismo SHA final.
- No hay workflows pendientes o fallidos.
- Los casos físicos teléfono/tablet se ejecutan y documentan con resultados verificables.
- No quedan defectos críticos/altos abiertos; los defectos restantes tienen decisión documentada.
- La documentación coincide con el código y el alcance real.

Un CI verde es necesario, pero no suficiente. Si no hay acceso físico a uno de los dispositivos, ese punto se marca como pendiente de aceptación y no se sustituye por una suposición.

## Separación de fases
CONNECT, emparejamiento seguro, transporte, sesión Windows/RDP y transferencia remota pertenecen a Fase III. No se inicia su cierre como parte de esta puerta de Fase II.
