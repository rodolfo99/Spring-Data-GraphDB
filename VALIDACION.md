# Validación de la edición legible v2

Fecha: 9 de septiembre de 2026.

| Comprobación | Resultado |
|---|---|
| Maven `verify`, biblioteca y ejemplo | BUILD SUCCESS |
| Pruebas de repositorios Java | 12 aprobadas |
| Pruebas web Spring Boot | 2 aprobadas |
| Compilación Angular de producción | Correcta |
| Pruebas Angular | 10 aprobadas |
| Sintaxis de scripts Python y shell | Correcta |
| POM XML | Analizados correctamente |
| Integridad de JAR y ZIP | Correcta |

**24 pruebas aprobadas en total, sin fallos.** Los reportes están en `evidencia/`.

Se ejecutaron las pruebas existentes después de aplicar el nuevo formato. La versión conserva Java 17 como objetivo de compilación, Spring Boot 3.5.3, RDF4J 5.1.3 y Angular 22.0.8.

La edición incluye 23 archivos Java formateados, fuentes TypeScript/HTML/CSS organizadas, POM expandidos y scripts más legibles. Los JAR incluidos se compilaron desde el código de esta edición.

## Alcance de la comprobación

La prueba opcional `GraphDbHttpTest` se omitió porque no se proporcionó `GRAPHDB_TEST_URL`. No se ejecutó Docker ni un CRUD completo contra GraphDB real en esta preparación. Las pruebas Java usan RDF4J MemoryStore y las de Angular usan HTTP simulado. Las pruebas web comprueban el arranque y la validación de peticiones.

No se realizó una revisión visual en navegador ni una prueba de carga. La edición cambia formato, comentarios y algunos nombres de variables locales; no agrega funciones nuevas ni cambia el contrato HTTP.
