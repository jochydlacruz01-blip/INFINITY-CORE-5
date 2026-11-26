# PACK 10 — FINAL INTEGRATION GATE

## Alcance de la verificación automatizada actual
El workflow `PROJECT INFINITY - Pull Request Verification` ejecuta una puerta de integración de código fuente sin generar APK:

- Verifica estructura del proyecto y valores de seguridad del AndroidManifest.
- Impide comandos de compilación/publicación de APK en los workflows y Codemagic.
- Comprueba el inventario integrado de entregables PACK 01–14 y ejecuta sus pruebas Python.
- Ejecuta auditoría estática del AndroidManifest y sus pruebas.
- Compila Kotlin para Debug y AndroidTest, ejecuta pruebas unitarias Debug/Release y Android Lint Debug/Release.
- Comprueba la revisión fijada del código fuente FreeRDP.
- Conserva los informes de pruebas y lint como artefactos de diagnóstico.

La ejecución de UI instrumentada en emuladores está pausada. Este workflow no ejecuta builds APK, firma ni publica APK.

## Qué NO certifica
- No sustituye pruebas de uso en tablet/teléfono, TalkBack, rotación, consumo, rendimiento o compatibilidad real.
- No demuestra interoperabilidad real Android–Windows ni una sesión RDP funcional en hardware.
- La presencia de los módulos CONNECT/FreeRDP en el inventario no certifica que transporte, emparejamiento, autenticación o entrada remota hayan superado pruebas físicas.
- Un CI verde de código fuente no equivale a una versión lista para instalar o publicar.

## Criterios para cerrar la fase de escritorio
1. PACK 01–10 presentes en el inventario integrado y CI verde en el SHA final.
2. Resolver conflictos entre ramas apiladas antes de integrar.
3. Completar pruebas funcionales y de accesibilidad en dispositivos Android reales.
4. Revisar permisos, privacidad, estabilidad, rendimiento y compatibilidad teléfono/tablet.
5. La compilación, firma y validación de APK se harán únicamente después de autorización expresa.
6. No iniciar el siguiente paquete CONNECT/Fase III hasta que la puerta de integración de Fase II y las pruebas de aceptación requeridas estén cerradas.
