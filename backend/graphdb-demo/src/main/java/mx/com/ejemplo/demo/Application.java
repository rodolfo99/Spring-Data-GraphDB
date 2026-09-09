package mx.com.ejemplo.demo;

import mx.com.ejemplo.graphdb.EnableGraphDbRepositories;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableGraphDbRepositories(basePackageClasses = LibroRepository.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
