package com.gimnasio.inscripcion;

/** Un documento legal vigente que hay que aceptar (ADR-0007 §8). */
public record DocumentoLegal(long id, String codigo, int version, String titulo, String texto, boolean soloMenores) {
}
