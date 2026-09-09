# Spring Data GraphDB — implementación propia 0.1.0

Biblioteca reutilizable para **Ontotext GraphDB**, construida sobre **Spring Data Commons y Eclipse RDF4J**. Incluye un backend Spring Boot de libros. No es un proyecto oficial de Spring, Ontotext ni Eclipse, ni pretende implementar todas las capacidades de Spring Data JPA.

La interfaz del usuario se implementa con `RepositoryFactorySupport`, `RepositoryFactoryBeanSupport` y `RepositoryQuery`. Las consultas derivadas se analizan mediante `PartTree` de Spring Data; no son métodos escritos manualmente en el controlador.

## Inicio rápido: todo con Docker

Requiere Docker con Compose v2, acceso a Internet para descargar imágenes/dependencias y aproximadamente 3–4 GB de RAM disponibles. No necesita Java ni Maven instalados en el equipo para esta opción.

Desde la carpeta que contiene `compose.yaml`:

```bash
docker compose up -d --build
docker compose logs -f backend
```

El primer arranque compila el proyecto y ejecuta las pruebas. GraphDB inicia; el servicio `graphdb-init` espera al servidor y crea `libros` si no existe. Después arranca el backend. Espere el mensaje `Started Application`.

- GraphDB Workbench: http://localhost:7200
- API: http://localhost:8080/api/libros
- Se publica únicamente en localhost; no hay autenticación en el ejemplo local.
- Si Docker requiere permisos en su equipo, anteponga `sudo` a sus comandos Docker.
- El servicio de inicialización debe terminar con código 0; no tiene que seguir ejecutándose.

Prueba del backend (requiere Python 3, sin paquetes adicionales):

```bash
python3 scripts/smoke-test.py
```

Comprueba creación, búsqueda, dos actualizaciones, conservación del ID, consulta, eliminación y repetición del borrado. Solo elimina el libro que crea.

Detener conservando los datos:

```bash
docker compose down
```

Los datos viven en el volumen `graphdb-data`. `docker compose down -v` los eliminaría; no lo necesita para reiniciar.

## JAR compilados incluidos

La carpeta `dist/` contiene la biblioteca reutilizable y el backend ejecutable. Para probar el backend sin compilar, requiere Java 17+ y GraphDB:

```bash
docker compose up -d graphdb
docker compose run --rm graphdb-init
java -jar dist/graphdb-demo-0.1.0.jar
```

El JAR del backend incluye sus dependencias. El JAR de la biblioteca es una dependencia Maven, no una aplicación ejecutable. Para modificar el código o instalar la biblioteca en Maven, siga las instrucciones siguientes.

## Ejecutar Java localmente y GraphDB en Docker

JDK **17 o superior** y Maven **3.9+**. El bytecode del proyecto apunta a Java 17. El POM fija únicamente los módulos RDF4J utilizados; deja Jackson y las bibliotecas compartidas bajo la gestión de Spring Boot para evitar conflictos de versiones. Fue compilado con JDK 17; no se probaron aquí todas las versiones superiores de Java. Las versiones están fijadas, no representan una promesa de usar la versión más reciente:

| Componente | Versión fijada |
|---|---|
| Spring Boot | 3.5.3 |
| Spring Data Commons | 3.5.1, administrada por Boot |
| RDF4J cliente | 5.1.3 |
| GraphDB Docker | 10.8.8 |

```bash
java -version
javac -version
mvn -version
docker compose up -d graphdb
docker compose run --rm graphdb-init
mvn clean verify
java -jar graphdb-demo/target/graphdb-demo-0.1.0.jar
```

Si ya inició todo con Compose, detenga su backend antes de iniciar Java localmente:

```bash
docker compose stop backend
```

No ejecute simultáneamente dos backend en el puerto 8080. Para usar otro puerto:

```bash
SERVER_PORT=8081 java -jar graphdb-demo/target/graphdb-demo-0.1.0.jar
```

También puede instalar los módulos y arrancar con Maven:

```bash
mvn clean install
mvn -pl graphdb-demo spring-boot:run
```

## Crear su entidad y repositorio

Ejemplo incluido:

```java
@RdfEntity(
    type = "https://schema.org/Book",
    namespace = "https://ejemplo.mx/libros/",
    graph = "urn:graphdb:libros"
)
public class Libro {
    @Id
    private String id;

    @RdfProperty("https://schema.org/name")
    private String titulo;

    @RdfProperty("https://schema.org/author")
    private String autor;

    @RdfProperty("https://schema.org/copyrightYear")
    private Integer anio;

    // Constructor sin argumentos, getters y setters.
}
```

Use `org.springframework.data.annotation.Id`, no `jakarta.persistence.Id`. Los getters/setters completos están en la clase `Libro` del ejemplo. Las anotaciones de mapeo se colocan en campos, no en getters.

```java
public interface LibroRepository extends GraphDbRepository<Libro> {
    Page<Libro> findByTituloContainingIgnoreCase(String titulo, Pageable pageable);
    List<Libro> findByAnioGreaterThanEqualOrderByAnioDesc(Integer anio);
    long countByAutor(String autor);
    boolean existsByTituloIgnoreCase(String titulo);
}
```

Inyecte `LibroRepository` por constructor. Spring genera la implementación:

```java
Libro guardado = repository.save(new Libro("Java efectivo", "Joshua Bloch", 2018));
String id = guardado.getId();

Page<Libro> pagina = repository.findByTituloContainingIgnoreCase(
    "java", PageRequest.of(0, 10, Sort.by("titulo")));

guardado.setTitulo("Java efectivo, actualizado");
repository.save(guardado);
repository.deleteById(id);
```

El ID automático es una cadena UUID. También se puede asignar una cadena local como `abc123`. En RDF el sujeto correspondiente es `https://ejemplo.mx/libros/abc123`. El grafo contiene:

```turtle
<https://ejemplo.mx/libros/abc123>
    a <https://schema.org/Book> ;
    <https://schema.org/name> "Java efectivo" ;
    <https://schema.org/author> "Joshua Bloch" ;
    <https://schema.org/copyrightYear> 2018 .
```

La generación automática ocurre cuando `id == null`. También puede asignar un ID con letras ASCII, números, guion o guion bajo. 

## Reutilizar la biblioteca en otro proyecto

1. Ejecute `mvn clean install` en la raíz de este proyecto. Instala el POM padre y la biblioteca en el repositorio Maven local.
2. En una aplicación con Spring Boot 3.5.3, agregue:

```xml
<dependency>
    <groupId>mx.com.ejemplo</groupId>
    <artifactId>spring-data-graphdb</artifactId>
    <version>0.1.0</version>
</dependency>
```

La dependencia es local: **no está publicada en Maven Central**. Si usa una plataforma/BOM que redefine RDF4J, alinee sus componentes con RDF4J 5.1.3. Para distribuir en su empresa, publique el padre y la biblioteca en su repositorio Maven.

3. Agregue `@EnableGraphDbRepositories(basePackageClasses = LibroRepository.class)` a su configuración.
4. Declare los beans de conexión y plantilla:

```java
@Bean(initMethod = "init", destroyMethod = "shutDown")
HTTPRepository rdfRepository() {
    return new HTTPRepository("http://localhost:7200/repositories/libros");
}

@Bean
GraphDbTemplate graphDbTemplate(HTTPRepository rdfRepository) {
    return new GraphDbTemplate(rdfRepository);
}
```

Imports: `org.eclipse.rdf4j.repository.http.HTTPRepository`, `org.springframework.context.annotation.Bean` y `mx.com.ejemplo.graphdb.*`.

Se pueden escanear varios paquetes con `basePackages`; para elegir otro bean de plantilla use `templateRef`. El registro usa el nombre completo de la interfaz como nombre del bean. La inyección habitual es por tipo. Marque las interfaces base intermedias con `@NoRepositoryBean`.

## Consultas soportadas

| Funcionalidad | Ejemplo / comportamiento |
|---|---|
| CRUD y operaciones por lotes | `save`, `saveAll`, `findById`, `findAllById`, `existsById`, `count`, `deleteById`, `deleteAllById`, `delete`, `deleteAll` |
| Listas y páginas | `findAll()`, `findAll(Sort)`, `findAll(Pageable)` |
| Igualdad / desigualdad | `findByAutor`, `findByAutorNot` |
| Texto | `Containing`, `StartingWith`, `EndingWith`, `IgnoreCase` para campos `String` |
| Comparaciones | `GreaterThan`, `GreaterThanEqual`, `LessThan`, `LessThanEqual` |
| Campos ausentes | `IsNull`, `IsNotNull` |
| Booleanos | `True`, `False` |
| Combinaciones | `And`, `Or`; precedencia del parser `PartTree` |
| Orden | `OrderByAnioDesc`, parámetro `Sort` o el `Sort` de `Pageable` |
| Proyecciones de conteo | `countBy...` devuelve `long`; `existsBy...` devuelve `boolean` |
| Consulta explícita | `@SparqlQuery` con `SELECT ?s` y parámetros `@Param` |

Consultas derivadas de selección: retornos `List<T>`, `Page<T>`, `Optional<T>` o la entidad. `Page<T>` exige `Pageable`. Una entidad ausente devuelve `null`; un `Optional` ausente está vacío. Más de un resultado en retorno individual produce `IncorrectResultSizeDataAccessException`.

No pase `null` como valor de búsqueda: use `IsNull`. `Not` compara solo valores existentes, por lo que no incluye campos ausentes. `Containing` significa subcadena, no búsqueda full-text. `IgnoreCase` usa `LCASE`, no elimina acentos.

La paginación utiliza `LIMIT` y `OFFSET` en SPARQL y una consulta `COUNT(DISTINCT ?s)`. Añade el IRI del sujeto como desempate de ordenación. El orden de nulos es el nativo de SPARQL. Las lecturas de contenido y conteo no forman una instantánea atómica ante escrituras concurrentes.

## SPARQL propio

```java
@SparqlQuery("""
    SELECT DISTINCT ?s WHERE {
      GRAPH <urn:graphdb:libros> {
        ?s a <https://schema.org/Book> ;
           <https://schema.org/copyrightYear> ?anio .
        FILTER(?anio >= ?desde)
      }
    } ORDER BY ?s
    """)
List<Libro> publicadosDesde(@Param("desde") Integer anio);
```

Se usa `TupleQuery.setBinding`; los valores no se concatenan a SPARQL. `?s` debe ser un IRI de una entidad explícita del tipo, namespace y grafo configurados. La consulta se controla desde código Java; no exponga un endpoint que reciba SPARQL arbitrario de usuarios. Para parámetros IRI use `java.net.URI`; los `String` son literales.

La versión 0.1 no admite `Pageable` ni `Sort` en `@SparqlQuery`, ni ASK, UPDATE, proyecciones DTO o conteos en estas consultas. Puede escribir `ORDER BY`, `LIMIT` y `OFFSET` fijos en el texto. La consulta debe devolver sujetos; la biblioteca hidrata las entidades después.

## API REST del ejemplo

| Operación | Ruta |
|---|---|
| Crear | `POST /api/libros` |
| Listar y buscar | `GET /api/libros?titulo=java&autor=bloch&pagina=0&tamanio=10&ordenar=titulo&direccion=asc` |
| Consultar | `GET /api/libros/{id}` |
| Reemplazar datos | `PUT /api/libros/{id}` |
| Borrar | `DELETE /api/libros/{id}` |

Título y autor juntos se combinan con AND. Páginas desde 0; tamaño entre 1 y 100. Orden por `id`, `titulo`, `autor` o `anio`. Sin datos iniciales automáticos.

```bash
curl -i -X POST http://localhost:8080/api/libros \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Java efectivo","autor":"Joshua Bloch","anio":2018}'

curl 'http://localhost:8080/api/libros?titulo=java&pagina=0&tamanio=10'
```

La respuesta de creación contiene el `id`. Para actualizar o borrar use ese mismo ID. El borrado es idempotente: devuelve 204 aunque ya no exista. `PUT` devuelve 404 si no existe; `save` en la biblioteca, en cambio, tiene semántica de upsert.

El listado tiene un DTO estable: `contenido`, `pagina`, `tamanio`, `totalElementos`, `totalPaginas`. No incluye cliente Angular en esta entrega.

## Conectar con un GraphDB existente

El repositorio debe existir. Puede crearlo desde Workbench o usar el archivo `graphdb/repository.ttl`. El inicializador proporcionado es para la instalación local sin autenticación y no reconfigura repositorios existentes.

```bash
export GRAPHDB_URL='http://localhost:7200/repositories/mi-repositorio'
java -jar graphdb-demo/target/graphdb-demo-0.1.0.jar
```

`GRAPHDB_USERNAME` y `GRAPHDB_PASSWORD` configuran autenticación de RDF4J cuando su servidor la exige. `SERVER_PORT` cambia el puerto del backend. El bean es `HTTPRepository`, no un endpoint SPARQL genérico: necesita la API HTTP de repositorios RDF4J que GraphDB implementa. Las credenciales son del servidor, no de esta biblioteca.

Para revisar triples en Workbench:

```sparql
SELECT ?s ?p ?o
WHERE { GRAPH <urn:graphdb:libros> { ?s ?p ?o } }
ORDER BY ?s ?p
```

## Alcance y límites de 0.1.0

- Mapea campos escalares `String`, `Integer`, `Long`, `Double`, `Boolean`, `BigDecimal`, `LocalDate` y `URI`. Use wrappers, no primitivos. Exige constructor sin argumentos y campos mutables; admite campos heredados. No mapea records, colecciones, blank nodes, objetos anidados, relaciones automáticas, cascadas ni listas RDF.
- Un predicado mapeado debe tener como máximo un valor por sujeto en ese grafo. Si encuentra varios, falla para evitar elegir uno arbitrariamente. Los textos del ejemplo se escriben como `xsd:string`; no preserva etiquetas de idioma ni tipos personalizados de literales externos.
- Configure namespaces distintos para tipos distintos. El grafo/tipo deben contener entidades del namespace compatible. No es un mapper general para importar cualquier dataset RDF ya existente.
- `save` reemplaza los predicados mapeados y asegura su `rdf:type`. Un campo `null` elimina su predicado mapeado. Conserva predicados ajenos, otros tipos y otros grafos. Si dos entidades comparten sujeto y predicados, esos datos se comparten también: elija sus namespaces para evitarlo.
- `delete` elimina los predicados mapeados y el tipo administrado, solo si ese tipo existe explícitamente en su grafo. Conserva enlaces entrantes y triples ajenos. Por eso puede quedar información RDF sobre un sujeto aunque ya no sea una entidad visible para el repositorio.
- `deleteAll()` elimina únicamente las entidades explícitas de ese tipo/grafo, no vacía GraphDB. Recorre los sujetos en memoria; no es una operación masiva optimizada.
- Todas las lecturas usan `includeInferred=false`. No convierte inferencias en datos persistidos. El repositorio Docker de demostración usa reglas `empty`.
- Cada escritura usa una transacción RDF4J y hace rollback ante errores. `saveAll` y borrados por lotes usan una transacción por llamada. Las conexiones se cierran y no se comparten entre hilos. Los IDs asignados al objeto Java pueden permanecer aunque la transacción falle.
- **No integra `@Transactional` de Spring ni transacciones entre varias llamadas**. No incluye bloqueo optimista (`@Version`), auditoría, callbacks, proyecciones, Query by Example, Specifications, QueryDSL ni soporte reactivo. Lecturas y posterior actualización no garantizan aislamiento frente a otra escritura concurrente.
- No admite operadores derivados no enumerados: `In`, `Between`, `Like`, `Regex`, `Top`, `First`, borrados derivados, propiedades anidadas, etc. Los rechaza al construir el repositorio; no interpreta esos métodos de forma aproximada.
- Carga cada entidad mediante una lectura RDF adicional después de seleccionar sujetos: existe un coste N+1 por página. `existsBy` usa conteo. Esta versión es una base funcional para extender, no una implementación optimizada para grandes cargas.
- Timeout de consultas SPARQL: 30 segundos por defecto, configurable en `new GraphDbTemplate(repository, segundos)`. No equivale a un timeout completo de red para todas las operaciones.

## Pruebas y estructura

```text
spring-data-graphdb/       biblioteca, fábricas Spring Data, mapper, consultas y pruebas
graphdb-demo/              aplicación Spring Boot, entidad Libro y API REST
graphdb/repository.ttl     configuración del repositorio GraphDB
scripts/                  inicialización y prueba HTTP del backend
compose.yaml              GraphDB, inicialización y backend
Dockerfile                compilación Maven y ejecución Java
VALIDACION.md             evidencia y límites de las pruebas realizadas
```

Pruebas normales, sin GraphDB ni Docker:

```bash
mvn clean verify
```

Las pruebas de repositorios usan el motor real RDF4J MemoryStore y un contexto Spring. Las pruebas web arrancan Tomcat y verifican validación de parámetros y JSON sin acceder a GraphDB. El modo de Mockito de pruebas usa subclases para no requerir adjuntar un agente a la JVM. No sustituyen la comprobación HTTP contra GraphDB.

Prueba opcional contra su GraphDB:

```bash
GRAPHDB_TEST_URL=http://localhost:7200/repositories/libros \
  mvn -pl spring-data-graphdb -Dtest=GraphDbHttpTest test
```

Esta prueba usa un grafo `urn:test:graph`, crea una entidad de prueba UUID y borra únicamente esa entidad al terminar. Sin `GRAPHDB_TEST_URL` se omite explícitamente. Revise `VALIDACION.md` para saber cuáles se ejecutaron en la preparación de esta entrega.

## Referencias técnicas

- [Spring Data Commons: RepositoryFactorySupport](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/repository/core/support/RepositoryFactorySupport.html): punto de extensión de fábricas de repositorios. La documentación actual puede describir firmas posteriores; este proyecto compila contra 3.5.1.
- [Eclipse RDF4J: Repository API](https://rdf4j.org/documentation/programming/repository/): conexiones, consultas, parámetros y transacciones.
- [GraphDB 10.8: configuración de repositorios](https://graphdb.ontotext.com/documentation/10.8/configuring-a-repository.html): configuración RDF del repositorio.
- [GraphDB: administración de repositorios con REST](https://graphdb.ontotext.com/documentation/10.8/manage-repos-with-curl.html): creación vía multipart/form-data.
