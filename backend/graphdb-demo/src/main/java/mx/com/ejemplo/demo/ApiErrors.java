package mx.com.ejemplo.demo;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> unavailable(DataAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "No se pudo acceder a GraphDB. Revise la conexion, credenciales y"
                                        + " los registros del servidor."));
    }
}
