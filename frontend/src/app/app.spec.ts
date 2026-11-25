import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { App, textoDeEspera } from './app';
import { GENERADOR_QR } from './sesion/generador-qr';
import { agruparDeACuatro } from './sesion/paso-configurar-mfa';

/** Deja correr las promesas pendientes (cada `await` del servicio) antes de responder la siguiente petición. */
const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

describe('App (pantalla del gimnasio)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        // jsdom no tiene canvas: el QR se reemplaza por una imagen falsa (por eso existe el token).
        { provide: GENERADOR_QR, useValue: async () => 'data:image/png;base64,QR-FALSO' },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    fixture.detectChanges(); // dispara ngOnInit → sesion.iniciar()
    await TestBed.inject(Router).navigateByUrl('/'); // la página de inicio va en el <router-outlet>
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

  it('el personal ve el acceso a recepción', async () => {
    await arrancar({
      email: 'r@correo.com',
      nombre: 'Rita',
      gimnasio: 'gym-norte',
      roles: ['RECEPCION'],
    });
    expect(texto()).toContain('Ir a recepción');
  });

  it('un alumno no ve el acceso a recepción', async () => {
    await arrancar({
      email: 'a@correo.com',
      nombre: 'Ana',
      gimnasio: 'gym-norte',
      roles: ['ALUMNO'],
    });
    expect(texto()).not.toContain('Ir a recepción');
  });

  it('con credenciales inválidas muestra un mensaje genérico', async () => {
    await arrancar(null);
    await aContrasenia();
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
    await aContrasenia();
    escribir('input[type=email]', 'ana@correo.com');
    escribir('input[type=password]', 'clave-equivocada');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();

    http.expectOne('/api/sesion/ingresar').flush(null, {
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
    await aContrasenia();
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

  /** Desde la pantalla del correo, va al ingreso con contraseña. */
  async function aContrasenia(): Promise<void> {
    boton('Prefiero usar contraseña').click();
    await refrescar();
  }

  function escribir(selector: string, valor: string): void {
    const input = elemento<HTMLInputElement>(selector);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  // --------------------------------------------------- ingreso por correo (ADR-0006)

  it('la primera pantalla pide solo el correo, sin contraseña', async () => {
    await arrancar(null);

    expect(texto()).toContain('No necesitás contraseña');
    expect(elemento('input[type=password]')).toBeNull();
  });

  /** Escribe el correo, toca Continuar y responde el 202 del backend. */
  async function pedirCodigo(email: string): Promise<void> {
    await arrancar(null);
    escribir('app-paso-correo input', email);
    elemento<HTMLFormElement>('app-paso-correo form').dispatchEvent(new Event('submit'));
    await esperar();
    const solicitud = http.expectOne('/api/sesion/codigo/solicitar');
    expect(solicitud.request.body).toEqual({ email });
    solicitud.flush({ estado: 'ok' }, { status: 202, statusText: 'Accepted' });
    await refrescar();
  }

  async function escribirCodigoDelCorreo(codigo: string): Promise<void> {
    escribir('app-paso-codigo-correo input', codigo);
    elemento<HTMLFormElement>('app-paso-codigo-correo form').dispatchEvent(new Event('submit'));
    await esperar();
  }

  it('con el código del correo entra sin contraseña', async () => {
    await pedirCodigo('ana@correo.com');
    expect(texto()).toContain('Revisá tu correo');
    expect(texto()).toContain('ana@correo.com');

    await escribirCodigoDelCorreo('482913');
    const ingreso = http.expectOne('/api/sesion/codigo/ingresar');
    expect(ingreso.request.body).toEqual({ email: 'ana@correo.com', codigo: '482913' });
    ingreso.flush(null, { status: 204, statusText: 'No Content' });
    await esperar();
    http.expectOne('/api/sesion/csrf').flush(null);
    await esperar();
    http
      .expectOne('/api/sesion/yo')
      .flush({ email: 'ana@correo.com', nombre: 'Ana', gimnasio: 'gym-norte' });
    await refrescar();

    expect(texto()).toContain('Hola, Ana');
  });

  it('con un código incorrecto lo dice y deja reintentar', async () => {
    await pedirCodigo('ana@correo.com');
    await escribirCodigoDelCorreo('000000');
    http
      .expectOne('/api/sesion/codigo/ingresar')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await refrescar();

    expect(texto()).toContain('no es correcto o ya venció');
    expect(elemento('app-paso-codigo-correo form')).toBeTruthy();
  });

  it('si el rol exige MFA, después del código del correo pide el de la app', async () => {
    await pedirCodigo('admin@correo.com');
    await escribirCodigoDelCorreo('482913');
    http.expectOne('/api/sesion/codigo/ingresar').flush({ paso: 'CODIGO_MFA' });
    await refrescar();

    expect(texto()).toContain('Verificación en dos pasos');
  });

  it('el botón de reenviar arranca deshabilitado (hay que esperar un minuto)', async () => {
    await pedirCodigo('ana@correo.com');

    expect(boton('Reenviar código').disabled).toBe(true);
    expect(texto()).toMatch(/Reenviar código en \d+ s/);
  });

  it('desde el código puede pasar a la contraseña con el correo ya escrito', async () => {
    await pedirCodigo('ana@correo.com');
    boton('Prefiero usar contraseña').click();
    await refrescar();

    expect(elemento<HTMLInputElement>('app-paso-clave input[type=email]').value).toBe(
      'ana@correo.com',
    );
    expect(elemento('input[type=password]')).toBeTruthy();
  });

  it('"Usar otro correo" vuelve a la primera pantalla', async () => {
    await pedirCodigo('ana@correo.com');
    boton('Usar otro correo').click();
    await refrescar();

    expect(elemento('app-paso-correo')).toBeTruthy();
  });

  it('la contraseña se puede mostrar para no equivocarse al tipear', async () => {
    await arrancar(null);
    await aContrasenia();
    expect(elemento('app-paso-clave input[type=password]')).toBeTruthy();

    boton('Mostrar contraseña').click();
    await refrescar();

    expect(elemento('app-paso-clave input[type=password]')).toBeNull();
    expect(texto()).toContain('Ocultar contraseña');
  });

  // ------------------------------------------------------------------ MFA (ADR-0005)

  const boton = (textoBoton: string) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
      (b.textContent ?? '').includes(textoBoton),
    ) as HTMLButtonElement;

  async function refrescar(): Promise<void> {
    await esperar();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** Pone correo y clave y responde al POST /ingresar con el paso de MFA indicado. */
  async function pasarLaClave(paso: 'CODIGO_MFA' | 'CONFIGURAR_MFA'): Promise<void> {
    await arrancar(null);
    await aContrasenia();
    escribir('input[type=email]', 'admin@correo.com');
    escribir('input[type=password]', 'clave-correcta');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();
    http.expectOne('/api/sesion/ingresar').flush({ paso });
    await refrescar();
  }

  /** Después del segundo factor: el servicio pide el nuevo CSRF y carga al usuario. */
  async function completarIngreso(): Promise<void> {
    await esperar();
    http.expectOne('/api/sesion/csrf').flush(null);
    await esperar();
    http
      .expectOne('/api/sesion/yo')
      .flush({ email: 'admin@correo.com', nombre: 'Admin', gimnasio: 'gym-norte' });
    await refrescar();
  }

  it('si el rol exige MFA pide el código y recién al verificarlo muestra al usuario', async () => {
    await pasarLaClave('CODIGO_MFA');
    expect(texto()).toContain('Verificación en dos pasos');
    expect(texto()).not.toContain('Hola');

    escribir('app-paso-codigo-mfa input', '123456');
    elemento<HTMLFormElement>('app-paso-codigo-mfa form').dispatchEvent(new Event('submit'));
    await esperar();
    const verificar = http.expectOne('/api/sesion/mfa/verificar');
    expect(verificar.request.body).toEqual({ codigo: '123456' });
    verificar.flush(null, { status: 204, statusText: 'No Content' });
    await completarIngreso();

    expect(texto()).toContain('Hola');
    expect(texto()).toContain('Admin');
  });

  it('un código incorrecto muestra el error y deja reintentar', async () => {
    await pasarLaClave('CODIGO_MFA');
    escribir('app-paso-codigo-mfa input', '000000');
    elemento<HTMLFormElement>('app-paso-codigo-mfa form').dispatchEvent(new Event('submit'));
    await esperar();
    http
      .expectOne('/api/sesion/mfa/verificar')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await refrescar();

    expect(texto()).toContain('Código incorrecto');
    expect(elemento('app-paso-codigo-mfa form')).toBeTruthy();
  });

  it('con demasiados códigos incorrectos muestra cuánto esperar', async () => {
    await pasarLaClave('CODIGO_MFA');
    escribir('app-paso-codigo-mfa input', '111111');
    elemento<HTMLFormElement>('app-paso-codigo-mfa form').dispatchEvent(new Event('submit'));
    await esperar();
    http.expectOne('/api/sesion/mfa/verificar').flush(null, {
      status: 429,
      statusText: 'Too Many Requests',
      headers: { 'Retry-After': '120' },
    });
    await refrescar();

    expect(texto()).toContain('Probá de nuevo en 2 minutos');
  });

  it('si perdió el celular puede usar un código de recuperación', async () => {
    await pasarLaClave('CODIGO_MFA');
    boton('código de recuperación').click();
    await refrescar();

    escribir('app-paso-codigo-mfa input', 'ABCDE-FGHJK');
    elemento<HTMLFormElement>('app-paso-codigo-mfa form').dispatchEvent(new Event('submit'));
    await esperar();
    const verificar = http.expectOne('/api/sesion/mfa/verificar');
    expect(verificar.request.body).toEqual({ codigo: 'ABCDE-FGHJK' });
    verificar.flush(null, { status: 204, statusText: 'No Content' });
    await completarIngreso();

    expect(texto()).toContain('Hola');
  });

  it('"Volver" desde el MFA regresa a la primera pantalla', async () => {
    await pasarLaClave('CODIGO_MFA');
    boton('Volver').click();
    await refrescar();

    expect(elemento('app-paso-correo')).toBeTruthy();
    expect(elemento('app-paso-codigo-mfa')).toBeNull();
  });

  it('la primera vez muestra el QR, confirma y muestra los códigos de recuperación antes de entrar', async () => {
    await pasarLaClave('CONFIGURAR_MFA');
    http
      .expectOne('/api/sesion/mfa/configurar')
      .flush({ secreto: 'JBSWY3DPEHPK3PXP', uri: 'otpauth://totp/Gym%20Norte:admin%40correo.com' });
    await refrescar();
    await refrescar(); // el QR se genera después de recibir la configuración

    expect(elemento<HTMLImageElement>('img.qr').src).toContain('QR-FALSO');
    expect(texto()).toContain('JBSW Y3DP EHPK 3PXP');

    escribir('app-paso-configurar-mfa input', '654321');
    elemento<HTMLFormElement>('app-paso-configurar-mfa form').dispatchEvent(new Event('submit'));
    await esperar();
    const codigos = ['AAAAA-BBBBB', 'CCCCC-DDDDD', 'EEEEE-FFFFF'];
    const confirmar = http.expectOne('/api/sesion/mfa/confirmar');
    expect(confirmar.request.body).toEqual({ codigo: '654321' });
    confirmar.flush({ codigosRecuperacion: codigos });
    await completarIngreso();

    // Primero los códigos (se muestran una sola vez), no la bienvenida:
    expect(texto()).toContain('Guardá tus códigos de recuperación');
    expect(texto()).toContain('CCCCC-DDDDD');
    expect(texto()).not.toContain('Hola');
    expect(boton('Continuar').disabled).toBe(true);

    elemento<HTMLInputElement>('input[type=checkbox]').click();
    await refrescar();
    boton('Continuar').click();
    await refrescar();

    expect(texto()).toContain('Hola');
    expect(texto()).not.toContain('CCCCC-DDDDD');
  });

  it('si el paso pendiente venció, al configurar pide volver a empezar', async () => {
    await pasarLaClave('CONFIGURAR_MFA');
    http
      .expectOne('/api/sesion/mfa/configurar')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await refrescar();

    expect(texto()).toContain('Volvé a empezar');
    expect(elemento('img.qr')).toBeNull();
  });
});

describe('agruparDeACuatro', () => {
  it('separa la clave en grupos de 4 para copiarla a mano', () => {
    expect(agruparDeACuatro('JBSWY3DPEHPK3PXP')).toBe('JBSW Y3DP EHPK 3PXP');
    expect(agruparDeACuatro('ABCDEF')).toBe('ABCD EF');
  });
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
