import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { map, timeout } from 'rxjs';

export interface Libro {
  id: string;
  titulo: string;
  autor: string;
  anio: number | null;
}

export type EntradaLibro = Omit<Libro, 'id'>;

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface Filtros {
  titulo: string;
  autor: string;
  pagina: number;
  tamanio: number;
  ordenar: string;
  direccion: string;
}

export function validarLibro(value: unknown): Libro {
  const b = value as Libro | null;
  if (
    !b ||
    typeof b.id !== 'string' ||
    !/^[A-Za-z0-9_-]+$/.test(b.id) ||
    typeof b.titulo !== 'string' ||
    typeof b.autor !== 'string' ||
    (b.anio !== null && (!Number.isInteger(b.anio) || b.anio < 0 || b.anio > 9999))
  ) {
    throw new Error(
      'La API devolvió un libro con formato inesperado. Comprueba que estás usando el backend de GraphDB de esta entrega.',
    );
  }
  return b;
}

export function validarPagina(value: unknown): Pagina<Libro> {
  const p = value as Pagina<Libro> | null;
  if (
    !p ||
    !Array.isArray(p.contenido) ||
    ![p.pagina, p.tamanio, p.totalElementos, p.totalPaginas].every(
      (n) => Number.isInteger(n) && n >= 0,
    ) ||
    p.tamanio < 1
  ) {
    throw new Error(
      'La respuesta no tiene el formato esperado: contenido, pagina, tamanio, totalElementos y totalPaginas.',
    );
  }
  const contenido = p.contenido.map(validarLibro);
  if (new Set(contenido.map((b) => b.id)).size !== contenido.length) {
    throw new Error('La API devolvió identificadores duplicados.');
  }
  return { ...p, contenido };
}

export function mensajeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0 || [502, 503, 504].includes(error.status)) {
      return 'No se pudo conectar con la API o con GraphDB. Comprueba que el backend y la base de datos estén en ejecución.';
    }
    if (error.status === 404) {
      return 'El libro ya no existe o la ruta de la API no está disponible. Actualiza la lista.';
    }
    if (error.status === 401 || error.status === 403) {
      return 'El servidor rechazó el acceso. Revisa la configuración del backend.';
    }
    const detail = error.error?.detail;
    return typeof detail === 'string'
      ? detail
      : `No se pudo completar la operación (HTTP ${error.status}).`;
  }
  if (error instanceof Error && error.name === 'TimeoutError') {
    return 'El servidor tardó demasiado en responder. Actualiza la lista antes de repetir una escritura: es posible que se haya guardado.';
  }
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
}

@Injectable({ providedIn: 'root' })
export class LibrosApi {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/libros';

  listar(f: Filtros) {
    return this.http
      .get<unknown>(this.url, { params: new HttpParams({ fromObject: { ...f } }) })
      .pipe(timeout(45000), map(validarPagina));
  }

  crear(e: EntradaLibro) {
    return this.http.post<unknown>(this.url, e).pipe(timeout(45000), map(validarLibro));
  }

  actualizar(id: string, e: EntradaLibro) {
    return this.http
      .put<unknown>(`${this.url}/${encodeURIComponent(id)}`, e)
      .pipe(timeout(45000), map(validarLibro));
  }

  borrar(id: string) {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`).pipe(timeout(45000));
  }
}
