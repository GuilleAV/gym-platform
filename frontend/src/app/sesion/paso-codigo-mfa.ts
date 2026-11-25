import { Component, inject, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { textoDeEspera } from './texto-de-espera';
import { SesionService } from './sesion.service';

/**
 * Segundo paso del ingreso: el código de 6 dígitos de la app, o un código de recuperación
 * si perdió el celular (ADR-0005).
 */
@Component({
  selector: 'app-paso-codigo-mfa',
  imports: [ReactiveFormsModule],
  template: `
    <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="enviar()">
      <h2>Verificación en dos pasos</h2>
      @if (recuperacion()) {
        <p class="secundario">
          Escribí uno de tus códigos de recuperación. Cada uno sirve una sola vez.
        </p>
        <label>
          Código de recuperación
          <input
            class="monoespaciado"
            [formControl]="codigo"
            autocomplete="off"
            autocapitalize="characters"
            placeholder="ABCDE-FGHJK"
          />
        </label>
      } @else {
        <p class="secundario">Abrí tu app de autenticación y escribí el código de 6 dígitos.</p>
        <label>
          Código
          <input
            class="monoespaciado"
            [formControl]="codigo"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="123456"
          />
        </label>
      }
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      <button type="submit" [disabled]="codigo.invalid || enviando()">Verificar</button>
      <button type="button" class="secundaria" (click)="alternarRecuperacion()">
        {{
          recuperacion()
            ? 'Usar el código de la app'
            : 'Perdí el celular: usar un código de recuperación'
        }}
      </button>
      <button type="button" class="secundaria" (click)="volver.emit()">Volver</button>
    </form>
  `,
})
export class PasoCodigoMfa {
  private readonly sesion = inject(SesionService);

  /** Volver a la pantalla de correo y contraseña. */
  readonly volver = output<void>();

  protected readonly recuperacion = signal(false);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly codigo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  /** El grupo es lo que hace que el <form> emita (ngSubmit) al apretar Enter o el botón. */
  protected readonly formulario = new FormGroup({ codigo: this.codigo });

  protected alternarRecuperacion(): void {
    this.recuperacion.update((valor) => !valor);
    this.codigo.reset();
    this.error.set('');
  }

  protected async enviar(): Promise<void> {
    if (this.codigo.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    try {
      const resultado = await this.sesion.verificarMfa(this.codigo.value.trim());
      if (resultado.tipo === 'invalido') {
        // El backend no distingue "código incorrecto" de "pasaron más de 5 minutos": lo decimos así.
        this.error.set(
          'Código incorrecto. Si pasaron más de 5 minutos desde que pusiste la contraseña, volvé a empezar.',
        );
        this.codigo.reset();
      } else if (resultado.tipo === 'bloqueado') {
        this.error.set(
          `Demasiados intentos fallidos. Probá de nuevo en ${textoDeEspera(resultado.segundos)}.`,
        );
      }
      // 'ok': el servicio ya cargó al usuario y la pantalla principal lo muestra.
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }
}
