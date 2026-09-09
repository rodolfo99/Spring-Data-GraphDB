package mx.com.ejemplo.graphdb;

import org.springframework.data.repository.core.support.*;

public class GraphDbRepositoryFactoryBean<R extends GraphDbRepository<T>, T>
        extends RepositoryFactoryBeanSupport<R, T, String> {
    private GraphDbTemplate template;

    public GraphDbRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    public void setTemplate(GraphDbTemplate template) {
        this.template = template;
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory() {
        return new GraphDbRepositoryFactory(java.util.Objects.requireNonNull(template));
    }
}
