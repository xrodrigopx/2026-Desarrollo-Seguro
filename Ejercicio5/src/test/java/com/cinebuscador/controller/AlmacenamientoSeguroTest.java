package com.cinebuscador.controller;

import com.cinebuscador.model.User;
import com.cinebuscador.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Pruebas de seguridad del Ejercicio 5: almacenamiento inseguro de contrasenas
 * (CWE-256, CWE-327, CWE-798, CWE-200)
 *
 * Se verifica que la contrasena se guarde con un hash BCrypt y que quede fuera
 * de lo que recibe la vista. La version vulnerable la cifraba con AES/ECB y una
 * clave escrita en el codigo, y devolvia el valor cifrado a la vista.
 *   - en main (vulnerable) los tests fallan
 *   - en practico-2 (mitigada) pasan
 *
 * El UserRepository es un mock de Mockito.
 *
 * Como correrlas (desde la carpeta Ejercicio5):  mvn test
 */
class AlmacenamientoSeguroTest {

    private static final String PASSWORD = "Sup3rSecreta!";

    @Test
    void laPasswordSeGuardaConHashBcrypt() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByUsername(anyString())).thenReturn(Optional.empty());
        AuthController controller = new AuthController(repo);

        controller.register("alice", PASSWORD, PASSWORD, new ExtendedModelMap());

        // agarramos el usuario que se mando a guardar en la base
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(captor.capture());
        String guardada = captor.getValue().getPassword();

        // un hash BCrypt empieza con $2 y tiene 60 caracteres, por ejemplo $2a$10$...
        assertNotEquals(PASSWORD, guardada);
        assertTrue(guardada.startsWith("$2"), "No parece un hash BCrypt: " + guardada);
        assertEquals(60, guardada.length());
    }

    @Test
    void elRegistroNoMuestraLaPassword() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByUsername(anyString())).thenReturn(Optional.empty());
        AuthController controller = new AuthController(repo);
        Model model = new ExtendedModelMap();

        controller.register("alice", PASSWORD, PASSWORD, model);

        // en main la vista recibia la password cifrada en este atributo
        assertNull(model.getAttribute("encryptedPassword"));
    }
}
