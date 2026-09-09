package mx.com.ejemplo.graphdb;

import java.lang.annotation.*;

/** SELECT DISTINCT ?s, sin Pageable; parametros RDF4J mediante @Param. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SparqlQuery {
    String value();
}
