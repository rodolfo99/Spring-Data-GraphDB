package mx.com.ejemplo.graphdb;

import org.springframework.data.repository.core.*;
import org.springframework.data.repository.core.support.*;
import org.springframework.data.repository.query.*;

import java.util.Optional;

public class GraphDbRepositoryFactory extends RepositoryFactorySupport {
    private final GraphDbTemplate template;

    public GraphDbRepositoryFactory(GraphDbTemplate template) {
        this.template = template;
    }

    @Override
    public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        RdfMapper<T> m = new RdfMapper<>(domainClass);
        @SuppressWarnings("unchecked")
        EntityInformation<T, ID> info =
                (EntityInformation<T, ID>)
                        new AbstractEntityInformation<T, String>(domainClass) {
                            @Override
                            public String getId(T entity) {
                                return m.id(entity);
                            }

                            @Override
                            public Class<String> getIdType() {
                                return String.class;
                            }
                        };
        return info;
    }

    @Override
    protected Object getTargetRepository(RepositoryInformation info) {
        return new SimpleGraphDbRepository<>(template, new RdfMapper<>(info.getDomainType()));
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SimpleGraphDbRepository.class;
    }

    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
            QueryLookupStrategy.Key key, ValueExpressionDelegate delegate) {
        return Optional.of(
                (method, metadata, factory, namedQueries) ->
                        new GraphDbRepositoryQuery(method, metadata, factory, template));
    }
}
