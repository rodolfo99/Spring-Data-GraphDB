package mx.com.ejemplo.demo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "graphdb.url=http://127.0.0.1:1/repositories/unused")
class ApplicationTest {
    @Autowired TestRestTemplate http;

    @Test
    void webApplicationStartsAndValidatesPagingWithoutDatabaseAccess() {
        var response = http.getForEntity("/api/libros?pagina=-1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("pagina");
    }

    @Test
    void jsonAndBeanValidationWork() {
        var response =
                http.postForEntity(
                        "/api/libros", new LibroController.Entrada("", "", 2026), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
