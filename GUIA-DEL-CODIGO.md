# Guía para leer el código

## 1. Empezar por el ejemplo Spring Boot

Las clases están en `backend/graphdb-demo/src/main/java/mx/com/ejemplo/demo/`.

| Clase | Qué explica |
|---|---|
| `Application` | Arranque de Spring Boot y activación del escaneo de repositorios |
| `GraphDbConfig` | Construcción del cliente RDF4J HTTPRepository y GraphDbTemplate |
| `Libro` | Correspondencia de cada campo Java con una propiedad RDF |
| `LibroRepository` | Operaciones derivadas y consulta SPARQL con parámetros |
| `LibroController` | Endpoints REST, validación, PageRequest y DTO de respuesta |
| `ApiErrors` | Respuestas de error de validación y acceso a datos |

Lee `Libro` y `LibroRepository` primero. Después, sigue una llamada a `crear` o `listar` desde `LibroController`. Los records `Entrada` y `Pagina` permanecen dentro del controlador para conservar su API Java y mostrar el contrato HTTP junto a sus usos.

Por ejemplo, al listar, el controlador recibe los parámetros de la URL, valida página/tamaño/orden, construye `PageRequest` y llama al método del repositorio que corresponde a los filtros. La fábrica de la biblioteca genera la implementación de ese método.

## 2. Explorar la biblioteca Spring Data

Las clases están en `backend/spring-data-graphdb/src/main/java/mx/com/ejemplo/graphdb/`.

| Clase o anotación | Responsabilidad |
|---|---|
| `RdfEntity`, `RdfProperty` | Metadatos de tipo RDF, namespace, grafo y predicados |
| `GraphDbRepository` | Interfaz base de CRUD, listas, orden y paginación |
| `SimpleGraphDbRepository` | Implementación de operaciones CRUD delegadas a la plantilla |
| `RdfMapper` | Conversión entre campos Java, IRIs y literales RDF |
| `GraphDbTemplate` | Conexiones, transacciones por operación y ejecución RDF/SPARQL |
| `GraphDbRepositoryQuery` | Conversión de métodos derivados a filtros y ejecución de consultas explícitas |
| `GraphDbRepositoryFactory` | Integración con RepositoryFactorySupport de Spring Data |
| `GraphDbRepositoryFactoryBean` | Creación de la fábrica dentro del contexto Spring |
| `EnableGraphDbRepositories`, `GraphDbRepositoriesRegistrar` | Escaneo y registro de interfaces de repositorio |
| `SparqlQuery` | Declaración de consultas SELECT de sujetos con parámetros |

Para seguir `save`, lee primero `SimpleGraphDbRepository.save`, luego `GraphDbTemplate.saveAll` y finalmente `RdfMapper.statements`. La escritura reemplaza las propiedades administradas por el mapper dentro del grafo configurado. Conserva los triples ajenos al mapeo.

Para entender `findByTituloContainingIgnoreCase`, lee `GraphDbRepositoryQuery`. `PartTree` interpreta el nombre del método y los valores se envían mediante bindings de RDF4J. `GraphDbTemplate` selecciona los sujetos y carga sus propiedades.

Las limitaciones originales siguen aplicando: campos escalares, transacción por operación RDF4J y ausencia de integración con `@Transactional` de Spring. El cambio de formato no agrega funciones nuevas al motor.

## 3. Leer el cliente Angular

| Archivo dentro de `cliente-angular-graphdb/` | Responsabilidad |
|---|---|
| `src/main.ts` | Arranque, HttpClient y detección de cambios sin Zone.js |
| `src/app/libros-api.service.ts` | Interfaces TypeScript, validación de respuestas y solicitudes HTTP |
| `src/app/app.component.ts` | Formularios, signals, búsqueda, páginas, guardado y borrado |
| `src/app/app.component.html` | Filtros, tabla, confirmación de borrado y editor |
| `src/styles.css` | Estilos, estados y distribución adaptable |
| `proxy.conf.json` | Envío de `/api/**` al backend durante desarrollo |

En `AppComponent`, las secciones están separadas para distinguir estado de pantalla, formularios y operaciones. El ID se conserva en `editandoId`, fuera del formulario editable. Los datos de un libro se envían al servicio y, tras una escritura correcta, el componente vuelve a consultar la lista.

## 4. Puntos habituales de modificación

- Para cambiar un campo RDF, empieza por `Libro`, después actualiza `Entrada` en el controlador y los tipos/formulario de Angular.
- Para agregar una búsqueda, declara el método en `LibroRepository` usando un operador soportado; luego conecta el filtro en el controlador y el cliente.
- Para modificar colores o tamaños, usa `src/styles.css`.
- Para cambiar el puerto del backend durante desarrollo, modifica `proxy.conf.json` y reinicia `npm start`.
- Para agregar otra entidad, crea su clase y repositorio con un namespace adecuado; consulta las limitaciones del mapper en `backend/README.md`.
