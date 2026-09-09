package mx.com.ejemplo.graphdb;

import static org.assertj.core.api.Assertions.*;

import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

/** Opt-in: no borra datos ajenos, solo la entidad UUID creada por esta prueba. */
@EnabledIfEnvironmentVariable(named = "GRAPHDB_TEST_URL", matches = ".+")
class GraphDbHttpTest {
    @Test
    void realGraphDbCrud() {
        HTTPRepository store = new HTTPRepository(System.getenv("GRAPHDB_TEST_URL"));
        String user = System.getenv("GRAPHDB_USERNAME");
        if (user != null) {
            store.setUsernameAndPassword(
                    user, System.getenv().getOrDefault("GRAPHDB_PASSWORD", ""));
        }
        store.init();
        TestBookRepository books =
                new GraphDbRepositoryFactory(new GraphDbTemplate(store))
                        .getRepository(TestBookRepository.class);
        TestBook book = new TestBook("HTTP-" + UUID.randomUUID(), "GraphDB test", 2026);
        try {
            books.save(book);
            String id = book.id;
            assertThat(books.findByTitle(book.title)).isPresent();
            book.title += "-updated";
            books.save(book);
            assertThat(books.findById(id).orElseThrow().title).isEqualTo(book.title);
            books.deleteById(id);
            assertThat(books.findById(id)).isEmpty();
        } finally {
            try {
                if (book.id != null) {
                    books.deleteById(book.id);
                }
            } finally {
                store.shutDown();
            }
        }
    }
}
