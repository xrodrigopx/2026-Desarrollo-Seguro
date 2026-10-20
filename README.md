# Desarrollo de Software Seguro — CineBuscador

Repositorio de los practicos de la materia. Cada `EjercicioN/` es una aplicacion
de cine con una vulnerabilidad distinta. La rama `main` contiene las versiones
**vulnerables** y la rama `practico-2` las versiones **mitigadas** (ver
[Practico2.md](Practico2.md) en esa rama).

| Ej. | Vulnerabilidad | CWE | Tecnologia |
|---|---|---|---|
| 1 | Inyeccion SQL | CWE-89 | Python / Flask |
| 2 | Cross-Site Scripting almacenado | CWE-79 | Python / Flask |
| 3 | File Upload + Path Traversal | CWE-434 / CWE-22 | Java / Spring Boot |
| 4 | Server Side Template Injection (SpEL) | CWE-1336 / CWE-94 | Java / Spring Boot |
| 5 | Almacenamiento inseguro de contrasenas | CWE-256 / 327 / 798 / 200 | Java / Spring Boot |

---

# Practico 4 — Pruebas de seguridad automatizadas

Son **pruebas de regresion de seguridad** (DAST "personalizadas": se conoce la
vulnerabilidad y se repite el ataque). Se ejecutan en cada liberacion para
confirmar que una vulnerabilidad ya mitigada **no se reintroduce**.

**Resultado esperado — el mismo codigo de pruebas en las dos ramas:**

| Rama | Resultado |
|---|---|
| `main` (vulnerable) | las pruebas de ataque **FALLAN** |
| `practico-2` (mitigada) | **todas PASAN** |

Cada archivo de pruebas tiene en su cabecera la descripcion del ataque, la
mitigacion que verifica y el resultado esperado en cada rama.

## Mapa de pruebas

| Ej. | Tipo | Herramienta | Archivo |
|---|---|---|---|
| 1 SQLi | Unitarias | pytest | [Ejercicio1/tests/test_sqli.py](Ejercicio1/tests/test_sqli.py) |
| 2 XSS | Unitarias | pytest | [Ejercicio2/tests/test_xss.py](Ejercicio2/tests/test_xss.py) |
| 3 File upload | Integracion (HTTP sobre Docker) | pytest + requests | [Ejercicio3/tests/test_file_upload.py](Ejercicio3/tests/test_file_upload.py) |
| 4 SSTI | Integracion (HTTP sobre Docker) | pytest + requests | [Ejercicio4/tests/test_ssti.py](Ejercicio4/tests/test_ssti.py) |
| 5 Almacenamiento | Unitarias | JUnit 5 + Mockito | [Ejercicio5/src/test/java/com/cinebuscador/controller/AlmacenamientoSeguroTest.java](Ejercicio5/src/test/java/com/cinebuscador/controller/AlmacenamientoSeguroTest.java) |

## Requisitos

- Python 3.10+ (probado con 3.14) y `pip`
- Java 17+ y Maven 3.9+ (Ejercicio 5)
- Docker con `docker compose` (solo para los Ejercicios 3 y 4)

## 1. Ejercicios 1 y 2 — pytest (unitarias)

Corren con el cliente de pruebas de Flask y una base SQLite temporal.
Se ejecutan **desde la carpeta de cada ejercicio** (ambos tienen su propio
`app.py`).

```bash
# Una sola vez: entorno virtual con pytest y flask
python3 -m venv venv
source venv/bin/activate            # Windows: venv\Scripts\activate
pip install -r Ejercicio1/requirements-test.txt

# Ejercicio 1 - SQL Injection
cd Ejercicio1 && pytest -v && cd ..

# Ejercicio 2 - XSS almacenado
cd Ejercicio2 && pytest -v && cd ..
```

## 2. Ejercicio 5 — JUnit 5 (unitarias)

El repositorio de usuarios es un mock de Mockito.

```bash
cd Ejercicio5
mvn test
```

Los reportes quedan en `Ejercicio5/target/surefire-reports/`.

> **Nota para `main`:** antes de correr `mvn test` hay que agregar al `pom.xml`
> de `main`, dentro de `<dependencies>`:
>
> ```xml
> <dependency>
>     <groupId>org.springframework.boot</groupId>
>     <artifactId>spring-boot-starter-test</artifactId>
>     <scope>test</scope>
> </dependency>
> ```
>
> La prueba usa `AuthController`, `User` y `UserRepository`, que existen en
> ambas ramas, asi que compila igual en las dos.

## 3. Ejercicios 3 y 4 — integracion con pytest + requests (sobre Docker)

Las pruebas hacen peticiones HTTP reales a la aplicacion dockerizada en
`http://localhost:8080`. Los Ejercicios 3 y 4 usan el mismo puerto, asi que se
ejecutan **de a uno**: levantar el ejercicio, correr pytest y bajar el contenedor.

```bash
# Una sola vez (puede ser el mismo venv de arriba)
source venv/bin/activate
pip install -r Ejercicio3/requirements-test.txt

# Ejercicio 3 - File Upload
cd Ejercicio3
docker compose up -d --build
pytest -v
docker compose down -v
cd ..

# Ejercicio 4 - SSTI
cd Ejercicio4
docker compose up -d --build
pytest -v
docker compose down -v
cd ..
```

La primera ejecucion tarda mas porque Docker compila la imagen con Maven. Si el
puerto 8080 esta ocupado, hay que bajar el otro contenedor antes de levantar el
siguiente.

La URL base se puede cambiar con la variable de entorno `BASE_URL` (por defecto
`http://localhost:8080`). Si la aplicacion no esta levantada, los tests se
saltean (skip) con un mensaje que indica como levantarla.

## Probar las dos ramas

```bash
git checkout practico-2     # ejecutar todo lo anterior: debe pasar
git checkout main           # ejecutar todo lo anterior: los tests de ataque deben fallar
```

(En `main` recordar agregar la dependencia de test al `pom.xml` del Ejercicio 5.)

### Resultados observados

Cada ejercicio tiene 2 pruebas de ataque.

| Ej. | `practico-2` | `main` |
|---|---|---|
| 1 SQLi (pytest) | 2 pasan | 2 fallan |
| 2 XSS (pytest) | 2 pasan | 2 fallan |
| 3 File upload (integracion) | 2 pasan | 2 fallan |
| 4 SSTI (integracion) | 2 pasan | 2 fallan |
| 5 Almacenamiento (JUnit) | 2 pasan | 2 fallan |

## Que verifica cada prueba

- **Ej. 1 (SQLi):**
  - Un `UNION SELECT` en `buscar` no devuelve la version de SQLite.
  - Un valor malicioso en `sentido` (que va al `ORDER BY`) no rompe la consulta.
- **Ej. 2 (XSS):**
  - Un `<script>` guardado en la descripcion se muestra escapado (`&lt;script&gt;`).
  - Un `<img onerror=...>` guardado no aparece como HTML en la pagina.
- **Ej. 3 (File upload):**
  - Subir un `.html` se rechaza con 400.
  - Una imagen valida se guarda con nombre UUID y no con el nombre que manda el cliente.
- **Ej. 4 (SSTI):** dos sondas inofensivas de SpEL (una concatenacion y un acceso
  a `Runtime` condicional) que solo devuelven resultados si el servidor evalua la
  entrada.
- **Ej. 5 (Almacenamiento):**
  - La contrasena se guarda como hash BCrypt.
  - El registro no le pasa la contrasena a la vista.
