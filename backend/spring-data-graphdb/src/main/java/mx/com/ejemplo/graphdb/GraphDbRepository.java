package mx.com.ejemplo.graphdb;

import org.springframework.data.repository.*;

@NoRepositoryBean
public interface GraphDbRepository<T>
        extends ListCrudRepository<T, String>, ListPagingAndSortingRepository<T, String> {}
