# GraphDB — proyecto completo con código legible (v2)

Esta entrega reúne **la biblioteca Spring Data GraphDB, el backend de ejemplo y el cliente Angular**. Está basada en los proyectos creados en esta conversación: biblioteca/backend 0.1.0 y cliente 1.0.0.

**v2 identifica la edición de formato del paquete completo.** Las versiones Maven y npm se conservan; no requiere cambiar las dependencias de tus proyectos ni migrar datos.

## Qué se mejoró

- Java con indentación de cuatro espacios, métodos desarrollados y bloques de control con llaves.
- Imports ordenados y principales imports del ejemplo explícitos.
- Nombres de variables más descriptivos en el controlador de libros.
- Comentarios que explican la conexión, el mapeo RDF, la paginación y las operaciones del cliente.
- POM XML expandido: dependencias y propiedades en líneas separadas.
- TypeScript, plantillas Angular y CSS organizados en bloques con indentación de dos espacios.
- Script Python de prueba separado por pasos.
- `.editorconfig` y configuración de Prettier para mantener el estilo al editar.

Las rutas, nombres JSON, puertos, versiones de dependencias y consultas son los mismos. Tampoco se incorpora un proceso nuevo de creación de repositorios: GraphDB se inicializa con el servicio `graphdb-init` del Compose original.

## Dónde está cada parte

| Carpeta o archivo | Contenido |
|---|---|
| `backend/pom.xml` | Proyecto Maven padre con los dos módulos Java |
| `backend/spring-data-graphdb/` | Biblioteca reutilizable: mapper, repositorios y SPARQL |
| `backend/graphdb-demo/` | Ejemplo Spring Boot de libros |
| `backend/compose.yaml` | GraphDB, inicialización y backend |
| `backend/dist/` | JAR de la biblioteca y JAR ejecutable del ejemplo |
| `cliente-angular-graphdb/` | Proyecto Angular completo |
| `cliente-angular-graphdb/dist/` | Compilación de producción del cliente |
| `GUIA-DEL-CODIGO.md` | Orden sugerido para leer y modificar el código |
| `VALIDACION.md` | Resultados de compilación y pruebas de esta edición |

## Ejecutar el backend

Desde la raíz de este paquete:

```bash
cd backend
docker compose up -d --build
```

GraphDB: http://localhost:7200

API: http://localhost:8080/api/libros

Si la instalación anterior ya está ejecutándose y solo quieres probar este cliente, puedes conservar ese backend. Para ejecutar el nuevo backend Java debes liberar primero el puerto 8080.

Compose identifica normalmente el proyecto por la carpeta: al ejecutar desde `backend`, puede crear un volumen distinto al de tu instalación anterior. El volumen anterior no se elimina. Si quieres mantener exactamente esa instalación, ejecuta sus comandos desde la carpeta original o utiliza explícitamente el mismo nombre de proyecto de Compose que usaste antes.

## Ejecutar el cliente

En otra terminal, desde la raíz del paquete:

```bash
cd cliente-angular-graphdb
npm ci
npm start
```

Abre http://localhost:4200. Usa Node.js 24.15+ dentro de la rama 24, o Node 22.22.3+ dentro de la rama 22. El archivo `.nvmrc` permite seleccionar Node 24 con NVM.

El proxy del cliente sigue apuntando a `http://127.0.0.1:8080`. No necesitas modificar el cliente Angular de la entrega anterior para usar este backend.

## Compilar la biblioteca por separado

Dentro de `backend`:

```bash
mvn -pl spring-data-graphdb -am clean install
```

Se instala una vez en el repositorio Maven local. El JAR se genera en:

```text
spring-data-graphdb/target/spring-data-graphdb-0.1.0.jar
```

## Compilar y ejecutar el ejemplo Java

Dentro de `backend`:

```bash
mvn -pl graphdb-demo -am clean package

docker compose up -d graphdb
docker compose run --rm graphdb-init

docker compose stop backend
java -jar graphdb-demo/target/graphdb-demo-0.1.0.jar
```

También puedes usar el JAR incluido: `java -jar dist/graphdb-demo-0.1.0.jar`. Requiere Java 17+ y que GraphDB esté disponible.

## Mantener el formato

Abre `backend/pom.xml` como proyecto Maven en IntelliJ IDEA o tu IDE. Las clases mantienen sus paquetes originales y puedes seguir usando las mismas configuraciones de ejecución.

El archivo `.editorconfig` define espacios, indentación, finales de línea y UTF-8. Para reformatear el cliente desde su carpeta:

```bash
npx --yes prettier@3.6.2 --write "src/**/*.{ts,html,css}"
```

La primera ejecución puede descargar Prettier. No se añade como dependencia de ejecución del cliente. Java se formateó con google-java-format 1.24.0 en estilo AOSP (cuatro espacios).

## Pruebas

Backend, desde `backend`:

```bash
mvn verify
```

Cliente, desde `cliente-angular-graphdb`:

```bash
npm test
npm run build
```

Los README de ambas carpetas contienen los detalles completos de configuración y las limitaciones funcionales heredadas de 0.1.0. El backend sigue siendo una implementación propia y no oficial de Spring Data para GraphDB.
