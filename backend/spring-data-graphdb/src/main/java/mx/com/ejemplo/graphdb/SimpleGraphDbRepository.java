package mx.com.ejemplo.graphdb;

import org.springframework.data.domain.*;

import java.util.*;

public class SimpleGraphDbRepository<T> implements GraphDbRepository<T> {
    private final GraphDbTemplate template;
    private final RdfMapper<T> mapper;

    public SimpleGraphDbRepository(GraphDbTemplate template, RdfMapper<T> mapper) {
        this.template = template;
        this.mapper = mapper;
    }

    @Override
    public <S extends T> S save(S entity) {
        template.save(entity, mapper);
        return entity;
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> values = new ArrayList<>();
        entities.forEach(values::add);
        template.saveAll(new ArrayList<T>(values), mapper);
        return values;
    }

    @Override
    public Optional<T> findById(String id) {
        return template.findById(id, mapper);
    }

    @Override
    public boolean existsById(String id) {
        return template.existsById(id, mapper);
    }

    @Override
    public List<T> findAll() {
        return findAll(Sort.unsorted());
    }

    @Override
    public List<T> findAll(Sort sort) {
        return template.find("", Map.of(), sort, Pageable.unpaged(), mapper);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return template.page("", Map.of(), pageable, mapper);
    }

    @Override
    public List<T> findAllById(Iterable<String> ids) {
        return template.findAllById(ids, mapper);
    }

    @Override
    public long count() {
        return template.count("", Map.of(), mapper);
    }

    @Override
    public void deleteById(String id) {
        template.deleteAllById(List.of(id), mapper);
    }

    @Override
    public void delete(T entity) {
        deleteById(mapper.id(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        List<String> keys = new ArrayList<>();
        ids.forEach(keys::add);
        template.deleteAllById(keys, mapper);
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        List<String> keys = new ArrayList<>();
        entities.forEach(e -> keys.add(mapper.id(e)));
        template.deleteAllById(keys, mapper);
    }

    @Override
    public void deleteAll() {
        template.deleteAll(mapper);
    }
}
