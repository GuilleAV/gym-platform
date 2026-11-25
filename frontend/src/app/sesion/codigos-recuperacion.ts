import { Component, input, output, signal } from '@angular/core';

/**
 * Los 10 códigos de recuperación, que el servidor muestra UNA sola vez. No se puede seguir sin
 * confirmar que se guardaron: si se pierde el celular sin ellos, solo queda llamar al soporte.
 */
@Component({
  selector: 'app-codigos-recuperacion',
  styles: `
    ul {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 0.5rem 1rem;
      margin: 0;
      padding: 0.75rem 1rem;
      list-style: none;
      border-radius: 0.5rem;
      background: #f4f5f7;
    }
    .acciones {
      display: flex;
      gap: 1rem;
    }
    .confirmacion {
      flex-direction: row;
      align-items: center;
      gap: 0.5rem;
      font-weight: 400;
    }
  `,
  template: `
    <section class="tarjeta">
      <h2>Guardá tus códigos de recuperación</h2>
      <p class="secundario">
        Si perdés el celular, vas a poder entrar con uno de estos códigos. Cada uno sirve una sola
        vez.
        <strong>No se van a volver a mostrar.</strong>
      </p>
      <ul class="monoespaciado">
        @for (codigo of codigos(); track codigo) {
          <li>{{ codigo }}</li>
        }
      </ul>
      <div class="acciones">
        <button type="button" class="secundaria" (click)="copiar()">
          {{ copiado() ? '¡Copiados!' : 'Copiar' }}
        </button>
        <button type="button" class="secundaria" (click)="descargar()">Descargar .txt</button>
      </div>
      <label class="confirmacion">
        <input type="checkbox" [checked]="guardados()" (change)="guardados.set(!guardados())" />
        Ya los guardé en un lugar seguro
      </label>
      <button type="button" [disabled]="!guardados()" (click)="listo.emit()">Continuar</button>
    </section>
  `,
})
export class CodigosRecuperacion {
  readonly codigos = input.required<string[]>();
  readonly listo = output<void>();

  protected readonly guardados = signal(false);
  protected readonly copiado = signal(false);

  protected async copiar(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.codigos().join('\n'));
      this.copiado.set(true);
    } catch {
      this.copiado.set(false); // sin permiso de portapapeles: queda la descarga o copiarlos a mano
    }
  }

  protected descargar(): void {
    const contenido = `Códigos de recuperación\n\n${this.codigos().join('\n')}\n\nCada código sirve una sola vez.\n`;
    const url = URL.createObjectURL(new Blob([contenido], { type: 'text/plain;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = 'codigos-de-recuperacion.txt';
    enlace.click();
    URL.revokeObjectURL(url);
  }
}
