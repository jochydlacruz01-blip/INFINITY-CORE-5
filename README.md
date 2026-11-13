# INFINITY CORE 0.5 — Fase 5: INFINITY Hub

Entorno de escritorio Android nativo construido con Kotlin y Jetpack Compose.

## Novedades de Fase 5
- Explorador de archivos funcional mediante el selector oficial SAF de Android.
- Recuerda la carpeta autorizada entre reinicios, conservando el permiso persistente concedido por Android.
- Lista carpetas y archivos, permite entrar en subcarpetas y volver a la carpeta inicial.
- Abre archivos con las aplicaciones Android compatibles instaladas en la tablet.
- Buscador de aplicaciones por nombre o identificador de paquete.
- Conserva la personalización de Fase 4: cinco temas, modo claro/oscuro, fondo, tamaño de iconos y el icono multicolor INFINITY.

## Compilar en GitHub Actions
El workflow está en `.github/workflows/android.yml`.
1. Descomprime el ZIP.
2. Sube el contenido de la carpeta del proyecto a la raíz del repositorio, incluyendo `.github`.
3. Haz commit en `main` o `master`.
4. Abre Actions y espera **Build INFINITY CORE 0.5**.
5. Descarga el artefacto `INFINITY-CORE-0.5-debug`.

## Requisitos
- JDK 17
- Gradle 8.9
- Android compileSdk 35
- Android mínimo 8.0 (API 26)

## Alcance y permisos
INFINITY solo puede explorar la carpeta que el usuario elige explícitamente en el selector de Android. No accede silenciosamente a todo el almacenamiento. La apertura de un archivo depende de que exista una aplicación compatible. Esta fase no incluye todavía copiar, mover, renombrar ni eliminar archivos.

El paquete fuente se ha validado estructuralmente, pero debe compilarse en GitHub Actions; no se presenta como APK ya probado.
