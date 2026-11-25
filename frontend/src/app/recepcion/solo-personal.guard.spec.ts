import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { SesionService } from '../sesion/sesion.service';
import { soloPersonal } from './solo-personal.guard';

describe('soloPersonal (guard de /recepcion)', () => {
  function evaluar(esPersonal: boolean): Promise<unknown> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: SesionService,
          useValue: { esperarCarga: async () => undefined, esPersonal: () => esPersonal },
        },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      soloPersonal({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Promise<unknown>;
  }

  it('deja pasar al personal', async () => {
    expect(await evaluar(true)).toBe(true);
  });

  it('a cualquier otro lo manda al inicio', async () => {
    const resultado = (await evaluar(false)) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(resultado)).toBe('/');
  });
});
