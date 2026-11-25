package com.gimnasio.soporte;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.gimnasio.notificaciones.Correo;
import com.gimnasio.notificaciones.Correos;

/**
 * Reemplazo de {@link Correos} para los tests: guarda los correos en memoria, en el momento (sin hilos),
 * para poder leer el código que "le llegó" al usuario. Se usa con {@code @Import(CorreosEnMemoria.class)}.
 *
 * <p>El envío real por SMTP se prueba aparte, contra Mailpit, en {@code CorreosSmtpIT}.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class CorreosEnMemoria {

    private static final List<Correo> ENVIADOS = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    Correos correosEnMemoria() {
        return ENVIADOS::add;
    }

    public static List<Correo> para(String email) {
        return ENVIADOS.stream().filter(c -> c.para().equals(email)).toList();
    }

    public static Optional<Correo> ultimoPara(String email) {
        List<Correo> correos = para(email);
        return correos.isEmpty() ? Optional.empty() : Optional.of(correos.getLast());
    }
}
