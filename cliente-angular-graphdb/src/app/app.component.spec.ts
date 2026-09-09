import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { Libro, validarPagina } from './libros-api.service';

describe('Cliente GraphDB: contrato HTTP y operaciones', () => {
  let fixture: ComponentFixture<AppComponent>;
  let app: AppComponent;
  let http: HttpTestingController;
  const libro: Libro = {
    id: 'abc-123',
    titulo: 'Java efectivo',
    autor: 'Joshua Bloch',
    anio: 2018,
  };
  function lista(contenido: Libro[] = [libro], pagina = 0, tamanio = 10, total = contenido.length) {
    const req = http.expectOne((r) => r.method === 'GET' && r.url === '/api/libros');
    req.flush({
      contenido,
      pagina,
      tamanio,
      totalElementos: total,
      totalPaginas: Math.ceil(total / tamanio),
    });
    fixture.detectChanges();
    return req;
  }
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(AppComponent);
    app = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    lista();
  });
  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  it('muestra los botones de editar y borrar usando el ID recibido', () => {
    expect(
      fixture.nativeElement.querySelector('[aria-label="Editar Java efectivo"]'),
    ).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('[aria-label="Borrar Java efectivo"]'),
    ).not.toBeNull();
    expect(app.ocupado()).toBe(false);
  });

  it('crea con POST sin enviar ID, acepta año vacío y bloquea doble envío', () => {
    app.editor.setValue({ titulo: '  Nuevo libro  ', autor: ' Rodolfo ', anio: null });
    app.guardar();
    app.guardar();
    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/libros');
    expect(req.request.body).toEqual({ titulo: 'Nuevo libro', autor: 'Rodolfo', anio: null });
    req.flush({ id: 'nuevo-1', ...req.request.body });
    lista([{ id: 'nuevo-1', ...req.request.body }]);
    expect(app.editandoId()).toBeNull();
    expect(app.editor.controls.titulo.value).toBe('');
    expect(app.aviso()).toContain('creado');
    expect(app.ocupado()).toBe(false);
  });

  it('actualiza dos veces y conserva el borrado mediante el mismo ID', () => {
    let actual = { ...libro };
    for (const titulo of ['Java edición 2', 'Java edición 3']) {
      app.editar(actual);
      app.editor.controls.titulo.setValue(titulo);
      app.guardar();
      const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/libros/abc-123');
      expect(req.request.body.id).toBeUndefined();
      actual = { ...actual, titulo };
      req.flush(actual);
      lista([actual]);
      expect(fixture.nativeElement.querySelector(`[aria-label="Borrar ${titulo}"]`)).not.toBeNull();
    }
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[aria-label="Borrar Java edición 3"]',
    );
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirmar borrado');
    app.confirmarBorrado();
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/libros/abc-123')
      .flush(null, { status: 204, statusText: 'No Content' });
    lista([]);
    expect(app.datos()?.contenido).toEqual([]);
    expect(app.porBorrar()).toBeNull();
    expect(app.ocupado()).toBe(false);
  });

  it('cancela el borrado sin enviar DELETE', () => {
    app.pedirBorrado(libro);
    app.cancelarBorrado();
    app.confirmarBorrado();
    http.expectNone((r) => r.method === 'DELETE');
    expect(app.porBorrar()).toBeNull();
  });

  it('envía filtros y ordenamiento con los nombres exactos del backend', () => {
    app.filtros.setValue({
      titulo: '  Java & RDF ',
      autor: ' Bloch ',
      ordenar: 'anio',
      direccion: 'desc',
      tamanio: 5,
    });
    app.buscar();
    const req = lista([libro], 0, 5, 11);
    expect(req.request.params.get('titulo')).toBe('Java & RDF');
    expect(req.request.params.get('autor')).toBe('Bloch');
    expect(req.request.params.get('ordenar')).toBe('anio');
    expect(req.request.params.get('direccion')).toBe('desc');
    expect(req.request.params.get('pagina')).toBe('0');
    app.irPagina(1);
    const next = lista([libro], 1, 5, 11);
    expect(next.request.params.get('pagina')).toBe('1');
    expect(next.request.params.get('titulo')).toBe('Java & RDF');
  });

  it('regresa a una página existente al borrar el último libro de la última página', async () => {
    app.cargar();
    lista([libro], 0, 10, 11);
    app.irPagina(1);
    lista([libro], 1, 10, 11);
    app.pedirBorrado(libro);
    app.confirmarBorrado();
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush(null, { status: 204, statusText: 'No Content' });
    lista([], 1, 10, 10);
    await Promise.resolve();
    const req = lista([{ ...libro, id: 'otro-id' }], 0, 10, 10);
    expect(req.request.params.get('pagina')).toBe('0');
    expect(app.datos()?.pagina).toBe(0);
  });

  it('mantiene los datos editados si falla el guardado y permite reintentar', () => {
    app.editar(libro);
    app.editor.controls.titulo.setValue('Cambios pendientes');
    app.guardar();
    http
      .expectOne((r) => r.method === 'PUT')
      .flush(
        { detail: 'Servicio temporalmente no disponible' },
        { status: 503, statusText: 'Unavailable' },
      );
    expect(app.editor.controls.titulo.value).toBe('Cambios pendientes');
    expect(app.editandoId()).toBe(libro.id);
    expect(app.guardando()).toBe(false);
    expect(app.error()).toContain('No se pudo conectar');
  });

  it('muestra el fallo de conexión y descarta la lista anterior', () => {
    app.cargar();
    http.expectOne((r) => r.method === 'GET').error(new ProgressEvent('error'));
    expect(app.datos()).toBeNull();
    expect(app.error()).toContain('No se pudo conectar');
    expect(app.cargando()).toBe(false);
  });

  it('rechaza espacios en campos obligatorios y años fraccionarios antes de escribir', () => {
    app.editor.setValue({ titulo: '   ', autor: 'Autor', anio: 2026.5 });
    app.guardar();
    expect(app.editor.invalid).toBe(true);
    http.expectNone((r) => r.method === 'POST');
    app.editor.setValue({ titulo: 'Válido', autor: 'Autor', anio: 0 });
    expect(app.editor.valid).toBe(true);
  });

  it('detecta otro formato de backend e IDs numéricos', () => {
    expect(() => validarPagina({ content: [libro], totalElements: 1 })).toThrow();
    expect(() =>
      validarPagina({
        contenido: [{ ...libro, id: 1 }],
        pagina: 0,
        tamanio: 10,
        totalElementos: 1,
        totalPaginas: 1,
      }),
    ).toThrow();
  });
});
