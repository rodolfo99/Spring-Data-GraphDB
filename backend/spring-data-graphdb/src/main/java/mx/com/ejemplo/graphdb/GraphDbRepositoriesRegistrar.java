package mx.com.ejemplo.graphdb;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.*;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.util.ClassUtils;

import java.util.*;

public class GraphDbRepositoriesRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> a =
                metadata.getAnnotationAttributes(EnableGraphDbRepositories.class.getName());
        if (a == null) {
            return;
        }
        Set<String> packages = new LinkedHashSet<>(Arrays.asList((String[]) a.get("basePackages")));
        for (Class<?> c : (Class<?>[]) a.get("basePackageClasses")) {
            packages.add(c.getPackageName());
        }
        if (packages.isEmpty()) {
            packages.add(ClassUtils.getPackageName(metadata.getClassName()));
        }
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition d) {
                        return d.getMetadata().isIndependent() && d.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(GraphDbRepository.class));
        scanner.addExcludeFilter(new AnnotationTypeFilter(NoRepositoryBean.class));
        for (String pkg : packages) {
            for (var candidate : scanner.findCandidateComponents(pkg)) {
                String name = Objects.requireNonNull(candidate.getBeanClassName());
                RootBeanDefinition definition =
                        new RootBeanDefinition(GraphDbRepositoryFactoryBean.class);
                definition.getConstructorArgumentValues().addIndexedArgumentValue(0, name);
                definition
                        .getPropertyValues()
                        .add("template", new RuntimeBeanReference((String) a.get("templateRef")));
                // Nombre completo evita colisiones entre interfaces homonimas en paquetes
                // distintos.
                if (!registry.containsBeanDefinition(name)) {
                    registry.registerBeanDefinition(name, definition);
                }
            }
        }
    }
}
