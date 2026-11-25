import { Component, OnInit, inject, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { GENERADOR_QR } from './generador-qr';
import { textoDeEspera } from './texto-de-espera';
import { SesionService } from './sesion.service';

/**
 * Primera vez con MFA: escanear el QR con la app y confirmar con el primer código (ADR-0005).
 * Al confirmar, el servicio guarda los códigos de recuperación y la pantalla principal los muestra.
 */
@Component({
  selector: 'app-paso-configurar-mfa',
  imports: [ReactiveFormsModule],
  styles: `
    ol {
      margin: 0;
      padding-left: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .qr {
      align-self: center;
      width: 220px;
      height: 220px;
      image-rendering: pixelated;
    }
    details summary {
      cursor: pointer;
      color: #1f5eff;
    }
    .clave {
      margin-top: 0.5rem;
      word-break: break-all;
    }
  `,
  template: `
    <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="confirmar()">
      <h2>Activá la verificación en dos pasos</h2>
      <p class="secundario">
        Tu rol exige un segundo factor para ingresar. Se configura una sola vez.
      </p>
      <ol>
        <li>
          Instalá una app de autenticación en tu celular (Google Authenticator, Microsoft
          Authenticator…).
        </li>
        <li>En la app, tocá "Agregar" y escaneá este código.</li>
        <li>Escribí abajo el código de 6 dígitos que te muestra la app.</li>
      </ol>

      @if (qr(); as imagen) {
        <img class="qr" [src]="imagen" alt="Código QR para configurar la app de autenticación" />
      } @else if (!error()) {
        <p class="secundario">Generando el código…</p>
      }
      @if (claveManual(); as clave) {
        <details>
          <summary>¿No podés escanear? Cargá la clave a mano</summary>
          <p class="clave monoespaciado">{{ clave }}</p>
        </details>
      }

      <label>
        Código de la app
        <input
          class="monoespaciado"
          [formControl]="codigo"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          placeholder="123456"
        />
      </label>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      <button type="submit" [disabled]="codigo.invalid || enviando() || !claveManual()">
        Activar
      </button>
      <button type="button" class="secundaria" (click)="volver.emit()">Volver</button>
    </form>
  `,
})
export class PasoConfigurarMfa implements OnInit {
  private readonly sesion = inject(SesionService);
  private readonly generarQr = inject(GENERADOR_QR);

  readonly volver = output<void>();

  protected readonly qr = signal<string | null>(null);
  protected readonly claveManual = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly codigo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
  });
  /** El grupo es lo que hace que el <form> emita (ngSubmit) al apretar Enter o el botón. */
  protected readonly formulario = new FormGroup({ codigo: this.codigo });

  async ngOnInit(): Promise<void> {
    try {
      const configuracion = await this.sesion.iniciarConfiguracionMfa();
      if (!configuracion) {
        this.error.set(
          'Pasaron más de 5 minutos desde que pusiste la contraseña. Volvé a empezar.',
        );
        return;
      }
      this.claveManual.set(agruparDeACuatro(configuracion.secreto));
      this.qr.set(await this.generarQr(configuracion.uri));
    } catch {
      this.error.set('No se pudo preparar la configuración. Probá de nuevo.');
    }
  }

  protected async confirmar(): Promise<void> {
    if (this.codigo.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    try {
      const resultado = await this.sesion.confirmarMfa(this.codigo.value);
      if (resultado.tipo === 'invalido') {
        this.error.set(
          'El código no coincide. Revisá que la hora del celular sea automática y probá con el siguiente.',
        );
        this.codigo.reset();
      } else if (resultado.tipo === 'bloqueado') {
        this.error.set(
          `Demasiados intentos fallidos. Probá de nuevo en ${textoDeEspera(resultado.segundos)}.`,
        );
      }
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }
}

/** "JBSWY3DPEHPK3PXP" → "JBSW Y3DP EHPK 3PXP": más fácil de copiar a mano sin equivocarse. */
export function agruparDeACuatro(texto: string): string {
  return texto.match(/.{1,4}/g)?.join(' ') ?? texto;
}
