/** 45 → "45 segundos"; 60 → "1 minuto"; 125 → "3 minutos" (siempre redondea hacia arriba). */
export function textoDeEspera(segundos: number): string {
  if (segundos < 60) {
    return segundos === 1 ? '1 segundo' : `${segundos} segundos`;
  }
  const minutos = Math.ceil(segundos / 60);
  return minutos === 1 ? '1 minuto' : `${minutos} minutos`;
}
