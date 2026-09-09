package mx.com.ejemplo.demo;

import mx.com.ejemplo.graphdb.GraphDbTemplate;

import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configura la conexión HTTP a GraphDB y la plantilla utilizada por los repositorios. */
@Configuration
public class GraphDbConfig {
    // Spring abre el cliente al arrancar y lo cierra al detener la aplicación.
    @Bean(initMethod = "init", destroyMethod = "shutDown")
    HTTPRepository rdfRepository(
            @Value("${graphdb.url}") String url,
            @Value("${graphdb.username:}") String user,
            @Value("${graphdb.password:}") String password) {
        HTTPRepository repository = new HTTPRepository(url);
        if (!user.isBlank()) {
            repository.setUsernameAndPassword(user, password);
        }
        return repository;
    }

    @Bean
    GraphDbTemplate graphDbTemplate(HTTPRepository repository) {
        return new GraphDbTemplate(repository);
    }
}
