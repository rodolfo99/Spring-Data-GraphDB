package mx.com.ejemplo.graphdb;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.*;

import java.util.*;
import java.util.function.Function;

/** Una conexion por operacion; no comparte RepositoryConnection entre hilos. */
public class GraphDbTemplate {
    private final Repository repository;
    private final int timeoutSeconds;

    public GraphDbTemplate(Repository repository) {
        this(repository, 30);
    }

    public GraphDbTemplate(Repository repository, int timeoutSeconds) {
        this.repository = Objects.requireNonNull(repository);
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds >= 1");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    private <R> R run(boolean write, Function<RepositoryConnection, R> action) {
        try (RepositoryConnection c = repository.getConnection()) {
            if (write) {
                c.begin();
            }
            try {
                R result = action.apply(c);
                if (write) {
                    c.commit();
                }
                return result;
            } catch (RuntimeException e) {
                if (write && c.isActive()) {
                    try {
                        c.rollback();
                    } catch (RuntimeException rollback) {
                        e.addSuppressed(rollback);
                    }
                }
                throw e;
            }
        } catch (RepositoryException | QueryEvaluationException e) {
            throw new DataAccessResourceFailureException("Error al acceder al repositorio RDF", e);
        }
    }

    public <T> T save(T entity, RdfMapper<T> m) {
        return saveAll(List.of(entity), m).get(0);
    }

    public <T> List<T> saveAll(Iterable<T> entities, RdfMapper<T> m) {
        List<T> list = new ArrayList<>();
        List<List<Statement>> statements = new ArrayList<>();
        for (T e : entities) {
            Objects.requireNonNull(e);
            m.assignId(e);
            list.add(e);
            statements.add(m.statements(e));
        }
        return run(
                true,
                c -> {
                    for (int i = 0; i < list.size(); i++) {
                        IRI s = m.subject(m.id(list.get(i)));
                        // Reemplazar solo los predicados mapeados; conservar triples ajenos.
                        for (IRI p : m.predicates.values()) {
                            c.remove(s, p, null, m.graph);
                        }
                        c.add(statements.get(i));
                    }
                    return list;
                });
    }

    private <T> Optional<T> read(RepositoryConnection c, IRI s, RdfMapper<T> m) {
        if (!c.hasStatement(s, RDF.TYPE, m.type, false, m.graph)) {
            return Optional.empty();
        }
        Map<IRI, List<Value>> values = new HashMap<>();
        try (RepositoryResult<Statement> rows = c.getStatements(s, null, null, false, m.graph)) {
            while (rows.hasNext()) {
                Statement st = rows.next();
                values.computeIfAbsent(st.getPredicate(), k -> new ArrayList<>())
                        .add(st.getObject());
            }
        }
        return Optional.of(m.read(s, values));
    }

    public <T> Optional<T> findById(String id, RdfMapper<T> m) {
        IRI s = m.subject(id);
        return run(false, c -> read(c, s, m));
    }

    public <T> List<T> findAllById(Iterable<String> ids, RdfMapper<T> m) {
        List<IRI> subjects = new ArrayList<>();
        ids.forEach(id -> subjects.add(m.subject(id)));
        return run(
                false,
                c -> {
                    List<T> out = new ArrayList<>();
                    for (IRI s : new LinkedHashSet<>(subjects)) {
                        read(c, s, m).ifPresent(out::add);
                    }
                    return out;
                });
    }

    public <T> boolean existsById(String id, RdfMapper<T> m) {
        IRI s = m.subject(id);
        return run(false, c -> c.hasStatement(s, RDF.TYPE, m.type, false, m.graph));
    }

    public <T> void deleteAllById(Iterable<String> ids, RdfMapper<T> m) {
        List<IRI> subjects = new ArrayList<>();
        ids.forEach(id -> subjects.add(m.subject(id)));
        run(
                true,
                c -> {
                    for (IRI s : subjects) {
                        remove(c, s, m);
                    }
                    return null;
                });
    }

    private <T> void remove(RepositoryConnection c, IRI s, RdfMapper<T> m) {
        if (!c.hasStatement(s, RDF.TYPE, m.type, false, m.graph)) {
            return;
        }
        for (IRI p : m.predicates.values()) {
            c.remove(s, p, null, m.graph);
        }
        c.remove(s, RDF.TYPE, m.type, m.graph);
    }

    public <T> void deleteAll(RdfMapper<T> m) {
        run(
                true,
                c -> {
                    List<IRI> ids =
                            ids(c, "SELECT DISTINCT ?s WHERE { " + where(m, "") + " }", Map.of());
                    for (IRI s : ids) {
                        remove(c, s, m);
                    }
                    return null;
                });
    }

    static String where(RdfMapper<?> m, String filter) {
        return "GRAPH <" + m.graph + "> { ?s a <" + m.type + "> . " + filter + " }";
    }

    private TupleQuery query(RepositoryConnection c, String sparql, Map<String, Value> bindings) {
        TupleQuery q = c.prepareTupleQuery(QueryLanguage.SPARQL, sparql);
        q.setIncludeInferred(false);
        q.setMaxExecutionTime(timeoutSeconds);
        bindings.forEach(q::setBinding);
        return q;
    }

    private List<IRI> ids(RepositoryConnection c, String sparql, Map<String, Value> bindings) {
        Set<IRI> out = new LinkedHashSet<>();
        try (TupleQueryResult rows = query(c, sparql, bindings).evaluate()) {
            while (rows.hasNext()) {
                Value v = rows.next().getValue("s");
                if (!(v instanceof IRI iri)) {
                    throw new IllegalArgumentException("La consulta debe devolver ?s como IRI");
                }
                out.add(iri);
            }
        }
        return new ArrayList<>(out);
    }

    public <T> List<T> select(String sparql, Map<String, Value> bindings, RdfMapper<T> m) {
        return run(
                false,
                c -> {
                    List<T> result = new ArrayList<>();
                    for (IRI s : ids(c, sparql, bindings)) {
                        m.localId(s);
                        result.add(
                                read(c, s, m)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "?s no es una entidad explicita del"
                                                                    + " tipo/grafo configurado: "
                                                                        + s)));
                    }
                    return result;
                });
    }

    public <T> long count(String filter, Map<String, Value> bindings, RdfMapper<T> m) {
        return run(
                false,
                c -> {
                    try (TupleQueryResult rows =
                            query(
                                            c,
                                            "SELECT (COUNT(DISTINCT ?s) AS ?n) WHERE { "
                                                    + where(m, filter)
                                                    + " }",
                                            bindings)
                                    .evaluate()) {
                        return ((Literal) rows.next().getValue("n")).longValue();
                    }
                });
    }

    public <T> List<T> find(
            String filter, Map<String, Value> bindings, Sort sort, Pageable page, RdfMapper<T> m) {
        StringBuilder extra = new StringBuilder(), order = new StringBuilder();
        int i = 0;
        for (Sort.Order o : sort) {
            String prop = o.getProperty();
            m.propertyType(prop);
            if (o.getNullHandling() != Sort.NullHandling.NATIVE) {
                throw new IllegalArgumentException("Solo NullHandling.NATIVE");
            }
            String v;
            if (prop.equals(m.idProperty())) {
                v = "?s";
            } else {
                v = "?sort" + i++;
                extra.append(" OPTIONAL { ?s <")
                        .append(m.predicates.get(prop))
                        .append("> ")
                        .append(v)
                        .append(" } ");
            }
            if (o.isIgnoreCase()) {
                if (m.propertyType(prop) != String.class) {
                    throw new IllegalArgumentException("IgnoreCase requiere String");
                }
                v = "LCASE(STR(" + v + "))";
            }
            order.append(o.isAscending() ? " ASC(" : " DESC(").append(v).append(")");
        }
        order.append(" ASC(STR(?s))");
        String q =
                "SELECT DISTINCT ?s WHERE { " + where(m, filter + extra) + " } ORDER BY " + order;
        if (page.isPaged()) {
            q += " LIMIT " + page.getPageSize() + " OFFSET " + page.getOffset();
        }
        return select(q, bindings, m);
    }

    public <T> Page<T> page(
            String filter, Map<String, Value> bindings, Pageable p, RdfMapper<T> m) {
        List<T> content = find(filter, bindings, p.getSort(), p, m);
        return p.isUnpaged()
                ? new PageImpl<>(content)
                : new PageImpl<>(content, p, count(filter, bindings, m));
    }
}
