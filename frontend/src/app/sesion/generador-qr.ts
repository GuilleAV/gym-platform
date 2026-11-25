import { InjectionToken } from '@angular/core';

/** Convierte un texto (la URI otpauth://) en una imagen de código QR, como data URL. */
export type GeneradorQr = (texto: string) => Promise<string>;

/**
 * Token de inyección para el generador de QR.
 *
 * Por qué un token y no importar la librería directo en el componente:
 * - **Carga diferida:** el `import('qrcode')` dinámico hace que la librería (unos 25 kB) vaya en un
 *   archivo aparte que el navegador baja solo si alguien llega a configurar el MFA. El resto de los
 *   usuarios no paga ese peso al abrir la app.
 * - **Tests:** en los tests se reemplaza por una función falsa (jsdom no tiene canvas). Es el
 *   principio de inversión de dependencias: el componente depende de "algo que genera QR", no de
 *   una librería concreta.
 */
export const GENERADOR_QR = new InjectionToken<GeneradorQr>('GENERADOR_QR', {
  providedIn: 'root',
  factory: () => async (texto: string) => {
    const { toDataURL } = await import('qrcode');
    // Nivel M de corrección de errores: se lee aunque la pantalla tenga reflejos o esté un poco sucia.
    return toDataURL(texto, { errorCorrectionLevel: 'M', margin: 1, width: 220 });
  },
});
