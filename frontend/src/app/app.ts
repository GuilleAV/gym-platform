import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SesionService } from './sesion/sesion.service';

export { textoDeEspera } from './sesion/texto-de-espera';

/**
 * El marco de la app: nombre y bienvenida del gimnasio y, debajo, la página de la ruta actual.
 * Es la misma app para todos los gimnasios: el contenido depende del dominio.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  protected readonly sesion = inject(SesionService);

  ngOnInit(): void {
    void this.sesion.iniciar();
  }
}
