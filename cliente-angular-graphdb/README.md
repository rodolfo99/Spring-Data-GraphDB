# Cliente Angular para Spring Data GraphDB

Cliente Angular **22.0.8** para el backend `spring-data-graphdb-0.1.0` de la entrega anterior. Incluye CRUD de libros, búsqueda por título/autor, ordenación y paginación.

## Ejecutar

Primero inicia el backend y GraphDB. Desde la carpeta del proyecto Java anterior:

```bash
docker compose up -d --build
```

Si ya están ejecutándose, no necesitas reiniciarlos. El backend debe responder en `http://localhost:8080/api/libros`.

Descomprime este ZIP y abre una terminal en la carpeta `cliente-angular-graphdb`:

```bash
npm ci
npm start
```

Abre **http://localhost:4200**. `npm ci` instala exactamente lo registrado en `package-lock.json`. No necesitas instalar Angular CLI globalmente; `npm start` usa la versión del proyecto.

### Node.js

Se recomienda **Node.js 24.15.0 o superior dentro de la rama 24**. También admite la rama 22 a partir de 22.22.3. Se compiló y probó con Node.js 24.19.0.

Si tienes NVM:

```bash
nvm install 24
nvm use 24
node --version
npm ci
npm start
```

El archivo `.nvmrc` selecciona la rama 24. No uses Node 18/20 para este proyecto Angular 22. El requisito se basa en la [tabla oficial de compatibilidad de Angular](https://angular.dev/reference/versions).

## Funciones

- Listar libros y mostrar título, autor, año e ID.
- Buscar por título y autor. Cuando llenas ambos campos se combinan con AND en el backend.
- Ordenar por título, autor, año o ID; dirección ascendente/descendente.
- Elegir 5, 10, 20, 50 o 100 registros por página.
- Crear un libro y editar uno existente desde el formulario.
- Borrar mediante una confirmación dentro de la tabla.
- Mostrar estados de carga, catálogo vacío, errores y operaciones completadas.
- Conservar el formulario si falla una escritura.
- Volver automáticamente a una página existente si borras el último registro de la última página.

Pulsa **Buscar** para aplicar los filtros, el tamaño de página y la ordenación seleccionados. **Actualizar** vuelve a consultar usando los filtros ya aplicados. **Limpiar filtros** restablece los valores iniciales.

Al guardar se mantienen los filtros. Un libro recién creado o editado puede quedar fuera de los resultados actuales; usa **Limpiar filtros** para encontrarlo.

El formulario exige título y autor con contenido, admite hasta 500 y 300 caracteres respectivamente y permite año vacío (`null`) o un entero entre 0 y 9999. **Nuevo libro**, **Limpiar** y **Cancelar edición** descartan los cambios del formulario sin guardarlos.

## Compatibilidad con el backend

Las llamadas del navegador usan rutas relativas y el proxy de Angular:

```text
Navegador localhost:4200 → /api/libros → backend 127.0.0.1:8080 → GraphDB
```

El cliente nunca se conecta directamente al puerto 7200 ni guarda credenciales de GraphDB.

| Operación | Petición |
|---|---|
| Listado y búsqueda | `GET /api/libros` |
| Crear | `POST /api/libros` |
| Actualizar | `PUT /api/libros/{id}` |
| Borrar | `DELETE /api/libros/{id}` |

Parámetros del listado: `titulo`, `autor`, `pagina`, `tamanio`, `ordenar`, `direccion`. Las páginas del backend empiezan en 0; la interfaz las presenta desde 1.

Respuesta esperada:

```json
{
  "contenido": [
    { "id": "abc-123", "titulo": "Java efectivo", "autor": "Joshua Bloch", "anio": 2018 }
  ],
  "pagina": 0,
  "tamanio": 10,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

No utiliza los campos `content`/`totalElements` de otro formato de paginación ni espera un array plano. Verifica el formato recibido para detectar si se conectó al backend incorrecto.

Los IDs siempre son **string**, se conservan fuera del formulario editable y se incluyen en la URL al actualizar/borrar. POST/PUT envían solo `titulo`, `autor` y `anio`. DELETE admite la respuesta **204 sin cuerpo**.

Las filas usan `track libro.id`. Las operaciones se bloquean mientras hay solicitudes pendientes, y los datos del listado se vuelven a consultar después de escribir. No hay datos de demostración ni almacenamiento ficticio en el cliente.

## Cambiar el puerto del backend

Edita `proxy.conf.json`:

```json
{
  "/api/**": {
    "target": "http://127.0.0.1:8081",
    "secure": false,
    "changeOrigin": true
  }
}
```

Detén Angular con `Ctrl+C` y vuelve a ejecutar `npm start`. El comodín `/api/**` también cubre las rutas con ID usadas por PUT/DELETE; es el comportamiento documentado para el [servidor de desarrollo de Angular](https://angular.dev/tools/cli/serve).

Para cambiar el puerto del cliente:

```bash
npx ng serve --host localhost --port 4300
```

## Compilar y probar

```bash
npm run build
npm test
```

La compilación genera los archivos estáticos en `dist/cliente-angular-graphdb/browser`. Se incluye esa compilación en el ZIP, además del código fuente. El ZIP no incluye `node_modules`.

Las 10 pruebas usan Angular TestBed, Vitest y HttpTestingController. Comprueban el contrato de solicitudes, validación, creación sin ID, doble envío, edición repetida seguida de borrado, confirmación cancelada, filtros, paginación, conservación del formulario ante errores y rechazo de formatos incompatibles.

Las respuestas HTTP de estas pruebas son simuladas; no equivalen a un CRUD probado contra una instancia real de GraphDB. Consulta `VALIDACION.md` para conocer las verificaciones realizadas.

## Sin Zone.js

La aplicación registra explícitamente `provideZonelessChangeDetection()` y usa signals para el estado que cambia con las solicitudes HTTP. No requiere instalar ni importar `zone.js`; así evita el error `NG0908: Angular requires Zone.js` por una configuración incompleta. Véase la [documentación oficial de Angular sobre zoneless](https://angular.dev/guide/zoneless).

## Publicar los archivos compilados

El proxy de `ng serve` solo funciona durante el desarrollo. Si sirves `dist/cliente-angular-graphdb/browser` con Nginx u otro servidor, configura también un reverse proxy para `/api/` al backend. No abras `index.html` con `file://`.

El archivo `nginx.example.conf` contiene un ejemplo para Nginx y backend en el mismo equipo. Ajusta su raíz de archivos y puerto a tu instalación. No se publica ni modifica automáticamente ningún servidor.

## Problemas frecuentes

- **ECONNREFUSED / error de conexión:** comprueba que Spring Boot esté iniciado en el puerto del proxy y que GraphDB esté disponible. Ejecuta `curl http://localhost:8080/api/libros` para revisar la API.
- **Aparece un formato inesperado:** comprueba que el puerto 8080 corresponda al backend `spring-data-graphdb-0.1.0`, no a otro ejemplo de PostgreSQL, Solr o Neo4j.
- **No aparece un libro guardado:** los filtros y la ordenación se mantienen; limpia los filtros y revisa las páginas.
- **La terminal exige otra versión de Node:** ejecuta `nvm use 24` en esa misma terminal.
- **El puerto 4200 está ocupado:** detén el otro cliente o usa el comando de puerto 4300 indicado arriba.
- **Cambiar el proxy no hace efecto:** reinicia `npm start`.

## Archivos principales

- `src/app/libros-api.service.ts`: tipos, validación de respuestas y llamadas HTTP.
- `src/app/app.component.ts`: formularios, estado y operaciones.
- `src/app/app.component.html`: interfaz del catálogo y editor.
- `src/styles.css`: estilos y distribución adaptable.
- `src/main.ts`: arranque Angular sin Zone.js.
- `src/app/app.component.spec.ts`: pruebas de regresión.
- `proxy.conf.json`: dirección del backend en desarrollo.
