package mx.com.ejemplo.graphdb;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.*;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.*;
import org.springframework.data.repository.query.parser.*;

import java.lang.reflect.Method;
import java.util.*;

final class GraphDbRepositoryQuery implements RepositoryQuery {
    private final Method method;
    private final QueryMethod queryMethod;
    private final GraphDbTemplate template;
    private final RdfMapper<?> mapper;
    private final PartTree tree;
    private final String sparql;
    private final List<Integer> parameters = new ArrayList<>();
    private int pageableIndex = -1, sortIndex = -1;

    GraphDbRepositoryQuery(
            Method method,
            RepositoryMetadata metadata,
            ProjectionFactory factory,
            GraphDbTemplate template) {
        this.method = method;
        this.queryMethod = new QueryMethod(method, metadata, factory);
        this.template = template;
        mapper = new RdfMapper<>(metadata.getDomainType());
        for (int i = 0; i < method.getParameterCount(); i++) {
            Class<?> p = method.getParameterTypes()[i];
            if (p == Pageable.class) {
                if (pageableIndex >= 0) {
                    fail("Pageable duplicado");
                }
                pageableIndex = i;
            } else if (p == Sort.class) {
                if (sortIndex >= 0) {
                    fail("Sort duplicado");
                }
                sortIndex = i;
            } else {
                parameters.add(i);
            }
        }
        if (pageableIndex >= 0 && sortIndex >= 0) {
            fail("Use Pageable con Sort, no ambos parametros");
        }
        SparqlQuery annotation = method.getAnnotation(SparqlQuery.class);
        Class<?> result = method.getReturnType();
        if (annotation != null) {
            sparql = annotation.value();
            tree = null;
            if (pageableIndex >= 0 || sortIndex >= 0) {
                fail("@SparqlQuery no admite Pageable/Sort; incluya ORDER BY/LIMIT en SPARQL");
            }
            var parsed = QueryParserUtil.parseTupleQuery(QueryLanguage.SPARQL, sparql, null);
            if (!parsed.getTupleExpr().getBindingNames().contains("s")) {
                fail("@SparqlQuery requiere SELECT ?s");
            }
            Set<String> names = new HashSet<>();
            for (int i : parameters) {
                Param p = method.getParameters()[i].getAnnotation(Param.class);
                if (p == null
                        || !p.value().matches("[A-Za-z_][A-Za-z0-9_]*")
                        || !names.add(p.value())) {
                    fail("Use @Param con nombres SPARQL validos y unicos");
                }
            }
        } else {
            sparql = null;
            tree = new PartTree(method.getName(), metadata.getDomainType());
            if (tree.isDelete() || tree.isLimiting()) {
                fail("Delete/Top/First derivados no implementados");
            }
            int expected = 0;
            for (Part p : tree.getParts()) {
                String name = p.getProperty().toDotPath();
                Class<?> type = mapper.propertyType(name);
                switch (p.getType()) {
                    case SIMPLE_PROPERTY,
                            NEGATING_SIMPLE_PROPERTY,
                            CONTAINING,
                            STARTING_WITH,
                            ENDING_WITH,
                            GREATER_THAN,
                            GREATER_THAN_EQUAL,
                            LESS_THAN,
                            LESS_THAN_EQUAL,
                            IS_NULL,
                            IS_NOT_NULL,
                            TRUE,
                            FALSE -> {}
                    default -> fail("Operador no implementado: " + p.getType());
                }
                if (Set.of(Part.Type.CONTAINING, Part.Type.STARTING_WITH, Part.Type.ENDING_WITH)
                                .contains(p.getType())
                        && (type != String.class || name.equals(mapper.idProperty()))) {
                    fail("Operador de texto requiere @RdfProperty String");
                }
                if (p.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER
                        && (type != String.class || name.equals(mapper.idProperty()))) {
                    fail("IgnoreCase requiere @RdfProperty String");
                }
                if ((p.getType() == Part.Type.TRUE || p.getType() == Part.Type.FALSE)
                        && type != Boolean.class) {
                    fail("True/False requiere Boolean");
                }
                expected += p.getNumberOfArguments();
            }
            if (expected != parameters.size()) {
                fail("Cantidad de parametros incorrecta");
            }
            if ((tree.isCountProjection() || tree.isExistsProjection())
                    && (pageableIndex >= 0 || sortIndex >= 0)) {
                fail("count/exists no admiten Pageable/Sort");
            }
            if (tree.isCountProjection()) {
                if (result != long.class && result != Long.class) {
                    fail("countBy debe devolver long");
                }
                return;
            }
            if (tree.isExistsProjection()) {
                if (result != boolean.class && result != Boolean.class) {
                    fail("existsBy debe devolver boolean");
                }
                return;
            }
        }
        if (result != List.class
                && result != Optional.class
                && result != Page.class
                && result != metadata.getDomainType()) {
            fail("Retorno admitido: entidad, List<T>, Optional<T>, Page<T>");
        }
        if (result == Page.class && (pageableIndex < 0 || sparql != null)) {
            fail("Page requiere Pageable y consulta derivada");
        }
        if (result != Page.class && result != List.class && pageableIndex >= 0) {
            fail("Pageable solo para List/Page");
        }
        if (queryMethod.getReturnedObjectType() != metadata.getDomainType()) {
            fail("Proyecciones no implementadas: devuelva el tipo de entidad");
        }
    }

    private void fail(String message) {
        throw new IllegalArgumentException(method.toGenericString() + ": " + message);
    }

    @Override
    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    @Override
    public Object execute(Object[] args) {
        Map<String, Value> bindings = new LinkedHashMap<>();
        if (sparql != null) {
            for (int i : parameters) {
                bindings.put(
                        method.getParameters()[i].getAnnotation(Param.class).value(),
                        RdfMapper.value(args[i]));
            }
            return adapt(template.select(sparql, bindings, mapper));
        }
        int index = 0;
        List<String> alternatives = new ArrayList<>();
        for (PartTree.OrPart or : tree) {
            StringBuilder and = new StringBuilder("?s a <" + mapper.type + "> . ");
            for (Part part : or) {
                String name = part.getProperty().toDotPath(), v = "?v" + index, b = "p" + index;
                boolean id = name.equals(mapper.idProperty());
                String pattern = id ? "" : "?s <" + mapper.predicates.get(name) + "> " + v + " . ";
                if (id) {
                    v = "?s";
                }
                Part.Type op = part.getType();
                if (op == Part.Type.IS_NULL || op == Part.Type.IS_NOT_NULL) {
                    and.append(
                            id
                                    ? (op == Part.Type.IS_NULL ? "FILTER(false) " : "FILTER(true) ")
                                    : "FILTER "
                                            + (op == Part.Type.IS_NULL ? "NOT " : "")
                                            + "EXISTS { "
                                            + pattern
                                            + " } ");
                    continue;
                }
                String rhs;
                if (op == Part.Type.TRUE || op == Part.Type.FALSE) {
                    rhs = op == Part.Type.TRUE ? "true" : "false";
                } else {
                    bindings.put(b, mapper.propertyValue(name, args[parameters.get(index)]));
                    rhs = "?" + b;
                    index++;
                }
                String left = v;
                if (part.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER) {
                    left = "LCASE(STR(" + v + "))";
                    rhs = "LCASE(STR(" + rhs + "))";
                }
                String expression =
                        switch (op) {
                            case SIMPLE_PROPERTY -> left + " = " + rhs;
                            case NEGATING_SIMPLE_PROPERTY -> left + " != " + rhs;
                            case GREATER_THAN -> left + " > " + rhs;
                            case GREATER_THAN_EQUAL -> left + " >= " + rhs;
                            case LESS_THAN -> left + " < " + rhs;
                            case LESS_THAN_EQUAL -> left + " <= " + rhs;
                            case CONTAINING -> "CONTAINS(STR(" + left + "), STR(" + rhs + "))";
                            case STARTING_WITH -> "STRSTARTS(STR(" + left + "), STR(" + rhs + "))";
                            case ENDING_WITH -> "STRENDS(STR(" + left + "), STR(" + rhs + "))";
                            case TRUE, FALSE -> left + " = " + rhs;
                            default -> throw new IllegalStateException("Operador no soportado");
                        };
                and.append(
                        id
                                ? "FILTER(" + expression + ") "
                                : "FILTER EXISTS { " + pattern + " FILTER(" + expression + ") } ");
            }
            alternatives.add("{ " + and + " }");
        }
        String filter = String.join(" UNION ", alternatives);
        if (tree.isCountProjection()) {
            return template.count(filter, bindings, mapper);
        }
        if (tree.isExistsProjection()) {
            return template.count(filter, bindings, mapper) > 0;
        }
        Pageable page =
                pageableIndex < 0
                        ? Pageable.unpaged()
                        : Objects.requireNonNull((Pageable) args[pageableIndex]);
        Sort dynamic =
                sortIndex < 0 ? page.getSort() : Objects.requireNonNull((Sort) args[sortIndex]);
        Sort sort = tree.getSort().and(dynamic);
        if (method.getReturnType() == Page.class) {
            Pageable sorted =
                    page.isPaged()
                            ? PageRequest.of(page.getPageNumber(), page.getPageSize(), sort)
                            : Pageable.unpaged(sort);
            return template.page(filter, bindings, sorted, mapper);
        }
        return adapt(template.find(filter, bindings, sort, page, mapper));
    }

    private Object adapt(List<?> result) {
        if (method.getReturnType() == List.class) {
            return result;
        }
        if (result.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, result.size());
        }
        if (method.getReturnType() == Optional.class) {
            return result.stream().findFirst();
        }
        return result.isEmpty() ? null : result.get(0);
    }
}
