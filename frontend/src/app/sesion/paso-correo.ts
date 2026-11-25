import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { SesionService } from './sesion.service';

/**
 * Primera pantalla del ingreso (ADR-0006): solo el correo. Lo más simple para la mayoría:
 * nadie tiene que acordarse de una contraseña.
 */
@Component({
  selector: 'app-paso-correo',
  imports: [ReactiveFormsModule],
  template: `
    <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="continuar()">
      <h2>Ingresar</h2>
      <p class="secundario">Te mandamos un código a tu correo. No necesitás contraseña.</p>
      <label>
        Correo
        <input type="email" formControlName="email" autocomplete="username" inputmode="email" />
      </label>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      <button type="submit" [disabled]="formulario.invalid || enviando()">Continuar</button>
      <button
        type="button"
        class="secundaria"
        (click)="usarClave.emit(formulario.controls.email.value)"
      >
        Prefiero usar contraseña
      </button>
    </form>
  `,
})
export class PasoCorreo implements OnInit {
  private readonly sesion = inject(SesionService);

  /** Correo con el que arranca el campo (por ejemplo, al volver de otra pantalla). */
  readonly emailInicial = input('');
  /** Se pidió el código: pasar a la pantalla para escribirlo. */
  readonly codigoEnviado = output<string>();
  readonly usarClave = output<string>();

  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly formulario = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  ngOnInit(): void {
    this.formulario.controls.email.setValue(this.emailInicial());
  }

  protected async continuar(): Promise<void> {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    const email = this.formulario.controls.email.value.trim();
    try {
      if (await this.sesion.solicitarCodigo(email)) {
        this.codigoEnviado.emit(email);
      } else {
        this.error.set('Revisá el correo: no parece válido.');
      }
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }
}
