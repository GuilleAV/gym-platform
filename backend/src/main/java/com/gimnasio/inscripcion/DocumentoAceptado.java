package com.gimnasio.inscripcion;

/** Qué documento y QUÉ VERSIÓN aceptó: si el texto cambió mientras completaba la ficha, no vale. */
public record DocumentoAceptado(String codigo, int version) {
}
