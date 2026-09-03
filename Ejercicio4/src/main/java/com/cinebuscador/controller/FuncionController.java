package com.cinebuscador.controller;

import com.cinebuscador.model.Funcion;
import com.cinebuscador.repository.FuncionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class FuncionController {

    private final FuncionRepository funcionRepo;

    @Autowired
    public FuncionController(FuncionRepository funcionRepo) {
        this.funcionRepo = funcionRepo;
    }

    @GetMapping("/")
    public String search(@RequestParam(required = false) String buscar, Model model) {

        model.addAttribute("query", buscar != null ? buscar : "");

        if (buscar == null || buscar.isBlank()) {
            List<Funcion> todas = funcionRepo.findAll();
            model.addAttribute("resultados", todas);
            model.addAttribute("mensaje", "Mostrando todas las funciones.");
            return "index";
        }

        // Buscamos el texto tal cual lo escribio el usuario, como un simple
        // "contains". Ya no evaluamos el input como si fuera codigo.
        String textoBusqueda = buscar.toLowerCase();
        List<Funcion> todasLasFunciones = funcionRepo.findAll();
        List<Funcion> resultados = new ArrayList<>();

        for (Funcion funcion : todasLasFunciones) {
            if (funcion.getNombreFuncion() != null) {
                String nombre = funcion.getNombreFuncion().toLowerCase();
                if (nombre.contains(textoBusqueda)) {
                    resultados.add(funcion);
                }
            }
        }

        model.addAttribute("resultados", resultados);
        if (resultados.isEmpty()) {
            model.addAttribute("mensaje", "No se encontraron coincidencias.");
        } else {
            model.addAttribute("mensaje", "Resultados buscando por: " + buscar);
        }

        return "index";
    }
}
