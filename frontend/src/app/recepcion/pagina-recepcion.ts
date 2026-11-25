import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DatosFicha,
  EnvioFicha,
  FichaAlumno,
  ResultadoFicha,
  traducirError,
} from '../ficha/ficha.modelos';
import { FormularioFicha } from '../ficha/formulario-ficha';
import { InscripcionesService, ResumenInscripcion } from './inscripciones.service';

/**
 * /recepcion: lo que hace el personal en el mostrador (ADR-0007). Reemplaza la planilla de papel:
 * inscribir, ver quién tiene la ficha pendiente, reenviar invitaciones y completar fichas.
 */
@Component({
  selector: 'app-pagina-recepcion',
  imports: [ReactiveFormsModule, RouterLink, FormularioFicha],
  styles: `
    .fila {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 0;
      border-bottom: 1px solid #e6e8ec;
    }
    .fila:last-child {
      border-bottom: 0;
    }
    .acciones {
      display: flex;
      gap: 1rem;
    }
    ul {
      margin: 0;
      padding-left: 1.25rem;
    }
  `,
  template: `
    <p><a routerLink="/">← Volver al inicio</a></p>

    @if (fichaAbierta(); as ficha) {
      <app-formulario-ficha
        [ficha]="ficha"
        [errores]="erroresFicha()"
        [aviso]="avisoFicha()"
        [ocupado]="ocupado()"
        (guardar)="guardarFicha(ficha.inscripcionId, $event)"
        (enviar)="enviarFicha(ficha.inscripcionId, $event)"
      />
      <p>
        <button type="button" class="secundaria" (click)="cerrarFicha()">
          Cerrar la ficha sin enviar
        </button>
      </p>
    } @else {
      <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="inscribir()">
        <h2>Nueva inscripción</h2>
        <label>Nombre <input formControlName="nombre" autocomplete="off" /></label>
        <label>Apellido <input formControlName="apellido" autocomplete="off" /></label>
        <label>
          Correo (opcional)
          <input type="email" formControlName="email" autocomplete="off" />
        </label>
        <label
          >Celular <input formControlName="celular" inputmode="tel" autocomplete="off"
        /></label>
        <label>Fecha de nacimiento <input type="date" formControlName="fechaNacimiento" /></label>
        @if (erroresAlta().length > 0) {
          <ul class="error" role="alert">
            @for (e of erroresAlta(); track e) {
              <li>{{ traducir(e) }}</li>
            }
          </ul>
        }
        @if (resultadoAlta()) {
          <p role="status">{{ resultadoAlta() }}</p>
        }
        <button type="submit" [disabled]="formulario.invalid || ocupado()">Inscribir</button>
      </form>

      <section class="tarjeta">
        <h2>Fichas pendientes</h2>
        @if (aviso()) {
          <p role="status">{{ aviso() }}</p>
        }
        @for (p of pendientes(); track p.id) {
          <div class="fila">
            <div>
              <strong>{{ p.apellido }}, {{ p.nombre }}</strong>
              <div class="secundario">{{ p.email ?? 'Sin correo: completar en el mostrador' }}</div>
            </div>
            <div class="acciones">
              @if (p.email) {
                <button type="button" class="secundaria" (click)="reenviar(p)">
                  Reenviar invitación
                </button>
              }
              <button type="button" class="secundaria" (click)="abrirFicha(p.id)">
                Completar ficha
              </button>
            </div>
          </div>
        } @empty {
          <p class="secundario">No hay fichas pendientes.</p>
        }
      </section>
    }
  `,
})
export class PaginaRecepcion implements OnInit {
  private readonly inscripciones = inject(InscripcionesService);

  protected readonly traducir = traducirError;
  protected readonly ocupado = signal(false);
  protected readonly pendientes = signal<ResumenInscripcion[]>([]);
  protected readonly aviso = signal('');
  protected readonly erroresAlta = signal<string[]>([]);
  protected readonly resultadoAlta = signal('');
  protected readonly fichaAbierta = signal<FichaAlumno | null>(null);
  protected readonly erroresFicha = signal<string[]>([]);
  protected readonly avisoFicha = signal('');

  protected readonly formulario = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    apellido: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    celular: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    fechaNacimiento: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  async ngOnInit(): Promise<void> {
    await this.cargarPendientes();
  }

  protected async inscribir(): Promise<void> {
    if (this.formulario.invalid || this.ocupado()) {
      return;
    }
    this.ocupado.set(true);
    this.erroresAlta.set([]);
    this.resultadoAlta.set('');
    const v = this.formulario.getRawValue();
    try {
      const r = await this.inscripciones.inscribir({ ...v, email: v.email.trim() || null });
      if (r.tipo === 'ok') {
        const quien = `${v.nombre} ${v.apellido}`;
        const menor = r.inscripcion.esMenor
          ? ' Es menor: la ficha va a pedir los datos del responsable.'
          : '';
        this.resultadoAlta.set(
          r.inscripcion.invitacionEnviada
            ? `Listo: le mandamos a ${quien} la invitación para completar su ficha.${menor}`
            : `${quien} quedó inscripto sin correo: completá su ficha en la lista de abajo.${menor}`,
        );
        this.formulario.reset();
        await this.cargarPendientes();
      } else if (r.tipo === 'errores') {
        this.erroresAlta.set(r.errores);
      } else {
        this.erroresAlta.set([r.mensaje]);
      }
    } catch {
      this.erroresAlta.set(['No se pudo conectar con el servidor. Probá de nuevo.']);
    } finally {
      this.ocupado.set(false);
    }
  }

  protected async reenviar(p: ResumenInscripcion): Promise<void> {
    this.aviso.set('');
    try {
      const motivo = await this.inscripciones.reenviarInvitacion(p.id);
      this.aviso.set(motivo ?? `Le reenviamos la invitación a ${p.nombre} ${p.apellido}.`);
    } catch {
      this.aviso.set('No se pudo conectar con el servidor. Probá de nuevo.');
    }
  }

  protected async abrirFicha(id: number): Promise<void> {
    this.erroresFicha.set([]);
    this.avisoFicha.set('');
    try {
      this.fichaAbierta.set(await this.inscripciones.ficha(id));
    } catch {
      this.aviso.set('No se pudo abrir la ficha. Probá de nuevo.');
    }
  }

  protected cerrarFicha(): void {
    this.fichaAbierta.set(null);
  }

  protected async guardarFicha(id: number, datos: DatosFicha): Promise<void> {
    await this.ejecutarFicha(() => this.inscripciones.guardarFicha(id, datos), 'Guardado.');
  }

  protected async enviarFicha(id: number, envio: EnvioFicha): Promise<void> {
    await this.ejecutarFicha(() => this.inscripciones.enviarFicha(id, envio), '');
  }

  private async ejecutarFicha(
    operacion: () => Promise<ResultadoFicha>,
    avisoSiOk: string,
  ): Promise<void> {
    this.ocupado.set(true);
    this.erroresFicha.set([]);
    this.avisoFicha.set('');
    try {
      const r = await operacion();
      if (r.tipo === 'ok' && r.estado) {
        const ficha = this.fichaAbierta();
        this.fichaAbierta.set(null);
        this.aviso.set(
          r.estado === 'ACTIVO'
            ? `Ficha de ${ficha?.nombre} ${ficha?.apellido} completa: ya es alumno activo.`
            : `Ficha de ${ficha?.nombre} ${ficha?.apellido} enviada a revisión.`,
        );
        await this.cargarPendientes();
      } else if (r.tipo === 'ok') {
        this.avisoFicha.set(avisoSiOk);
      } else if (r.tipo === 'errores') {
        this.erroresFicha.set(r.errores);
      } else if (r.tipo === 'no-editable') {
        this.erroresFicha.set([r.mensaje]);
      } else {
        this.erroresFicha.set(['No tenés permiso para esta ficha.']);
      }
    } catch {
      this.erroresFicha.set(['No se pudo conectar con el servidor. Probá de nuevo.']);
    } finally {
      this.ocupado.set(false);
    }
  }

  private async cargarPendientes(): Promise<void> {
    try {
      this.pendientes.set(await this.inscripciones.pendientes());
    } catch {
      this.aviso.set('No se pudo cargar la lista. Recargá la página.');
    }
  }
}
