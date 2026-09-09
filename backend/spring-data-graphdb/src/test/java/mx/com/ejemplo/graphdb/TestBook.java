package mx.com.ejemplo.graphdb;

import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;

@RdfEntity(type = "urn:test:Book", namespace = "urn:test:book:", graph = "urn:test:graph")
public class TestBook {
    @Id public String id;

    @RdfProperty("urn:test:title")
    public String title;

    @RdfProperty("urn:test:author")
    public String author;

    @RdfProperty("urn:test:year")
    public Integer year;

    @RdfProperty("urn:test:price")
    public BigDecimal price;

    @RdfProperty("urn:test:published")
    public LocalDate published;

    @RdfProperty("urn:test:website")
    public URI website;

    @RdfProperty("urn:test:active")
    public Boolean active;

    @RdfProperty("urn:test:visits")
    public Long visits;

    @RdfProperty("urn:test:score")
    public Double score;

    public TestBook() {}

    public TestBook(String title, String author, Integer year) {
        this.title = title;
        this.author = author;
        this.year = year;
    }
}
