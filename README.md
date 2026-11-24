# PROJECT INFINITY — Android

Aplicación Android nativa desarrollada con Kotlin y Jetpack Compose. INFINITY ofrece un espacio de trabajo con estética de escritorio, ventanas y herramientas del sistema, adaptado a tablets y teléfonos Android.

## Funciones disponibles

- **Escritorio INFINITY:** espacios de trabajo, ventanas, accesos directos y personalización visual.
- **INFINITY FILE MANAGER:** navegación por carpetas autorizadas, búsqueda, orden por nombre/tipo/tamaño/fecha, vistas Lista y Cuadrícula, selección múltiple, crear carpetas, renombrar, copiar, cortar, pegar/mover, eliminar con confirmación y detalles de archivos.
- **Acceso a archivos:** utiliza el Storage Access Framework (SAF) oficial de Android. El usuario elige explícitamente las ubicaciones; INFINITY no intenta eludir las restricciones de almacenamiento de Android.
- **INFINITY ORBIT:** indicadores de batería, memoria, almacenamiento y estado térmico, con actualización periódica.
- **INFINITY ADVANCED:** ajustes rápidos y centro de notificaciones. El acceso requiere autorización especial del usuario en los ajustes de Android. La acción LIMPIAR borra la lista local de INFINITY; DESCARTAR solicita a Android retirar la notificación seleccionada. El feed limita su tamaño y sanitiza texto de notificaciones; el modo privacidad oculta títulos y mensajes.
- **INFINITY CONNECT:** administración de perfiles, validación local y diagnóstico TCP. **La sesión RDP real aún no está implementada**; un perfil guardado o un puerto accesible no demuestran identidad verificada ni escritorio remoto activo.

## Requisitos técnicos

- Android Studio / Android Gradle Plugin compatible con el proyecto.
- JDK 17.
- Gradle 8.9.
- Android compileSdk 35 / targetSdk 35.
- Android mínimo: API 26 (Android 8.0).
- Kotlin 2.0.21 y Jetpack Compose.

## Verificación durante el desarrollo

Durante esta etapa el repositorio utiliza verificación de código fuente sin generar APK:

1. Abre la pestaña **Actions** del repositorio.
2. Selecciona **PROJECT INFINITY - Pull Request Verification**.
3. Revisa la compilación Kotlin, las pruebas unitarias, las verificaciones de estructura/seguridad y Android Lint.
4. Consulta los pasos fallidos y sus registros antes de considerar aceptado un paquete.

**Política actual:** no ejecutar tareas `assembleDebug` o `assembleRelease`, no firmar APK y no publicar artefactos APK hasta que el usuario autorice expresamente una compilación. Un workflow verde de código fuente no equivale a un APK listo para instalar.

## Seguridad y privacidad

- `allowBackup=false` y reglas de extracción de datos.
- Tráfico HTTP sin cifrar deshabilitado mediante `usesCleartextTraffic=false`.
- No solicita permisos generales de almacenamiento; utiliza permisos SAF concedidos por el usuario.
- Las credenciales no deben incluirse en perfiles, registros, portapapeles ni telemetría.
- El contenido de las notificaciones puede ocultarse desde los controles de privacidad.
- La autorización de acceso a notificaciones se concede o revoca desde los ajustes especiales de Android.
- No exponer TCP 3389 directamente a Internet. Para redes diferentes, utilizar una VPN privada y autenticada.

## Estado y límites conocidos

- El workflow actual verifica código fuente, pruebas unitarias y lint; no genera ni valida APK.
- Las operaciones de copiar/mover dependen de que el proveedor SAF elegido por Android las admita. Algunos proveedores pueden rechazarlas.
- La apertura de documentos se delega a aplicaciones Android compatibles; no todos los formatos tienen vista previa integrada.
- INFINITY CONNECT administra perfiles y realiza diagnósticos limitados; todavía no establece sesiones RDP reales.
- El Windows Companion disponible en esta línea proporciona identidad, no captura de pantalla ni control remoto.
- El pipeline de frames es una base de procesamiento de datos, no una transmisión remota completa.
- Antes de publicar una versión definitiva se deben completar las pruebas funcionales en teléfono/tablet, validar los flujos de permisos y probar la conexión real con Windows en dispositivos físicos.

## Arquitectura

Consulta [docs/INFINITY-ARCHITECTURE.md](docs/INFINITY-ARCHITECTURE.md) para conocer la arquitectura y sus fases. El contrato de identidad, emparejamiento y confianza se documenta en [docs/INFINITY-CONNECT-PROTOCOL.md](docs/INFINITY-CONNECT-PROTOCOL.md).

