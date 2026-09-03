package com.cinebuscador.controller;

import com.cinebuscador.model.Pelicula;
import com.cinebuscador.repository.PeliculaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class PeliculaController {

    // Lista de extensiones y tipos de imagen que dejamos subir como afiche
    private static final List<String> EXTENSIONES_PERMITIDAS =
        Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> CONTENT_TYPES_PERMITIDOS =
        Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");

    private final PeliculaRepository peliculaRepo;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public PeliculaController(PeliculaRepository peliculaRepo) {
        this.peliculaRepo = peliculaRepo;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String buscar,
                        @RequestParam(required = false, defaultValue = "nombre") String ordenarPor,
                        @RequestParam(required = false, defaultValue = "ASC") String sentido,
                        Model model) {

        if (buscar != null && !buscar.isBlank()) {
            List<Object[]> resultadosRaw;
            if ("DESC".equalsIgnoreCase(sentido)) {
                resultadosRaw = peliculaRepo.searchWithFuncionesDesc(buscar, ordenarPor);
            } else {
                resultadosRaw = peliculaRepo.searchWithFunciones(buscar, ordenarPor);
            }

            // Wrap Object[] in Maps for cleaner Thymeleaf access
            List<Map<String, Object>> resultados = new java.util.ArrayList<>();
            for (Object[] row : resultadosRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id",        row[0]);
                map.put("nombre",    row[1]);
                map.put("fechaHora", row[2]);
                map.put("disponibles", row[3]);
                map.put("descripcion", row[4]);
                map.put("afichePath", row[5]);
                resultados.add(map);
            }
            model.addAttribute("resultados", resultados);
        }

        model.addAttribute("query", buscar != null ? buscar : "");
        model.addAttribute("sort_by", ordenarPor);
        model.addAttribute("sort_dir", sentido);
        return "index";
    }

    @GetMapping("/upload/{id}")
    public String uploadForm(@PathVariable Integer id, Model model) {
        Pelicula pelicula = peliculaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pelicula no encontrada"));
        model.addAttribute("pelicula", pelicula);
        return "upload";
    }

    @PostMapping("/upload/{id}")
    public String uploadFile(@PathVariable Integer id,
                             @RequestParam("afiche") MultipartFile archivo) throws IOException {
        Pelicula pelicula = peliculaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pelicula no encontrada"));

        if (archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo esta vacio");
        }

        // Chequeamos que el tipo de archivo declarado sea una imagen
        String contentType = archivo.getContentType();
        if (contentType == null || !CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de archivo no permitido");
        }

        // Sacamos la extension del nombre original solo para validarla,
        // el nombre en si no lo vamos a usar para guardar el archivo
        String originalFilename = archivo.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            int puntoIndex = originalFilename.lastIndexOf(".");
            extension = originalFilename.substring(puntoIndex + 1).toLowerCase();
        }

        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Extension de archivo no permitida");
        }

        // El nombre del archivo lo generamos nosotros con un UUID, asi el usuario
        // no puede meter cosas raras como "../" en el nombre para escribir en otro lado
        String nuevoNombre = UUID.randomUUID().toString() + "." + extension;

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path destino = uploadPath.resolve(nuevoNombre).normalize();
        if (!destino.startsWith(uploadPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de destino invalida");
        }

        Files.copy(archivo.getInputStream(), destino);

        pelicula.setAfichePath(nuevoNombre);
        peliculaRepo.save(pelicula);

        return "redirect:/";
    }

    @GetMapping("/uploads/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(filename).normalize();

        // Si alguien manda algo como "../../etc/passwd" en filename, el path
        // resuelto termina fuera de uploadPath y lo cortamos aca
        if (!filePath.startsWith(uploadPath)) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
            .body(resource);
    }

}