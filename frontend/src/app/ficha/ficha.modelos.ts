/** Tipos de la ficha del alumno, iguales a los del backend (ADR-0007). */

export interface DatosFicha {
  dni: string | null;
  direccion: string | null;
  emergenciaNombre: string | null;
  emergenciaTelefono: string | null;
  objetivo: string | null;
  responsableNombre: string | null;
  responsableDni: string | null;
  responsableTelefono: string | null;
  responsableEmail: string | null;
  responsableVinculo: string | null;
}

export interface DocumentoLegal {
  id: number;
  codigo: string;
  version: number;
  titulo: string;
  texto: string;
  soloMenores: boolean;
}

export interface FichaAlumno {
  inscripcionId: number;
  nombre: string;
  apellido: string;
  email: string | null;
  fechaNacimiento: string;
  esMenor: boolean;
  estado: string;
  datos: DatosFicha;
  documentos: DocumentoLegal[];
}

/** Lo que se manda al enviar: los datos y QUÉ VERSIÓN de cada documento se aceptó. */
export interface EnvioFicha {
  datos: DatosFicha;
  documentos: { codigo: string; version: number }[];
}

/** Resultado de guardar o enviar: el estado final o la lista de errores del backend. */
export type ResultadoFicha =
  | { tipo: 'ok'; estado?: 'ACTIVO' | 'EN_REVISION' }
  | { tipo: 'errores'; errores: string[] }
  | { tipo: 'no-editable'; mensaje: string }
  | { tipo: 'sin-acceso' };

export const OBJETIVOS: { valor: string; texto: string }[] = [
  { valor: 'BAJAR_DE_PESO', texto: 'Bajar de peso' },
  { valor: 'FUERZA', texto: 'Ganar fuerza' },
  { valor: 'MUSCULACION', texto: 'Musculación' },
  { valor: 'SALUD', texto: 'Salud y bienestar' },
  { valor: 'RENDIMIENTO', texto: 'Rendimiento deportivo' },
  { valor: 'OTRO', texto: 'Otro' },
];

/** Nombre visible de cada campo, para mostrar los errores del backend ("dni: obligatorio") en castellano. */
export const NOMBRES_DE_CAMPOS: Record<string, string> = {
  dni: 'DNI',
  direccion: 'Dirección',
  emergenciaNombre: 'Contacto de emergencia',
  emergenciaTelefono: 'Teléfono de emergencia',
  objetivo: 'Objetivo',
  responsableNombre: 'Nombre del responsable',
  responsableDni: 'DNI del responsable',
  responsableTelefono: 'Teléfono del responsable',
  responsableEmail: 'Correo del responsable',
  responsableVinculo: 'Vínculo con el menor',
  documentos: 'Documentos',
  nombre: 'Nombre',
  apellido: 'Apellido',
  email: 'Correo',
  celular: 'Celular',
  fechaNacimiento: 'Fecha de nacimiento',
};

/** "dni: obligatorio" → "DNI: obligatorio". */
export function traducirError(error: string): string {
  const separador = error.indexOf(':');
  if (separador < 0) {
    return error;
  }
  const campo = error.slice(0, separador);
  return `${NOMBRES_DE_CAMPOS[campo] ?? campo}${error.slice(separador)}`;
}
