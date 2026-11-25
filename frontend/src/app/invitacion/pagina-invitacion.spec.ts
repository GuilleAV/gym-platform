import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { FichaAlumno } from '../ficha/ficha.modelos';
import { PaginaInvitacion } from './pagina-invitacion';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

function ficha(esMenor = false): FichaAlumno {
  return {
    inscripcionId: 7,
    nombre: 'Ana',
    apellido: 'Pérez',
    email: 'ana@correo.com',
    fechaNacimiento: '1990-05-10',
    esMenor,
    estado: 'FICHA_PENDIENTE',
    datos: {
      dni: '30123456',
      direccion: null,
      emergenciaNombre: null,
      emergenciaTelefono: null,
      objetivo: null,
      responsableNombre: null,
      responsableDni: null,
      responsableTelefono: null,
      responsableEmail: null,
      responsableVinculo: null,
    },
    documentos: [
      {
        id: 1,
        codigo: 'DESLINDE',
        version: 1,
        titulo: 'Deslinde',
        texto: 'Texto A',
        soloMenores: false,
      },
      {
        id: 2,
        codigo: 'PRIVACIDAD',
        version: 1,
        titulo: 'Privacidad',
        texto: 'Texto B',
        soloMenores: false,
      },
    ],
  };
}

describe('PaginaInvitacion (/invitacion#t=...)', () => {
  let fixture: ComponentFixture<PaginaInvitacion>;
  let http: HttpTestingController;

  async function abrirCon(fragmento: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PaginaInvitacion],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { fragment: fragmento } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(Location).go('/invitacion#' + (fragmento ?? ''));
    fixture = TestBed.createComponent(PaginaInvitacion);
    fixture.detectChanges();
    await esperar();
  }

  afterEach(() => http.verify());

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const elemento = <T extends Element>(s: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(s) as T;
  const boton = (t: string) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
      (b.textContent ?? '').includes(t),
    ) as HTMLButtonElement;

  async function refrescar(): Promise<void> {
    await esperar();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function escribir(selector: string, valor: string): void {
    const input = elemento<HTMLInputElement>(selector);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  function aceptarTodo(): void {
    (fixture.nativeElement as HTMLElement)
      .querySelectorAll<HTMLInputElement>('input[type=checkbox]')
      .forEach((c) => c.click());
  }

  it('abre la invitación con el token del fragmento y lo borra de la barra de direcciones', async () => {
    await abrirCon('t=TOKEN-DE-PRUEBA');

    const pedido = http.expectOne('/api/invitacion/abrir');
    expect(pedido.request.body).toEqual({ token: 'TOKEN-DE-PRUEBA' });
    expect(TestBed.inject(Location).path(true)).toBe('/invitacion');
    pedido.flush(ficha());
    await refrescar();

    expect(texto()).toContain('Ficha de Ana Pérez');
    expect(elemento<HTMLInputElement>('input[formcontrolname=dni]').value).toBe('30123456'); // del borrador
  });

  it('sin token o con una invitación que no sirve lo dice', async () => {
    await abrirCon(null);
    await refrescar();
    expect(texto()).toContain('no es válida o ya venció');
  });

  it('si el backend dice que la invitación no sirve, lo explica', async () => {
    await abrirCon('t=VIEJO');
    http
      .expectOne('/api/invitacion/abrir')
      .flush({ error: 'x' }, { status: 404, statusText: 'Not Found' });
    await refrescar();
    expect(texto()).toContain('Pedile a la recepción');
  });

  it('no deja enviar hasta aceptar todos los documentos y al enviar en modo simple da la bienvenida', async () => {
    await abrirCon('t=TOKEN');
    http.expectOne('/api/invitacion/abrir').flush(ficha());
    await refrescar();
    expect(boton('Enviar ficha').disabled).toBe(true);

    aceptarTodo();
    await refrescar();
    expect(boton('Enviar ficha').disabled).toBe(false);
    escribir('input[formcontrolname=direccion]', 'Av. Siempre Viva 742');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();

    const envio = http.expectOne('/api/ficha/enviar');
    expect(envio.request.body.datos.direccion).toBe('Av. Siempre Viva 742');
    expect(envio.request.body.documentos).toEqual([
      { codigo: 'DESLINDE', version: 1 },
      { codigo: 'PRIVACIDAD', version: 1 },
    ]);
    envio.flush({ estado: 'ACTIVO' });
    await refrescar();

    expect(texto()).toContain('ya sos parte del gimnasio');
  });

  it('muestra los errores del backend con el nombre de cada campo', async () => {
    await abrirCon('t=TOKEN');
    http.expectOne('/api/invitacion/abrir').flush(ficha());
    await refrescar();
    aceptarTodo();
    await refrescar();
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();
    http
      .expectOne('/api/ficha/enviar')
      .flush(
        { errores: ['emergenciaTelefono: obligatorio'] },
        { status: 400, statusText: 'Bad Request' },
      );
    await refrescar();

    expect(texto()).toContain('Teléfono de emergencia: obligatorio');
  });

  it('guardar un borrador avisa que se puede volver con el mismo enlace', async () => {
    await abrirCon('t=TOKEN');
    http.expectOne('/api/invitacion/abrir').flush(ficha());
    await refrescar();

    boton('Guardar y seguir después').click();
    await esperar();
    http.expectOne('/api/ficha').flush(null, { status: 204, statusText: 'No Content' });
    await refrescar();

    expect(texto()).toContain('Podés volver con el mismo enlace');
  });

  it('para un menor pide los datos del responsable', async () => {
    await abrirCon('t=TOKEN');
    http.expectOne('/api/invitacion/abrir').flush(ficha(true));
    await refrescar();

    expect(texto()).toContain('Padre, madre o tutor');
    expect(elemento('input[formcontrolname=responsableDni]')).toBeTruthy();
  });

  it('en modo completo avisa que la ficha queda en revisión', async () => {
    await abrirCon('t=TOKEN');
    http.expectOne('/api/invitacion/abrir').flush(ficha());
    await refrescar();
    aceptarTodo();
    await refrescar();
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();
    http.expectOne('/api/ficha/enviar').flush({ estado: 'EN_REVISION' });
    await refrescar();

    expect(texto()).toContain('Recibimos tu ficha');
  });
});
