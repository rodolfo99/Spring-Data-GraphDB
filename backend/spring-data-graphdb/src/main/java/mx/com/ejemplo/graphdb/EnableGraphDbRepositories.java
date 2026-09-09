package mx.com.ejemplo.graphdb;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(GraphDbRepositoriesRegistrar.class)
public @interface EnableGraphDbRepositories {
    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};

    String templateRef() default "graphDbTemplate";
}
