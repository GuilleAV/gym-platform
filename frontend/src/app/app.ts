import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { SesionService } from './sesion/sesion.service';

/**
 * Pantalla inicial del gimnasio: nombre, bienvenida e inicio de sesión.
 * Es la misma app para todos los gimnasios: el contenido depende del dominio.
 */
@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  protected readonly sesion = inject(SesionService);
  protected readonly error = signal('');
  protected readonly enviando = signal(false);

  protected readonly formulario = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    void this.sesion.iniciar();
  }

  protected async ingresar(): Promise<void> {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    const { email, clave } = this.formulario.getRawValue();
    try {
      const resultado = await this.sesion.ingresar(email, clave);
      switch (resultado.tipo) {
        case 'ok':
          this.formulario.reset();
          break;
        case 'invalido':
          this.error.set('Correo o contraseña incorrectos');
          break;
        case 'bloqueado':
          this.error.set(
            `Demasiados intentos fallidos. Probá de nuevo en ${textoDeEspera(resultado.segundos)}.`,
          );
          break;
      }
    } catch {
      this.error.set('No se pudo conectar con el servidor. Probá de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }

  protected async salir(): Promise<void> {
    await this.sesion.salir();
  }
}

/** 45 → "45 segundos"; 60 → "1 minuto"; 125 → "3 minutos" (siempre redondea hacia arriba). */
export function textoDeEspera(segundos: number): string {
  if (segundos < 60) {
    return segundos === 1 ? '1 segundo' : `${segundos} segundos`;
  }
  const minutos = Math.ceil(segundos / 60);
  return minutos === 1 ? '1 minuto' : `${minutos} minutos`;
}
