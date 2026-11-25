import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { textoDeEspera } from './texto-de-espera';
import { ResultadoIngreso, SesionService } from './sesion.service';

/** Mismo tiempo mínimo entre envíos que el backend (gym.seguridad.codigo-correo.espera-entre-envios). */
export const ESPERA_REENVIO_SEGUNDOS = 60;

/**
 * Segunda pantalla del ingreso por correo: escribir el código de 6 dígitos que llegó (ADR-0006).
 */
@Component({
  selector: 'app-paso-codigo-correo',
  imports: [ReactiveFormsModule],
  template: `
    <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="ingresar()">
      <h2>Revisá tu correo</h2>
      <p class="secundario">
        Si <strong>{{ email() }}</strong> está registrado, te llegó un código de 6 dígitos. Vale por
        10 minutos.
      </p>
      <label>
        Código
        <input
          class="monoespaciado"
          formControlName="codigo"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          placeholder="123456"
        />
      </label>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      @if (aviso()) {
        <p class="secundario" role="status">{{ aviso() }}</p>
      }
      <button type="submit" [disabled]="formulario.invalid || enviando()">Ingresar</button>
      <button
        type="button"
        class="secundaria"
        [disabled]="esperaReenvio() > 0"
        (click)="reenviar()"
      >
        @if (esperaReenvio() > 0) {
          Reenviar código en {{ esperaReenvio() }} s
        } @else {
          Reenviar código
        }
      </button>
      <button type="button" class="secundaria" (click)="usarClave.emit()">
        Prefiero usar contraseña
      </button>
      <button type="button" class="secundaria" (click)="volver.emit()">Usar otro correo</button>
    </form>
  `,
})
export class PasoCodigoCorreo implements OnInit {
  private readonly sesion = inject(SesionService);
  private readonly destruccion = inject(DestroyRef);

  readonly email = input.required<string>();
  /** El código era correcto pero el rol exige MFA: falta el segundo factor (ADR-0006 §2). */
  readonly segundoFactor = output<'codigo-mfa' | 'configurar-mfa'>();
  readonly usarClave = output<void>();
  readonly volver = output<void>();

  protected readonly error = signal('');
  protected readonly aviso = signal('');
  protected readonly enviando = signal(false);
  protected readonly esperaReenvio = signal(ESPERA_REENVIO_SEGUNDOS);
  protected readonly formulario = new FormGroup({
    codigo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  ngOnInit(): void {
    this.iniciarCuentaRegresiva();
  }

  protected async ingresar(): Promise<void> {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    this.aviso.set('');
    try {
      const resultado: ResultadoIngreso = await this.sesion.ingresarConCodigo(
        this.email(),
        this.formulario.controls.codigo.value,
      );
      switch (resultado.tipo) {
        case 'invalido':
          this.error.set('El código no es correcto o ya venció. Revisalo o pedí uno nuevo.');
          this.formulario.reset();
          break;
        case 'bloqueado':
          this.error.set(
            `Demasiados intentos fallidos. Probá de nuevo en ${textoDeEspera(resultado.segundos)}.`,
          );
          break;
        case 'codigo-mfa':
        case 'configurar-mfa':
          this.segundoFactor.emit(resultado.tipo);
          break;
        case 'ok':
          break; // el servicio ya cargó al usuario: la pantalla principal lo muestra
      }
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }

  protected async reenviar(): Promise<void> {
    this.error.set('');
    try {
      await this.sesion.solicitarCodigo(this.email());
      this.aviso.set('Te mandamos un código nuevo. El anterior ya no sirve.');
      this.esperaReenvio.set(ESPERA_REENVIO_SEGUNDOS);
      this.iniciarCuentaRegresiva();
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    }
  }

  /** Cuenta de a un segundo hasta 0. Se limpia sola si el componente se destruye (DestroyRef). */
  private iniciarCuentaRegresiva(): void {
    const intervalo = setInterval(() => {
      this.esperaReenvio.update((s) => Math.max(0, s - 1));
      if (this.esperaReenvio() === 0) {
        clearInterval(intervalo);
      }
    }, 1000);
    this.destruccion.onDestroy(() => clearInterval(intervalo));
  }
}
