package mx.com.ejemplo.graphdb;

import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface TestBookRepository extends GraphDbRepository<TestBook> {
    List<TestBook> findByTitleContainingIgnoreCase(String title);

    Page<TestBook> findByAuthor(String author, Pageable pageable);

    List<TestBook> findByAuthorAndYearGreaterThanEqual(String author, Integer year);

    List<TestBook> findByAuthorOrYearLessThan(String author, Integer year);

    List<TestBook> findByYearGreaterThanEqualOrderByYearDesc(Integer year);

    List<TestBook> findByAuthorIsNull();

    List<TestBook> findByActiveTrueAndAuthor(String author);

    List<TestBook> findByAuthorIsNotNullAndTitleStartingWith(String title);

    List<TestBook> findByAuthorNot(String author);

    List<TestBook> findByTitleEndingWith(String title, Sort sort);

    Optional<TestBook> findByTitle(String title);

    long countByAuthor(String author);

    boolean existsByTitleIgnoreCase(String title);

    @SparqlQuery(
            "SELECT DISTINCT ?s WHERE { GRAPH <urn:test:graph> { ?s a <urn:test:Book>;"
                    + " <urn:test:year> ?year . FILTER(?year >= ?min) } } ORDER BY ?s")
    List<TestBook> since(@Param("min") Integer min);

    default boolean empty() {
        return count() == 0;
    }
}
