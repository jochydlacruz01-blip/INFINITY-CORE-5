# PROJECT INFINITY — Android

Aplicación Android nativa desarrollada con Kotlin y Jetpack Compose. INFINITY ofrece un espacio de trabajo con estética de escritorio, ventanas y herramientas del sistema, adaptado a tablets y teléfonos Android.

## Funciones disponibles

- **Escritorio INFINITY:** espacios de trabajo, ventanas, accesos directos y personalización visual.
- **INFINITY FILE MANAGER:** navegación por carpetas autorizadas, búsqueda, orden por nombre/tipo/tamaño/fecha, vistas Lista y Cuadrícula, selección múltiple, crear carpetas, renombrar, copiar, cortar, pegar/mover, eliminar con confirmación y detalles de archivos.
- **Acceso a archivos:** utiliza el Storage Access Framework (SAF) oficial de Android. El usuario elige explícitamente las ubicaciones; INFINITY no intenta eludir las restricciones de almacenamiento de Android.
- **INFINITY ORBIT:** indicadores de batería, memoria, almacenamiento y estado térmico, con actualización periódica.
- **INFINITY ADVANCED:** ajustes rápidos y centro de notificaciones. El acceso requiere autorización especial del usuario en los ajustes de Android. La acción LIMPIAR borra la lista local de INFINITY; DESCARTAR solicita a Android retirar la notificación seleccionada. El feed limita su tamaño y sanitiza texto de notificaciones; el modo privacidad oculta títulos y mensajes.
- **INFINITY CONNECT:** perfiles de equipos remotos, edición, validación de puerto, prevención de duplicados y copia de dirección. **El cliente RDP integrado todavía no está implementado**, por lo que guardar un perfil no significa que la conexión remota esté disponible o verificada.

## Requisitos técnicos

- Android Studio / Android Gradle Plugin compatible con el proyecto.
- JDK 17.
- Gradle 8.9.
- Android compileSdk 35 / targetSdk 35.
- Android mínimo: API 26 (Android 8.0).
- Kotlin 2.0.21 y Jetpack Compose.

## Compilar con GitHub Actions

El workflow se encuentra en `.github/workflows/android.yml` y se ejecuta al enviar cambios a la rama `INFINITY-0.9`.

1. Abre la pestaña **Actions** del repositorio.
2. Selecciona el workflow **PROJECT INFINITY 0.10 - APK BUILD**.
3. Espera a que terminen la compilación Debug/Release, el análisis estático, las pruebas unitarias disponibles y la verificación de firma del APK Release.
4. Descarga los artefactos Debug o Release firmado cuando el proceso termine correctamente.

### Secretos necesarios para firmar Release

Configura estos secretos en GitHub Actions; nunca guardes el archivo keystore ni sus contraseñas en el repositorio:

- `INFINITY_KEYSTORE_BASE64`
- `INFINITY_KEYSTORE_PASSWORD`
- `INFINITY_KEY_ALIAS`
- `INFINITY_KEY_PASSWORD`

El workflow utiliza el keystore existente. No lo regeneres si necesitas conservar la capacidad de actualizar instalaciones anteriores.

## Seguridad y privacidad

- `allowBackup=false` y reglas de extracción de datos.
- Tráfico HTTP sin cifrar deshabilitado mediante `usesCleartextTraffic=false`.
- No solicita permisos generales de almacenamiento; utiliza permisos SAF concedidos por el usuario.
- INFINITY CONNECT no guarda contraseñas de Windows.
- El contenido de las notificaciones puede ocultarse desde los controles de privacidad.
- La autorización de acceso a notificaciones se concede o revoca desde los ajustes especiales de Android.

## Estado y límites conocidos

- Las compilaciones de GitHub verifican que el código compile y que el APK Release tenga una firma válida; no sustituyen las pruebas funcionales en dispositivos físicos.
- Las operaciones de copiar/mover dependen de que el proveedor SAF elegido por Android las admita. Algunos proveedores pueden rechazarlas.
- La apertura de documentos se delega a aplicaciones Android compatibles; no todos los formatos tienen vista previa integrada.
- INFINITY CONNECT administra perfiles, pero aún no establece sesiones RDP.
- Antes de publicar una versión definitiva se deben completar las pruebas funcionales en teléfono/tablet y validar los flujos de permisos en las versiones Android objetivo.
