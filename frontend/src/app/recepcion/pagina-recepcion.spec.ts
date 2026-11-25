import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PaginaRecepcion } from './pagina-recepcion';
import { ResumenInscripcion } from './inscripciones.service';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const PENDIENTES: ResumenInscripcion[] = [
  {
    id: 1,
    nombre: 'Ana',
    apellido: 'Pérez',
    email: 'ana@correo.com',
    celular: '11',
    estado: 'FICHA_PENDIENTE',
    origen: 'MOSTRADOR',
    creadaEn: '2026-11-24T10:00:00Z',
  },
  {
    id: 2,
    nombre: 'Rosa',
    apellido: 'Gómez',
    email: null,
    celular: '11',
    estado: 'FICHA_PENDIENTE',
    origen: 'MOSTRADOR',
    creadaEn: '2026-11-24T09:00:00Z',
  },
];

describe('PaginaRecepcion (/recepcion)', () => {
  let fixture: ComponentFixture<PaginaRecepcion>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginaRecepcion],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PaginaRecepcion);
    fixture.detectChanges();
    await esperar();
    responderPendientes(PENDIENTES);
    await refrescar();
  });

  afterEach(() => http.verify());

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const elemento = <T extends Element>(s: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(s) as T;
  const botones = (t: string) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).filter((b) =>
      (b.textContent ?? '').includes(t),
    ) as HTMLButtonElement[];

  async function refrescar(): Promise<void> {
    await esperar();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function responderPendientes(lista: ResumenInscripcion[]): void {
    const pedido = http.expectOne((r) => r.url === '/api/inscripciones' && r.method === 'GET');
    expect(pedido.request.params.get('estado')).toBe('FICHA_PENDIENTE');
    pedido.flush(lista);
  }

  function escribir(selector: string, valor: string): void {
    const input = elemento<HTMLInputElement>(selector);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  function completarAlta(email: string): void {
    escribir('input[formcontrolname=nombre]', 'Leo');
    escribir('input[formcontrolname=apellido]', 'Díaz');
    escribir('input[formcontrolname=email]', email);
    escribir('input[formcontrolname=celular]', '1155551234');
    escribir('input[formcontrolname=fechaNacimiento]', '1995-03-03');
  }

  it('muestra las fichas pendientes; sin correo se completan en el mostrador', async () => {
    expect(texto()).toContain('Pérez, Ana');
    expect(texto()).toContain('Sin correo: completar en el mostrador');
    expect(botones('Reenviar invitación')).toHaveLength(1); // solo para quien tiene correo
  });

  it('inscribe con correo y avisa que se mandó la invitación', async () => {
    completarAlta('leo@correo.com');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();

    const alta = http.expectOne((r) => r.url === '/api/inscripciones' && r.method === 'POST');
    expect(alta.request.body).toEqual({
      nombre: 'Leo',
      apellido: 'Díaz',
      email: 'leo@correo.com',
      celular: '1155551234',
      fechaNacimiento: '1995-03-03',
    });
    alta.flush(
      { id: 3, alumnoId: 3, estado: 'FICHA_PENDIENTE', invitacionEnviada: true, esMenor: false },
      { status: 201, statusText: 'Created' },
    );
    await esperar();
    responderPendientes(PENDIENTES);
    await refrescar();

    expect(texto()).toContain('le mandamos a Leo Díaz la invitación');
  });

  it('sin correo manda null y avisa que la ficha se completa abajo', async () => {
    completarAlta('');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();
    const alta = http.expectOne((r) => r.url === '/api/inscripciones' && r.method === 'POST');
    expect(alta.request.body.email).toBeNull();
    alta.flush(
      { id: 4, alumnoId: 4, estado: 'FICHA_PENDIENTE', invitacionEnviada: false, esMenor: true },
      { status: 201, statusText: 'Created' },
    );
    await esperar();
    responderPendientes(PENDIENTES);
    await refrescar();

    expect(texto()).toContain('quedó inscripto sin correo');
    expect(texto()).toContain('Es menor');
  });

  it('si el correo ya existe muestra el aviso del backend', async () => {
    completarAlta('ana@correo.com');
    elemento<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await esperar();
    http
      .expectOne((r) => r.url === '/api/inscripciones' && r.method === 'POST')
      .flush(
        { error: 'Ese correo ya está registrado en este gimnasio' },
        { status: 409, statusText: 'Conflict' },
      );
    await refrescar();

    expect(texto()).toContain('Ese correo ya está registrado');
  });

  it('reenvía la invitación y muestra el motivo si hay que esperar', async () => {
    botones('Reenviar invitación')[0].click();
    await esperar();
    http
      .expectOne('/api/inscripciones/1/reenviar-invitacion')
      .flush(
        { error: 'Esperá un minuto antes de volver a enviarla' },
        { status: 409, statusText: 'Conflict' },
      );
    await refrescar();

    expect(texto()).toContain('Esperá un minuto');
  });

  it('completa la ficha en el mostrador y la saca de la lista', async () => {
    botones('Completar ficha')[1].click(); // Rosa, sin correo
    await esperar();
    http.expectOne('/api/inscripciones/2/ficha').flush({
      inscripcionId: 2,
      nombre: 'Rosa',
      apellido: 'Gómez',
      email: null,
      fechaNacimiento: '1950-01-01',
      esMenor: false,
      estado: 'FICHA_PENDIENTE',
      datos: {
        dni: null,
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
          texto: 'T',
          soloMenores: false,
        },
      ],
    });
    await refrescar();
    expect(texto()).toContain('Ficha de Rosa Gómez');

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLInputElement>('input[type=checkbox]')!
      .click();
    await refrescar();
    elemento<HTMLFormElement>('app-formulario-ficha form').dispatchEvent(new Event('submit'));
    await esperar();
    http.expectOne('/api/inscripciones/2/ficha/enviar').flush({ estado: 'ACTIVO' });
    await esperar();
    responderPendientes([PENDIENTES[0]]);
    await refrescar();

    expect(texto()).toContain('Ficha de Rosa Gómez completa: ya es alumno activo');
    expect(texto()).not.toContain('Gómez, Rosa');
  });
});
