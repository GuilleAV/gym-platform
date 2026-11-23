import { VERSION } from '@angular/core';

/**
 * Test "guardián" de ADR-0001 (stack y versiones).
 *
 * ¿Para qué sirve un test sobre versiones? Si alguien actualiza Angular a otra
 * versión mayor sin pasar por un ADR nuevo, este test falla y obliga a hacerlo
 * a conciencia (leer la guía de migración, revisar breaking changes, etc.).
 */
describe('Versiones del stack (ADR-0001)', () => {
  it('usa Angular 22', () => {
    expect(VERSION.major).toBe('22');
  });
});
