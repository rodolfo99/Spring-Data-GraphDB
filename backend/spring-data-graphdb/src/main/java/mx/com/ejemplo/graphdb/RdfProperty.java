package mx.com.ejemplo.graphdb;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RdfProperty {
    String value();
}
