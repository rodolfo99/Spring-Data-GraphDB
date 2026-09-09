package mx.com.ejemplo.demo;

import mx.com.ejemplo.graphdb.GraphDbRepository;
import mx.com.ejemplo.graphdb.SparqlQuery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Spring Data genera la implementación; los nombres de métodos describen sus filtros. */
public interface LibroRepository extends GraphDbRepository<Libro> {
    Page<Libro> findByTituloContainingIgnoreCase(String titulo, Pageable pageable);

    Page<Libro> findByAutorContainingIgnoreCase(String autor, Pageable pageable);

    Page<Libro> findByTituloContainingIgnoreCaseAndAutorContainingIgnoreCase(
            String titulo, String autor, Pageable pageable);

    List<Libro> findByAnioGreaterThanEqualOrderByAnioDesc(Integer anio);

    long countByAutor(String autor);

    boolean existsByTituloIgnoreCase(String titulo);

    @SparqlQuery(
            """
            SELECT DISTINCT ?s WHERE {
             GRAPH <urn:graphdb:libros> {
              ?s a <https://schema.org/Book> ; <https://schema.org/copyrightYear> ?anio .
              FILTER(?anio >= ?desde)
             }
            } ORDER BY ?s
            """)
    List<Libro> publicadosDesde(@Param("desde") Integer anio);
}
