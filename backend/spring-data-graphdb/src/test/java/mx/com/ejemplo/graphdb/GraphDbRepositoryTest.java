package mx.com.ejemplo.graphdb;

import static org.assertj.core.api.Assertions.*;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

class GraphDbRepositoryTest {
    AnnotationConfigApplicationContext context;
    SailRepository store;
    TestBookRepository books;
    static final ValueFactory VF = SimpleValueFactory.getInstance();

    @Configuration
    @EnableGraphDbRepositories(basePackageClasses = TestBookRepository.class)
    static class Config {
        @Bean(initMethod = "init", destroyMethod = "shutDown")
        SailRepository store() {
            return new SailRepository(new MemoryStore());
        }

        @Bean
        GraphDbTemplate graphDbTemplate(SailRepository store) {
            return new GraphDbTemplate(store);
        }
    }

    @BeforeEach
    void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        store = context.getBean(SailRepository.class);
        books = context.getBean(TestBookRepository.class);
    }

    @AfterEach
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    TestBook save(String title, String author, Integer year) {
        return books.save(new TestBook(title, author, year));
    }

    @Test
    void springCreatesRepositoryAndDefaultMethodsWork() {
        assertThat(books.empty()).isTrue();
        save("Java", "Rodolfo", 2026);
        assertThat(books.count()).isEqualTo(1);
        assertThat(books.empty()).isFalse();
    }

    @Test
    void repeatedUpdatesKeepIdAndDeletionStillWorks() {
        TestBook b = save("A", "One", 2020);
        String id = b.id;
        for (String title : List.of("B", "C")) {
            b.title = title;
            books.save(b);
            assertThat(b.id).isEqualTo(id);
            assertThat(books.findById(id).orElseThrow().title).isEqualTo(title);
            assertThat(books.count()).isEqualTo(1);
        }
        books.deleteById(id);
        assertThat(books.findById(id)).isEmpty();
        books.deleteById(id);
        assertThat(books.existsById(id)).isFalse();
    }

    @Test
    void roundTripScalarTypesAndRemoveNullValue() {
        TestBook b = new TestBook("Texto \"especial\" y ñ", "A", 2026);
        b.price = new BigDecimal("123.45");
        b.published = LocalDate.of(2026, 9, 8);
        b.website = URI.create("https://example.org/book");
        b.active = true;
        b.visits = 9000000000L;
        b.score = 9.25;
        books.save(b);
        TestBook got = books.findById(b.id).orElseThrow();
        assertThat(got).usingRecursiveComparison().isEqualTo(b);
        b.author = null;
        books.save(b);
        assertThat(books.findById(b.id).orElseThrow().author).isNull();
    }

    @Test
    void derivedFiltersAreCorrelatedAndBindingsCannotInject() {
        save("Java avanzado", "Ana", 2020);
        save("JAVA básico", "Luis", 2025);
        save("Python", "Ana", 2010);
        assertThat(books.findByTitleContainingIgnoreCase("java"))
                .extracting(b -> b.title)
                .containsExactlyInAnyOrder("Java avanzado", "JAVA básico");
        assertThat(books.findByTitleContainingIgnoreCase("\" ) } UNION { ?s ?p ?o } #")).isEmpty();
        assertThat(books.findByAuthorAndYearGreaterThanEqual("Ana", 2015))
                .extracting(b -> b.title)
                .containsExactly("Java avanzado");
        assertThat(books.findByAuthorOrYearLessThan("Luis", 2015))
                .extracting(b -> b.title)
                .containsExactlyInAnyOrder("JAVA básico", "Python");
        assertThat(books.countByAuthor("Ana")).isEqualTo(2);
        assertThat(books.existsByTitleIgnoreCase("python")).isTrue();
        assertThat(books.existsByTitleIgnoreCase("missing")).isFalse();
    }

    @Test
    void nullBooleanAndStringOperators() {
        TestBook b = save("Spring Java", "Ana", 2020);
        b.active = true;
        books.save(b);
        save("Other Java", null, 2021);
        save("Python", "Luis", 2022);
        assertThat(books.findByAuthorIsNull())
                .extracting(x -> x.title)
                .containsExactly("Other Java");
        assertThat(books.findByActiveTrueAndAuthor("Ana")).hasSize(1);
        assertThat(books.findByAuthorIsNotNullAndTitleStartingWith("Spring")).hasSize(1);
        assertThat(books.findByAuthorNot("Ana")).extracting(x -> x.title).containsExactly("Python");
        assertThat(books.findByTitleEndingWith("Java", Sort.by("title")))
                .extracting(x -> x.title)
                .containsExactly("Other Java", "Spring Java");
    }

    @Test
    void paginationSortingAndCountExecuteOnRdfStore() {
        save("B", "Ana", 2025);
        save("A", "Ana", 2020);
        save("C", "Luis", 2022);
        Page<TestBook> p = books.findByAuthor("Ana", PageRequest.of(1, 1, Sort.by("title")));
        assertThat(p.getTotalElements()).isEqualTo(2);
        assertThat(p.getTotalPages()).isEqualTo(2);
        assertThat(p.getContent()).extracting(b -> b.title).containsExactly("B");
        assertThat(books.findAll(PageRequest.of(20, 2)).getContent()).isEmpty();
        assertThat(books.findByYearGreaterThanEqualOrderByYearDesc(2020))
                .extracting(b -> b.year)
                .containsExactly(2025, 2022, 2020);
        assertThatThrownBy(() -> books.findAll(Sort.by("unknown")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customSparqlAndSingleResultContract() {
        save("same", "A", 2020);
        save("same", "B", 2026);
        assertThat(books.since(2025)).hasSize(1);
        assertThat(books.findByTitle("absent")).isEmpty();
        assertThatThrownBy(() -> books.findByTitle("same"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }

    @Test
    void saveAndDeletePreserveOtherPredicatesTypesGraphsAndIncomingLinks() {
        TestBook b = save("A", "Ana", 2026);
        IRI s = VF.createIRI("urn:test:book:" + b.id),
                g = VF.createIRI("urn:test:graph"),
                other = VF.createIRI("urn:other:graph"),
                p = VF.createIRI("urn:external:note"),
                foreign = VF.createIRI("urn:foreign");
        try (var c = store.getConnection()) {
            c.add(s, p, VF.createLiteral("keep"), g);
            c.add(s, RDF.TYPE, foreign, g);
            c.add(s, VF.createIRI("urn:test:title"), VF.createLiteral("other graph"), other);
            c.add(foreign, p, s, g);
        }
        b.title = "B";
        books.save(b);
        books.delete(b);
        try (var c = store.getConnection()) {
            assertThat(c.hasStatement(s, p, null, false, g)).isTrue();
            assertThat(c.hasStatement(s, RDF.TYPE, foreign, false, g)).isTrue();
            assertThat(c.hasStatement(s, null, null, false, other)).isTrue();
            assertThat(c.hasStatement(foreign, p, s, false, g)).isTrue();
            assertThat(c.hasStatement(s, VF.createIRI("urn:test:title"), null, false, g)).isFalse();
        }
    }

    @Test
    void batchCrudAndTypeScopedDeleteAll() {
        TestBook a = new TestBook("A", "Ana", 2020), b = new TestBook("B", "Ana", 2021);
        books.saveAll(List.of(a, b));
        assertThat(books.findAllById(List.of(a.id, b.id, "absent", a.id))).hasSize(2);
        books.deleteAllById(List.of(a.id));
        assertThat(books.count()).isEqualTo(1);
        try (var c = store.getConnection()) {
            c.add(
                    VF.createIRI("urn:foreign"),
                    RDF.TYPE,
                    VF.createIRI("urn:Other"),
                    VF.createIRI("urn:test:graph"));
        }
        books.deleteAll();
        assertThat(books.count()).isZero();
        try (var c = store.getConnection()) {
            assertThat(c.hasStatement(VF.createIRI("urn:foreign"), RDF.TYPE, null, false)).isTrue();
        }
    }

    @Test
    void rejectInvalidIdAndMultiValuedScalars() {
        assertThatThrownBy(() -> books.findById("x> ?s ?p ?o"))
                .isInstanceOf(IllegalArgumentException.class);
        TestBook b = save("one", "A", 2020);
        try (var c = store.getConnection()) {
            c.add(
                    VF.createIRI("urn:test:book:" + b.id),
                    VF.createIRI("urn:test:title"),
                    VF.createLiteral("two"),
                    VF.createIRI("urn:test:graph"));
        }
        assertThatThrownBy(() -> books.findById(b.id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedBatchDoesNotPersistEarlierItems() {
        TestBook good = new TestBook("Good", "A", 2020), bad = new TestBook("Bad", "B", 2020);
        bad.website = URI.create("relative");
        assertThatThrownBy(() -> books.saveAll(List.of(good, bad)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(books.count()).isZero();
    }

    @org.springframework.data.repository.NoRepositoryBean
    interface Unsupported extends GraphDbRepository<TestBook> {
        List<TestBook> findByTitleRegex(String regex);
    }

    @Test
    void unsupportedDerivedQueriesFailAtStartup() {
        assertThatThrownBy(
                        () ->
                                new GraphDbRepositoryFactory(new GraphDbTemplate(store))
                                        .getRepository(Unsupported.class))
                .hasStackTraceContaining("Operador no implementado");
    }
}
