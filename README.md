# Spring Data GraphDB — Java + RDF4J + SPARQL + Angular

> Implementación reutilizable, no oficial, de un estilo **Spring Data para Ontotext GraphDB**, acompañada de un backend Spring Boot de ejemplo y un cliente Angular.

![Java](https://img.shields.io/badge/Java-Spring-informational)
![GraphDB](https://img.shields.io/badge/Ontotext-GraphDB-informational)
![RDF4J](https://img.shields.io/badge/Eclipse-RDF4J-informational)
![SPARQL](https://img.shields.io/badge/SPARQL-RDF-informational)
![Angular](https://img.shields.io/badge/Angular-Frontend-informational)

Este proyecto explora una capa de repositorios y mapeo para trabajar con **GraphDB, RDF, RDF4J y SPARQL desde Spring Boot**, siguiendo patrones familiares de Spring Data. Incluye la biblioteca reutilizable, un CRUD de libros de demostración, infraestructura Docker y un cliente Angular.

## Lo más importante

- Biblioteca Java reutilizable para GraphDB.
- Integración con Eclipse RDF4J.
- Consultas y operaciones SPARQL.
- Backend de ejemplo con Spring Boot.
- Cliente Angular completo.
- Docker Compose para GraphDB e inicialización.
- Código formateado y documentado para facilitar su estudio.

> **Nota:** este proyecto es una implementación propia y **no es un módulo oficial de Spring Data ni de Ontotext**.

## Arquitectura

```text
Angular
   │ HTTP
   ▼
Spring Boot demo
   │ repositorios / mapper / SPARQL
   ▼
Spring Data GraphDB library
   │ RDF4J
   ▼
Ontotext GraphDB
```

## Estructura

| Carpeta o archivo | Contenido |
|---|---|
| `backend/pom.xml` | Proyecto Maven padre con los módulos Java |
| `backend/spring-data-graphdb/` | Biblioteca reutilizable: mapper, repositorios y SPARQL |
| `backend/graphdb-demo/` | Ejemplo Spring Boot de libros |
| `backend/compose.yaml` | GraphDB, inicialización y backend |
| `backend/dist/` | JAR de la biblioteca y JAR ejecutable del ejemplo |
| `cliente-angular-graphdb/` | Proyecto Angular completo |
| `GUIA-DEL-CODIGO.md` | Orden sugerido para estudiar y modificar el código |
| `VALIDACION.md` | Resultados de compilación y pruebas |

## Inicio rápido

### Backend + GraphDB

```bash
cd backend
docker compose up -d --build
```

GraphDB:

```text
http://localhost:7200
```

API de ejemplo:

```text
http://localhost:8080/api/libros
```

### Cliente Angular

```bash
cd cliente-angular-graphdb
npm ci
npm start
```

Abre:

```text
http://localhost:4200
```

## Compilar la biblioteca

```bash
cd backend
mvn -pl spring-data-graphdb -am clean install
```

El JAR queda en:

```text
spring-data-graphdb/target/spring-data-graphdb-0.1.0.jar
```

## Compilar el ejemplo

```bash
cd backend
mvn -pl graphdb-demo -am clean package
```

También puedes ejecutar:

```bash
docker compose up -d graphdb
docker compose run --rm graphdb-init
docker compose stop backend
java -jar graphdb-demo/target/graphdb-demo-0.1.0.jar
```

## Pruebas

Backend:

```bash
cd backend
mvn verify
```

Cliente:

```bash
cd cliente-angular-graphdb
npm test
npm run build
```

## Qué puedes estudiar aquí

- Integración de **Spring Boot con GraphDB**.
- Uso de **RDF4J** desde Java.
- Modelado y persistencia RDF.
- Consultas **SPARQL**.
- Diseño de una abstracción estilo repositorio.
- Separación entre biblioteca, aplicación demo y frontend Angular.
- Bases para aplicaciones de web semántica y datos enlazados.

## Otros proyectos del mismo perfil

- [Marc2BF — MARC21 a BIBFRAME](https://github.com/rodolfo99/Marc2BF)
- [CRUD Neo4j + Angular](https://github.com/rodolfo99/CRUD-LIBROS-NEO4J)
- [CRUD Apache Solr + Angular](https://github.com/rodolfo99/CRUD-Solr-con-cliente-angular)
- [CRUD PostgreSQL + Angular](https://github.com/rodolfo99/CRUD-PstgreSQL-Libros-con-cliente-angular)
- [CRUD GraphQL + PostgreSQL + Angular](https://github.com/rodolfo99/crud-GraphQL-con-cliente-angular)

## Autor

**Rodolfo Valencia** — desarrollo de software, Java, Spring, Angular, bases de datos, tecnologías semánticas e inteligencia artificial.

GitHub: [@rodolfo99](https://github.com/rodolfo99)

## Palabras clave

Spring Boot · GraphDB · Ontotext · RDF4J · RDF · SPARQL · Semantic Web · Linked Data · Java · Angular · Spring Data · graph database
