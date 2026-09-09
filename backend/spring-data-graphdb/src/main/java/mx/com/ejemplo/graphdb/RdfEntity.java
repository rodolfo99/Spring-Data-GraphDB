package mx.com.ejemplo.graphdb;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RdfEntity {
    String type();

    String namespace();

    String graph();
}
