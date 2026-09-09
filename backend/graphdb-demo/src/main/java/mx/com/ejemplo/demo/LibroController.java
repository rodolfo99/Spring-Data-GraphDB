package mx.com.ejemplo.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Set;

/** Recibe solicitudes HTTP y utiliza el repositorio de libros generado por Spring Data. */
@RestController
@RequestMapping("/api/libros")
public class LibroController {

    private final LibroRepository repository;

    public LibroController(LibroRepository repository) {
        this.repository = repository;
    }

    /** Datos aceptados al crear o actualizar. El ID se recibe únicamente en la ruta. */
    public record Entrada(
            @NotBlank @Size(max = 500) String titulo,
            @NotBlank @Size(max = 300) String autor,
            @Min(0) @Max(9999) Integer anio) {}

    /** Contrato JSON que espera el cliente Angular; conserva los nombres en español. */
    public record Pagina<T>(
            List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

        static <T> Pagina<T> of(Page<T> resultado) {
            return new Pagina<>(
                    resultado.getContent(),
                    resultado.getNumber(),
                    resultado.getSize(),
                    resultado.getTotalElements(),
                    resultado.getTotalPages());
        }
    }

    /** Busca por título, autor o ambos y devuelve una página de resultados. */
    @GetMapping
    public Pagina<Libro> listar(
            @RequestParam(defaultValue = "") String titulo,
            @RequestParam(defaultValue = "") String autor,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio,
            @RequestParam(defaultValue = "id") String ordenar,
            @RequestParam(defaultValue = "asc") String direccion) {

        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw bad("pagina >= 0; tamanio entre 1 y 100");
        }

        if (!Set.of("id", "titulo", "autor", "anio").contains(ordenar)) {
            throw bad("Ordenar por id, titulo, autor o anio");
        }

        if (!Set.of("asc", "desc").contains(direccion)) {
            throw bad("direccion: asc o desc");
        }

        // Las páginas empiezan en cero. Pageable transporta página, tamaño y orden.
        Pageable pageable =
                PageRequest.of(
                        pagina, tamanio, Sort.by(Sort.Direction.fromString(direccion), ordenar));

        if (!titulo.isBlank() && !autor.isBlank()) {
            return Pagina.of(
                    repository.findByTituloContainingIgnoreCaseAndAutorContainingIgnoreCase(
                            titulo.trim(), autor.trim(), pageable));
        }

        if (!titulo.isBlank()) {
            return Pagina.of(repository.findByTituloContainingIgnoreCase(titulo.trim(), pageable));
        }

        if (!autor.isBlank()) {
            return Pagina.of(repository.findByAutorContainingIgnoreCase(autor.trim(), pageable));
        }

        return Pagina.of(repository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public Libro obtener(@PathVariable String id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Libro no encontrado"));
    }

    @PostMapping
    public ResponseEntity<Libro> crear(@Valid @RequestBody Entrada entrada) {
        Libro libro = repository.save(new Libro(entrada.titulo(), entrada.autor(), entrada.anio()));

        return ResponseEntity.created(URI.create("/api/libros/" + libro.getId())).body(libro);
    }

    @PutMapping("/{id}")
    public Libro actualizar(@PathVariable String id, @Valid @RequestBody Entrada entrada) {
        // Cargar la entidad conserva el ID y permite responder 404 si no existe.
        Libro libro = obtener(id);
        libro.setTitulo(entrada.titulo());
        libro.setAutor(entrada.autor());
        libro.setAnio(entrada.anio());

        return repository.save(libro);
    }

    /** El borrado es idempotente y responde 204 sin cuerpo, como espera el cliente. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable String id) {
        repository.deleteById(id);
    }

    private ResponseStatusException bad(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
