package mx.com.ejemplo.graphdb;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.springframework.data.annotation.Id;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

/** Mapeo deliberadamente escalar; falla ante entidades ambiguas. */
public final class RdfMapper<T> {
    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    final Class<T> javaType;
    final IRI type, graph;
    final String namespace;
    private final Field id;
    private final Constructor<T> constructor;
    final Map<String, Field> fields = new LinkedHashMap<>();
    final Map<String, IRI> predicates = new LinkedHashMap<>();

    public RdfMapper(Class<T> javaType) {
        this.javaType = javaType;
        RdfEntity entity = javaType.getAnnotation(RdfEntity.class);
        if (entity == null) {
            throw new IllegalArgumentException("Falta @RdfEntity: " + javaType);
        }
        type = iri(entity.type());
        graph = iri(entity.graph());
        namespace = entity.namespace();
        iri(namespace);
        if (!namespace.endsWith("/") && !namespace.endsWith("#") && !namespace.endsWith(":")) {
            throw new IllegalArgumentException("namespace debe terminar en /, # o :");
        }
        try {
            constructor = javaType.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Se necesita constructor sin argumentos", e);
        }
        Field found = null;
        for (Class<?> c = javaType; c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class) || f.isAnnotationPresent(RdfProperty.class)) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                        throw new IllegalArgumentException("Campo estatico/final: " + f);
                    }
                    f.setAccessible(true);
                }
                if (f.isAnnotationPresent(Id.class)) {
                    if (found != null
                            || f.getType() != String.class
                            || f.isAnnotationPresent(RdfProperty.class)) {
                        throw new IllegalArgumentException("Un solo @Id String, sin @RdfProperty");
                    }
                    found = f;
                } else if (f.isAnnotationPresent(RdfProperty.class)) {
                    if (!supported(f.getType())) {
                        throw new IllegalArgumentException("Tipo no soportado: " + f);
                    }
                    IRI p = iri(f.getAnnotation(RdfProperty.class).value());
                    if (p.equals(RDF.TYPE)
                            || predicates.containsValue(p)
                            || fields.containsKey(f.getName())) {
                        throw new IllegalArgumentException(
                                "Predicado/campo duplicado o rdf:type: " + f);
                    }
                    fields.put(f.getName(), f);
                    predicates.put(f.getName(), p);
                }
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("Falta @Id String");
        }
        id = found;
    }

    static boolean supported(Class<?> c) {
        return Set.of(
                        String.class,
                        Integer.class,
                        Long.class,
                        Double.class,
                        Boolean.class,
                        BigDecimal.class,
                        LocalDate.class,
                        URI.class)
                .contains(c);
    }

    static IRI iri(String s) {
        if (s == null
                || !URI.create(s).isAbsolute()
                || s.chars()
                        .anyMatch(
                                c -> Character.isWhitespace(c) || "<>\"{}|^`\\".indexOf(c) >= 0)) {
            throw new IllegalArgumentException("IRI absoluta invalida: " + s);
        }
        return VF.createIRI(s);
    }

    public String id(T entity) {
        return (String) get(id, entity);
    }

    void assignId(T entity) {
        if (id(entity) == null) {
            set(id, entity, UUID.randomUUID().toString());
        }
        subject(id(entity));
    }

    IRI subject(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("ID debe usar letras, numeros, guion o guion bajo");
        }
        return iri(namespace + id);
    }

    String localId(IRI subject) {
        if (!subject.stringValue().startsWith(namespace)) {
            throw new IllegalArgumentException("IRI fuera del namespace: " + subject);
        }
        String key = subject.stringValue().substring(namespace.length());
        subject(key);
        return key;
    }

    String idProperty() {
        return id.getName();
    }

    Class<?> propertyType(String name) {
        if (name.equals(idProperty())) {
            return String.class;
        }
        Field f = fields.get(name);
        if (f == null) {
            throw new IllegalArgumentException("Propiedad RDF desconocida: " + name);
        }
        return f.getType();
    }

    Value propertyValue(String name, Object value) {
        Objects.requireNonNull(value, "Parametro null: use IsNull/IsNotNull");
        if (!propertyType(name).isInstance(value)) {
            throw new IllegalArgumentException("Tipo de parametro incorrecto: " + name);
        }
        return name.equals(idProperty()) ? subject((String) value) : value(value);
    }

    static Value value(Object o) {
        Objects.requireNonNull(o, "Parametro RDF null");
        if (o instanceof String x) {
            return VF.createLiteral(x);
        }
        if (o instanceof Integer x) {
            return VF.createLiteral(x);
        }
        if (o instanceof Long x) {
            return VF.createLiteral(x);
        }
        if (o instanceof Double x) {
            return VF.createLiteral(x);
        }
        if (o instanceof Boolean x) {
            return VF.createLiteral(x);
        }
        if (o instanceof BigDecimal x) {
            return VF.createLiteral(x);
        }
        if (o instanceof LocalDate x) {
            return VF.createLiteral(x.toString(), org.eclipse.rdf4j.model.vocabulary.XSD.DATE);
        }
        if (o instanceof URI x) {
            return iri(x.toString());
        }
        throw new IllegalArgumentException("Tipo RDF no soportado: " + o.getClass());
    }

    List<Statement> statements(T entity) {
        List<Statement> out = new ArrayList<>();
        IRI s = subject(id(entity));
        out.add(VF.createStatement(s, RDF.TYPE, type, graph));
        fields.forEach(
                (name, f) -> {
                    Object v = get(f, entity);
                    if (v != null) {
                        out.add(VF.createStatement(s, predicates.get(name), value(v), graph));
                    }
                });
        return out;
    }

    T read(IRI subject, Map<IRI, List<Value>> values) {
        try {
            T entity = constructor.newInstance();
            set(id, entity, localId(subject));
            fields.forEach(
                    (name, f) -> {
                        List<Value> vs = values.getOrDefault(predicates.get(name), List.of());
                        if (vs.size() > 1) {
                            throw new IllegalStateException(
                                    "Campo escalar con varios valores: " + subject + " " + name);
                        }
                        if (!vs.isEmpty()) {
                            set(f, entity, decode(vs.get(0), f.getType()));
                        }
                    });
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object decode(Value v, Class<?> c) {
        if (c == URI.class) {
            if (!(v instanceof IRI)) {
                throw new IllegalStateException("Se esperaba IRI");
            }
            return URI.create(v.stringValue());
        }
        if (!(v instanceof Literal l)) {
            throw new IllegalStateException("Se esperaba literal RDF");
        }
        if (c == String.class) {
            return l.getLabel();
        }
        if (c == Integer.class) {
            return l.intValue();
        }
        if (c == Long.class) {
            return l.longValue();
        }
        if (c == Double.class) {
            return l.doubleValue();
        }
        if (c == Boolean.class) {
            return l.booleanValue();
        }
        if (c == BigDecimal.class) {
            return l.decimalValue();
        }
        if (c == LocalDate.class) {
            return LocalDate.parse(l.getLabel());
        }
        throw new IllegalArgumentException("Tipo no soportado: " + c);
    }

    private static Object get(Field f, Object o) {
        try {
            return f.get(o);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field f, Object o, Object v) {
        try {
            f.set(o, v);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
