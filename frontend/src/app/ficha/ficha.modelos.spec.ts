import { traducirError } from './ficha.modelos';

describe('traducirError', () => {
  it('pone el nombre visible del campo y deja el resto igual', () => {
    expect(traducirError('dni: obligatorio')).toBe('DNI: obligatorio');
    expect(traducirError('responsableVinculo: MADRE, PADRE o TUTOR')).toBe(
      'Vínculo con el menor: MADRE, PADRE o TUTOR',
    );
  });

  it('si no conoce el campo o no hay campo, lo deja como está', () => {
    expect(traducirError('otro: algo')).toBe('otro: algo');
    expect(traducirError('Sin campo')).toBe('Sin campo');
  });
});
