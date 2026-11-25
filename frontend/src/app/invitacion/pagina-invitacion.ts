import { Location } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatosFicha, EnvioFicha, FichaAlumno, ResultadoFicha } from '../ficha/ficha.modelos';
import { FormularioFicha } from '../ficha/formulario-ficha';
import { FichaPorInvitacionService } from './ficha-por-invitacion.service';

type Estado = 'abriendo' | 'invalida' | 'completando' | 'activo' | 'en-revision' | 'error';

/**
 * /invitacion#t=TOKEN: la página que abre el alumno desde el correo (UC-05).
 *
 * El token viene en el FRAGMENTO (#t=...): el navegador nunca lo manda al servidor. Apenas se lee, se
 * borra de la barra de direcciones, para que no quede en el historial ni lo vea alguien por encima del hombro.
 */
@Component({
  selector: 'app-pagina-invitacion',
  imports: [FormularioFicha, RouterLink],
  template: `
    @switch (estado()) {
      @case ('abriendo') {
        <p class="secundario">Abriendo tu invitación…</p>
      }
      @case ('invalida') {
        <section class="tarjeta">
          <h2>La invitación no es válida o ya venció</h2>
          <p class="secundario">Pedile a la recepción del gimnasio que te la vuelva a enviar.</p>
        </section>
      }
      @case ('error') {
        <section class="tarjeta">
          <h2>No se pudo conectar con el servidor</h2>
          <p class="secundario">Probá abrir de nuevo el enlace del correo.</p>
        </section>
      }
      @case ('completando') {
        @if (ficha(); as f) {
          <app-formulario-ficha
            [ficha]="f"
            [errores]="errores()"
            [aviso]="aviso()"
            [ocupado]="ocupado()"
            (guardar)="guardar($event)"
            (enviar)="enviar($event)"
          />
        }
      }
      @case ('activo') {
        <section class="tarjeta">
          <h2>¡Listo, ya sos parte del gimnasio!</h2>
          <p class="secundario">
            Para entrar, poné tu correo y te mandamos un código. No necesitás contraseña.
          </p>
          <a routerLink="/">Ir a ingresar</a>
        </section>
      }
      @case ('en-revision') {
        <section class="tarjeta">
          <h2>Recibimos tu ficha</h2>
          <p class="secundario">
            El gimnasio la va a revisar y te avisamos por correo cuando esté aprobada.
          </p>
        </section>
      }
    }
  `,
})
export class PaginaInvitacion implements OnInit {
  private readonly ruta = inject(ActivatedRoute);
  private readonly ubicacion = inject(Location);
  private readonly servicio = inject(FichaPorInvitacionService);

  protected readonly estado = signal<Estado>('abriendo');
  protected readonly ficha = signal<FichaAlumno | null>(null);
  protected readonly errores = signal<string[]>([]);
  protected readonly aviso = signal('');
  protected readonly ocupado = signal(false);

  async ngOnInit(): Promise<void> {
    const token = new URLSearchParams(this.ruta.snapshot.fragment ?? '').get('t');
    this.ubicacion.replaceState('/invitacion'); // borra el #t=... de la barra y del historial
    if (!token) {
      this.estado.set('invalida');
      return;
    }
    try {
      const ficha = await this.servicio.abrir(token);
      this.ficha.set(ficha);
      this.estado.set(ficha ? 'completando' : 'invalida');
    } catch {
      this.estado.set('error');
    }
  }

  protected async guardar(datos: DatosFicha): Promise<void> {
    await this.ejecutar(
      () => this.servicio.guardar(datos),
      'Guardado. Podés volver con el mismo enlace del correo.',
    );
  }

  protected async enviar(envio: EnvioFicha): Promise<void> {
    await this.ejecutar(() => this.servicio.enviar(envio), '');
  }

  private async ejecutar(
    operacion: () => Promise<ResultadoFicha>,
    avisoSiOk: string,
  ): Promise<void> {
    this.ocupado.set(true);
    this.errores.set([]);
    this.aviso.set('');
    try {
      const r = await operacion();
      switch (r.tipo) {
        case 'ok':
          if (r.estado === 'ACTIVO') {
            this.estado.set('activo');
          } else if (r.estado === 'EN_REVISION') {
            this.estado.set('en-revision');
          } else {
            this.aviso.set(avisoSiOk);
          }
          break;
        case 'errores':
          this.errores.set(r.errores);
          break;
        case 'no-editable':
          this.errores.set([r.mensaje]);
          break;
        case 'sin-acceso':
          this.estado.set('invalida'); // venció la sesión de ficha (2 h): hay que abrir el enlace otra vez
          break;
      }
    } catch {
      this.errores.set(['No se pudo conectar con el servidor. Probá de nuevo.']);
    } finally {
      this.ocupado.set(false);
    }
  }
}
