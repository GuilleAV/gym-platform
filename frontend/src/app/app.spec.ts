import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App, textoDeEspera } from './app';

/** Deja correr las promesas pendientes (cada `await` del servicio) antes de responder la siguiente petición. */
const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

describe('App (pantalla del gimnasio)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    fixture.detectChanges(); // dispara ngOnInit → sesion.iniciar()
  });

  afterEach(() => http.verify()); // ninguna petición quedó sin responder ni hubo peticiones de más

  /** Responde en orden las peticiones de arranque. */
  async function arrancar(usuario: object | null): Promise<void> {
    http
      .expectOne('/api/gimnasio')
      .flush({ slug: 'gym-norte', nombre: 'Gym Norte', zonaHoraria: 'UTC' });
    await esperar();
    http.expectOne('/api/gimnasio/bienvenida').flush({ mensaje: '¡Bienvenido a Gym Norte!' });
    await esperar();
    http.expectOne('/api/sesion/csrf').flush(null);
    await esperar();
    const yo = http.expectOne('/api/sesion/yo');
    if (usuario) {
      yo.flush(usuario);
    } else {
      yo.flush(null, { status: 401, statusText: 'Unauthorized' });
    }
    await esperar();
    await fixture.whenStable();
  }

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const elemento = <T extends Element>(selector: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(selector) as T;

  it('muestra el gimnasio del dominio, su bienvenida y el formulario de ingreso', async () => {
    await arrancar(null);

    expect(texto()).toContain('Gym Norte');
    expect(texto()).toContain('¡Bienvenido a Gym Norte!');
    expect(elemento('form')).toBeTruthy();
  });

  it('si el dominio no es de ningún gimnasio, lo dice y no pide nada más', async () => {
    http.expectOne('/api/gimnasio').flush(null, { status: 404, statusText: 'Not Found' });
    await esperar();
    await fixture.whenStable();

    expect(texto()).toContain('Gimnasio no encontrado');
  });

  it('con una sesión abierta muestra al usuario en vez del formulario', async () => {
    await arrancar({ email: 'ana@correo.com', nombre: 'Ana', gimnasio: 'gym-norte' });

    expect(texto()).toContain('Ana');
    expect(elemento('form')).toBeNull();
  });

  it('con credenciales inválidas muestra un mensaje genérico', async () => {
    await arrancar(null);
    escribir('input[type=email]', 'ana@correo.com');
    escribir('input[type=password]', 'clave-equivocada');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();

    const pedido = http.expectOne('/api/sesion/ingresar');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ email: 'ana@correo.com', clave: 'clave-equivocada' });
    pedido.flush(null, { status: 401, statusText: 'Unauthorized' });
    await esperar();
    await fixture.whenStable();

    expect(texto()).toContain('Correo o contraseña incorrectos');
  });

  it('si hay demasiados intentos, muestra cuánto esperar (Retry-After)', async () => {
    await arrancar(null);
    escribir('input[type=email]', 'ana@correo.com');
    escribir('input[type=password]', 'clave-equivocada');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();

    http
      .expectOne('/api/sesion/ingresar')
      .flush(null, {
        status: 429,
        statusText: 'Too Many Requests',
        headers: { 'Retry-After': '90' },
      });
    await esperar();
    await fixture.whenStable();

    expect(texto()).toContain('Demasiados intentos fallidos. Probá de nuevo en 2 minutos.');
    expect(texto()).not.toContain('No se pudo conectar');
  });

  it('al ingresar bien, renueva el token CSRF y muestra al usuario', async () => {
    await arrancar(null);
    escribir('input[type=email]', 'ana@correo.com');
    escribir('input[type=password]', 'clave-correcta-123');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();

    http.expectOne('/api/sesion/ingresar').flush(null, { status: 204, statusText: 'No Content' });
    await esperar();
    http.expectOne('/api/sesion/csrf').flush(null);
    await esperar();
    http
      .expectOne('/api/sesion/yo')
      .flush({ email: 'ana@correo.com', nombre: 'Ana', gimnasio: 'gym-norte' });
    await esperar();
    await fixture.whenStable();

    expect(texto()).toContain('Hola, Ana');
  });

  function escribir(selector: string, valor: string): void {
    const input = elemento<HTMLInputElement>(selector);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }
});

describe('textoDeEspera', () => {
  it('redondea hacia arriba y usa singular o plural', () => {
    expect(textoDeEspera(1)).toBe('1 segundo');
    expect(textoDeEspera(45)).toBe('45 segundos');
    expect(textoDeEspera(60)).toBe('1 minuto');
    expect(textoDeEspera(61)).toBe('2 minutos');
    expect(textoDeEspera(900)).toBe('15 minutos');
  });
});
