import {
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize } from 'rxjs';
import {
  EntradaLibro,
  Filtros,
  Libro,
  LibrosApi,
  Pagina,
  mensajeError,
} from './libros-api.service';

const noVacio = (c: AbstractControl): ValidationErrors | null =>
  typeof c.value === 'string' && c.value.trim() ? null : { vacio: true };

const anioValido = (c: AbstractControl): ValidationErrors | null =>
  c.value === null || (Number.isInteger(c.value) && c.value >= 0 && c.value <= 9999)
    ? null
    : { anio: true };

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './app.component.html',
})
export class AppComponent {
  private readonly api = inject(LibrosApi);
  private readonly destroyRef = inject(DestroyRef);
  private lectura?: Subscription;
  @ViewChild('tituloEditor') tituloEditor?: ElementRef<HTMLInputElement>;

  // Estado observable de la pantalla: los signals notifican cambios sin Zone.js.
  readonly datos = signal<Pagina<Libro> | null>(null);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly ocupado = computed(() => this.cargando() || this.guardando());
  readonly error = signal('');
  readonly aviso = signal('');
  readonly editandoId = signal<string | null>(null);
  readonly porBorrar = signal<Libro | null>(null);

  // Los filtros del formulario se aplican al pulsar Buscar.
  readonly filtros = new FormGroup({
    titulo: new FormControl('', { nonNullable: true }),
    autor: new FormControl('', { nonNullable: true }),
    tamanio: new FormControl(10, { nonNullable: true }),
    ordenar: new FormControl('titulo', { nonNullable: true }),
    direccion: new FormControl('asc', { nonNullable: true }),
  });

  // El ID se mantiene fuera del formulario para conservarlo al actualizar.
  readonly editor = new FormGroup({
    titulo: new FormControl('', {
      nonNullable: true,
      validators: [noVacio, Validators.maxLength(500)],
    }),
    autor: new FormControl('', {
      nonNullable: true,
      validators: [noVacio, Validators.maxLength(300)],
    }),
    anio: new FormControl<number | null>(null, { validators: [anioValido] }),
  });
  private consulta: Filtros = {
    titulo: '',
    autor: '',
    pagina: 0,
    tamanio: 10,
    ordenar: 'titulo',
    direccion: 'asc',
  };
  readonly primeraFila = computed(() => {
    const p = this.datos();
    return p && p.contenido.length ? p.pagina * p.tamanio + 1 : 0;
  });
  readonly ultimaFila = computed(() => {
    const p = this.datos();
    return p ? p.pagina * p.tamanio + p.contenido.length : 0;
  });

  constructor() {
    this.cargar();
  }

  /** Consulta la API y cancela una lectura anterior que todavía estuviera pendiente. */

  cargar(): void {
    this.lectura?.unsubscribe();
    this.cargando.set(true);
    this.error.set('');
    this.porBorrar.set(null);
    this.lectura = this.api
      .listar(this.consulta)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (p) => {
          const ultima = Math.max(0, p.totalPaginas - 1);
          if (this.consulta.pagina > ultima) {
            this.consulta = { ...this.consulta, pagina: ultima };
            // Esperar al finalize de esta lectura antes de iniciar otra.
            queueMicrotask(() => {
              if (!this.destroyRef.destroyed) {
                this.cargar();
              }
            });
            return;
          }
          this.datos.set(p);
        },
        error: (e) => {
          this.datos.set(null);
          this.error.set(mensajeError(e));
        },
      });
  }

  buscar(): void {
    if (this.ocupado()) {
      return;
    }
    const f = this.filtros.getRawValue();
    this.consulta = { ...f, titulo: f.titulo.trim(), autor: f.autor.trim(), pagina: 0 };
    this.aviso.set('');
    this.cargar();
  }

  limpiar(): void {
    if (this.ocupado()) {
      return;
    }
    this.filtros.reset({ titulo: '', autor: '', tamanio: 10, ordenar: 'titulo', direccion: 'asc' });
    this.buscar();
  }

  irPagina(pagina: number): void {
    if (this.ocupado() || pagina < 0 || pagina >= (this.datos()?.totalPaginas ?? 0)) {
      return;
    }
    this.consulta = { ...this.consulta, pagina };
    this.cargar();
  }

  nuevo(): void {
    if (this.ocupado()) {
      return;
    }
    this.reiniciarEditor();
    this.porBorrar.set(null);
    this.tituloEditor?.nativeElement.focus();
  }

  editar(libro: Libro): void {
    if (this.ocupado()) {
      return;
    }
    this.editandoId.set(libro.id); // ID fuera de los controles editables.
    this.editor.reset({ titulo: libro.titulo, autor: libro.autor, anio: libro.anio });
    this.porBorrar.set(null);
    this.tituloEditor?.nativeElement.focus();
  }

  private reiniciarEditor(): void {
    this.editandoId.set(null);
    this.editor.reset({ titulo: '', autor: '', anio: null });
  }

  /** Usa POST para libros nuevos y PUT cuando existe un ID en edición. */

  guardar(): void {
    if (this.ocupado()) {
      return;
    }
    this.editor.markAllAsTouched();
    if (this.editor.invalid) {
      return;
    }
    const f = this.editor.getRawValue();
    const entrada: EntradaLibro = { titulo: f.titulo.trim(), autor: f.autor.trim(), anio: f.anio };
    const id = this.editandoId();
    this.guardando.set(true);
    this.error.set('');
    this.aviso.set('');
    this.porBorrar.set(null);
    const peticion = id === null ? this.api.crear(entrada) : this.api.actualizar(id, entrada);
    peticion
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: () => {
          this.reiniciarEditor();
          this.aviso.set(
            id === null
              ? 'Libro creado. La lista mantiene los filtros actuales.'
              : 'Cambios guardados. La lista mantiene los filtros actuales.',
          );
          this.cargar();
        },
        error: (e) => this.error.set(mensajeError(e)),
      });
  }

  pedirBorrado(libro: Libro): void {
    if (!this.ocupado()) {
      this.porBorrar.set(libro);
    }
  }

  cancelarBorrado(): void {
    if (!this.ocupado()) {
      this.porBorrar.set(null);
    }
  }

  /** Solo envía DELETE después de la confirmación del usuario. */

  confirmarBorrado(): void {
    const libro = this.porBorrar();
    if (!libro || this.ocupado()) {
      return;
    }
    this.guardando.set(true);
    this.error.set('');
    this.aviso.set('');
    this.api
      .borrar(libro.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: () => {
          this.porBorrar.set(null);
          if (this.editandoId() === libro.id) {
            this.reiniciarEditor();
          }
          this.aviso.set('Libro eliminado.');
          this.cargar();
        },
        error: (e) => this.error.set(mensajeError(e)),
      });
  }
}
