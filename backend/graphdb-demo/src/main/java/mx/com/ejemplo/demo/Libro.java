package mx.com.ejemplo.demo;

import mx.com.ejemplo.graphdb.RdfEntity;
import mx.com.ejemplo.graphdb.RdfProperty;

import org.springframework.data.annotation.Id;

/** Entidad Java cuyos campos se convierten en propiedades RDF del grafo de libros. */
@RdfEntity(
        type = "https://schema.org/Book",
        namespace = "https://ejemplo.mx/libros/",
        graph = "urn:graphdb:libros")
public class Libro {
    @Id private String id;

    @RdfProperty("https://schema.org/name")
    private String titulo;

    @RdfProperty("https://schema.org/author")
    private String autor;

    @RdfProperty("https://schema.org/copyrightYear")
    private Integer anio;

    public Libro() {}

    public Libro(String titulo, String autor, Integer anio) {
        this.titulo = titulo;
        this.autor = autor;
        this.anio = anio;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }
}
