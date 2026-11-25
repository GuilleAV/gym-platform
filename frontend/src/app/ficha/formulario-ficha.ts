import { Component, OnInit, computed, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DatosFicha, EnvioFicha, FichaAlumno, OBJETIVOS, traducirError } from './ficha.modelos';

/**
 * El formulario de la ficha del alumno. NO sabe a qué endpoint se guarda: avisa con `guardar` y `enviar`,
 * y el padre decide (la invitación usa /api/ficha; el mostrador, /api/inscripciones/{id}/ficha).
 * Así el mismo formulario sirve para los dos casos sin duplicar código.
 */
@Component({
  selector: 'app-formulario-ficha',
  imports: [ReactiveFormsModule],
  styles: `
    fieldset {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      border: 0;
    }
    legend {
      margin-bottom: 0.5rem;
      font-weight: 600;
    }
    select {
      padding: 0.625rem 0.75rem;
      border: 1px solid #c9ccd3;
      border-radius: 0.5rem;
      font: inherit;
      background: #fff;
    }
    .documento {
      padding: 0.75rem;
      border-radius: 0.5rem;
      background: #f4f5f7;
    }
    .documento p {
      margin: 0.5rem 0;
      font-size: 0.875rem;
      white-space: pre-line;
    }
    .acepto {
      flex-direction: row;
      align-items: center;
      gap: 0.5rem;
    }
    .errores {
      margin: 0;
      padding-left: 1.25rem;
    }
    .acciones {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }
  `,
  template: `
    <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="alEnviar()">
      <h2>Ficha de {{ ficha().nombre }} {{ ficha().apellido }}</h2>
      @if (ficha().email) {
        <p class="secundario">{{ ficha().email }}</p>
      }

      <fieldset>
        <legend>Tus datos</legend>
        <label>DNI <input formControlName="dni" inputmode="numeric" autocomplete="off" /></label>
        <label>Dirección <input formControlName="direccion" autocomplete="street-address" /></label>
        <label>
          Objetivo
          <select formControlName="objetivo">
            <option [ngValue]="null">Elegí uno…</option>
            @for (o of objetivos; track o.valor) {
              <option [ngValue]="o.valor">{{ o.texto }}</option>
            }
          </select>
        </label>
      </fieldset>

      <fieldset>
        <legend>Contacto de emergencia</legend>
        <label>Nombre <input formControlName="emergenciaNombre" /></label>
        <label>Teléfono <input formControlName="emergenciaTelefono" inputmode="tel" /></label>
      </fieldset>

      @if (ficha().esMenor) {
        <fieldset>
          <legend>Padre, madre o tutor</legend>
          <p class="secundario">
            Es menor de edad: el responsable completa estos datos y acepta los documentos.
          </p>
          <label>Nombre y apellido <input formControlName="responsableNombre" /></label>
          <label>DNI <input formControlName="responsableDni" inputmode="numeric" /></label>
          <label>Teléfono <input formControlName="responsableTelefono" inputmode="tel" /></label>
          <label>Correo (opcional) <input type="email" formControlName="responsableEmail" /></label>
          <label>
            Vínculo
            <select formControlName="responsableVinculo">
              <option [ngValue]="null">Elegí uno…</option>
              <option ngValue="MADRE">Madre</option>
              <option ngValue="PADRE">Padre</option>
              <option ngValue="TUTOR">Tutor</option>
            </select>
          </label>
        </fieldset>
      }

      <fieldset>
        <legend>Documentos</legend>
        @for (doc of ficha().documentos; track doc.id) {
          <div class="documento">
            <details>
              <summary>{{ doc.titulo }}</summary>
              <p>{{ doc.texto }}</p>
            </details>
            <label class="acepto">
              <input
                type="checkbox"
                [checked]="aceptados().has(doc.codigo)"
                (change)="alternar(doc.codigo)"
              />
              Leí y acepto
            </label>
          </div>
        }
      </fieldset>

      @if (errores().length > 0) {
        <ul class="errores error" role="alert">
          @for (e of errores(); track e) {
            <li>{{ traducir(e) }}</li>
          }
        </ul>
      }
      @if (aviso()) {
        <p class="secundario" role="status">{{ aviso() }}</p>
      }

      <div class="acciones">
        <button type="submit" [disabled]="!todosAceptados() || ocupado()">Enviar ficha</button>
        <button
          type="button"
          class="secundaria"
          [disabled]="ocupado()"
          (click)="guardar.emit(datos())"
        >
          Guardar y seguir después
        </button>
      </div>
    </form>
  `,
})
export class FormularioFicha implements OnInit {
  readonly ficha = input.required<FichaAlumno>();
  /** Errores del backend para mostrar (ej. "dni: obligatorio"). */
  readonly errores = input<string[]>([]);
  readonly aviso = input('');
  readonly ocupado = input(false);
  readonly guardar = output<DatosFicha>();
  readonly enviar = output<EnvioFicha>();

  protected readonly objetivos = OBJETIVOS;
  protected readonly traducir = traducirError;
  protected readonly aceptados = signal(new Set<string>());
  protected readonly todosAceptados = computed(() =>
    this.ficha().documentos.every((d) => this.aceptados().has(d.codigo)),
  );

  protected readonly formulario = new FormGroup({
    dni: new FormControl<string | null>(null),
    direccion: new FormControl<string | null>(null),
    emergenciaNombre: new FormControl<string | null>(null),
    emergenciaTelefono: new FormControl<string | null>(null),
    objetivo: new FormControl<string | null>(null),
    responsableNombre: new FormControl<string | null>(null),
    responsableDni: new FormControl<string | null>(null),
    responsableTelefono: new FormControl<string | null>(null),
    responsableEmail: new FormControl<string | null>(null),
    responsableVinculo: new FormControl<string | null>(null),
  });

  ngOnInit(): void {
    this.formulario.patchValue(this.ficha().datos); // lo guardado en un borrador anterior
  }

  protected alternar(codigo: string): void {
    this.aceptados.update((actual) => {
      const nuevo = new Set(actual); // signal: un objeto nuevo para que Angular detecte el cambio
      if (nuevo.has(codigo)) {
        nuevo.delete(codigo);
      } else {
        nuevo.add(codigo);
      }
      return nuevo;
    });
  }

  protected alEnviar(): void {
    if (!this.todosAceptados() || this.ocupado()) {
      return;
    }
    this.enviar.emit({
      datos: this.datos(),
      // Se manda la VERSIÓN que se leyó: si cambió mientras tanto, el backend lo rechaza.
      documentos: this.ficha().documentos.map((d) => ({ codigo: d.codigo, version: d.version })),
    });
  }

  private datos(): DatosFicha {
    return this.formulario.getRawValue() as DatosFicha;
  }
}
