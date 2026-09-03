# Práctico 2 — Mitigación de Vulnerabilidades de CWE

**Materia:** Desarrollo de Software Seguro
**Rama:** practico-2

Este informe recorre los 5 ejercicios de la consigna. Para cada uno cuento
qué vulnerabilidad tenía el código original (con su CWE correspondiente),
cómo se explota paso a paso, y qué hice para corregirla en esta rama.

Todas las mitigaciones fueron probadas localmente antes y después del
cambio, confirmando que el payload deja de tener efecto y que la
funcionalidad legítima de cada aplicación se sigue comportando igual.

---

## Ejercicio 1 — Inyección SQL (SQLi)

**CWE-89: Improper Neutralization of Special Elements used in an SQL Command**

### La vulnerabilidad

En Ejercicio1/app.py, la función buscar_funciones() arma la consulta SQL
concatenando con f-strings los parámetros que llegan por query string:
buscar (dentro del LIKE) y sentido (en el ORDER BY).

```python
sql = f"... WHERE peliculas.nombre LIKE '%{query}%' " \
      f"ORDER BY {...} {sort_dir}"
```

El parámetro sort_by está parcialmente protegido, porque pasa por un
operador ternario que solo puede devolver uno de dos literales fijos. Pero
sort_dir se concatena tal cual, sin ningún filtro, y query se inserta
directo entre las comillas del LIKE. El atacante controla ambos valores
desde la URL.

### Cómo explotarla (contra main)

1. Levantar el servicio con docker-compose up en Ejercicio1/ (puerto 5000).
2. Romper la cadena del LIKE con una comilla simple, para confirmar que el
   input no está saneado (esto ya genera un error de sintaxis SQL):

   ```
   GET /?buscar=Dune'
   ```

3. Explotarlo con un UNION SELECT para extraer datos de otra tabla, por
   ejemplo la versión de SQLite:

   ```
   GET /?buscar=%25%27 UNION SELECT sqlite_version(), NULL, NULL--%20
   ```

   Decodificado: buscar=%' UNION SELECT sqlite_version(), NULL, NULL-- .
   Con eso cerramos el LIKE '%...%' con %', agregamos un UNION SELECT con
   3 columnas (mismo número que el SELECT original) y comentamos el resto
   de la consulta con --.

4. También se puede explotar el parámetro sentido, que va directo al
   ORDER BY sin comillas de por medio, permitiendo inyectar sub-consultas:

   ```
   GET /?buscar=Dune&ordenar_por=nombre&sentido=ASC); DROP TABLE peliculas--
   ```

   El driver sqlite3 de Python no permite múltiples sentencias en un solo
   execute(), pero el punto débil sigue siendo el mismo: se puede inyectar
   una sub-consulta en el ORDER BY para exfiltrar datos, por ejemplo con
   ORDER BY (SELECT CASE WHEN (SELECT COUNT(*) FROM sqlite_master)>0 THEN
   peliculas.nombre ELSE NULL END).

### Cómo lo arreglé

En Ejercicio1/app.py (y aplicando el mismo criterio en Ejercicio2/app.py,
que tenía el mismo patrón heredado):

- El término de búsqueda ahora viaja como parámetro ligado en
  db.execute(sql, (like_query,)), nunca concatenado al SQL.
- sort_by y sort_dir se validan con un if/else simple: si el valor no es
  exactamente uno de los que esperamos ('fecha' o 'DESC'), se usa el
  default seguro ('nombre'/'ASC'). Así el SQL final solo puede terminar
  usando columnas y direcciones fijas que definió el servidor, sin
  importar qué mande el atacante en la URL.

**Verificación:** los payloads de la comilla simple, el UNION SELECT y el
de sentido ahora se tratan como texto literal de búsqueda (o se normalizan
a ASC). La aplicación responde 200 OK sin errores de sintaxis y la tabla
peliculas queda intacta.

---

## Ejercicio 2 — Cross-Site Scripting (XSS)

**CWE-79: Improper Neutralization of Input During Web Page Generation (Stored XSS)**

### La vulnerabilidad

En Ejercicio2/templates/edit.html, la descripción de la película se
renderiza con el filtro | safe de Jinja2, que desactiva el autoescapado de
HTML:

```html
<div class="prev-descripcion">
    <strong>Descripción actual:</strong><br>
    {{ pelicula['descripcion'] | safe }}
</div>
```

Como la descripción se puede editar sin autenticación vía POST /edit/id y
se guarda tal cual en la base de datos, cualquier HTML o JavaScript que se
inserte ahí se ejecuta en el navegador de todo el que visite esa página
después. Eso es un XSS almacenado.

### Cómo explotarla (contra main)

1. Levantar el servicio con docker-compose up en Ejercicio2/ (puerto 5000).
2. Mandar este formulario a POST /edit/1 (con curl o desde el formulario
   web), usando como descripción:

   ```bash
   curl -X POST http://localhost:5000/edit/1 \
     --data-urlencode "nombre=Dune: Parte Dos" \
     --data-urlencode "genero=Ciencia Ficción" \
     --data-urlencode "director=Denis Villeneuve" \
     --data-urlencode "descripcion=<script>alert(document.cookie)</script>"
   ```

3. Visitar GET /edit/1 en el navegador: el script se inyecta sin escapar
   en el HTML de la respuesta y se ejecuta, mostrando el alert. En un
   escenario real ese payload podría robar cookies de sesión o redirigir a
   un sitio malicioso.

### Cómo lo arreglé

En Ejercicio2/templates/edit.html se eliminó el filtro | safe:

```html
{{ pelicula['descripcion'] }}
```

Sin ese filtro, Jinja2 vuelve a aplicar el autoescapado por defecto (que ya
viene activo en Flask), convirtiendo `<script>` en `&lt;script&gt;` al
renderizarlo. El navegador lo muestra como texto plano en vez de
ejecutarlo.

De paso, aproveché para corregir en Ejercicio2/app.py el mismo patrón de
SQLi por concatenación que documenté en el Ejercicio 1 (venía heredado del
código base), aplicando la misma solución de parámetros ligados y
whitelist de columnas de orden.

**Verificación:** al reenviar el payload y recargar /edit/1, la respuesta
contiene la versión escapada del tag (0 apariciones sin escapar),
confirmando que ya no es ejecutable.

---

## Ejercicio 3 — File Upload

**CWE-434: Unrestricted Upload of File with Dangerous Type** y
**CWE-22: Improper Limitation of a Pathname to a Restricted Directory (Path Traversal)**

### La vulnerabilidad

En Ejercicio3/.../PeliculaController.java encontré dos problemas
relacionados:

1. **Subida sin restricciones.** El archivo se guarda con el nombre
   original que manda el cliente, sin validar extensión ni content-type:

   ```java
   String filename = archivo.getOriginalFilename();
   Files.copy(archivo.getInputStream(), uploadPath.resolve(filename));
   ```

   Esto permite subir cualquier tipo de archivo (HTML con JavaScript,
   ejecutables, lo que sea), y como el nombre original no se sanea,
   también permite path traversal en la escritura: un nombre de archivo
   como ../../algo termina escribiendo fuera del directorio de uploads.

2. **Lectura sin validar el path.** El método serveFile usa normalize(),
   pero nunca chequea que la ruta resultante siga estando dentro de
   uploadDir, lo que permite path traversal también en la lectura (local
   file inclusion) para leer archivos arbitrarios del servidor.

### Cómo explotarla (contra main)

1. Levantar el servicio con docker-compose up en Ejercicio3/ (puerto 8080).
2. **Subir un archivo peligroso:** ir a GET /upload/1 y subir un archivo
   evil.html con este contenido:

   ```html
   <script>alert(document.cookie)</script>
   ```

   El servidor lo acepta sin validar tipo ni extensión, y queda accesible
   en /uploads/evil.html sirviéndose como text/html, es decir, un XSS
   almacenado servido directamente como página. Con otro backend que
   ejecute los archivos subidos (por ejemplo un .jsp en un contenedor de
   aplicaciones), el mismo patrón deriva directamente en ejecución remota
   de código.

3. **Path traversal en lectura:** pedir directamente

   ```
   GET /uploads/../../../../../../etc/passwd
   ```

   (o variantes con URL-encoding tipo %2e%2e%2f) para intentar leer
   archivos fuera del directorio de uploads configurado, ya que serveFile
   no valida que el path resuelto se mantenga adentro de ese directorio.

### Cómo lo arreglé

En PeliculaController.java:

- **En la subida:** ahora se valida el Content-Type contra una whitelist
  de tipos de imagen (jpeg, png, gif, webp) y la extensión del archivo
  original contra esa misma lista; si no cumple, se responde con un 400.
  El nombre de archivo con el que se guarda ya no lo elige el cliente:
  lo genera el servidor con un UUID más la extensión, así que el nombre
  que manda el cliente nunca se usa para construir un path, y el path
  traversal en escritura queda eliminado de raíz. Como capa extra, también
  se verifica que la ruta de destino resuelta se mantenga dentro del
  directorio de uploads.
- **En la lectura:** se normalizan tanto el directorio base como el path
  pedido, y se verifica que el segundo siga estando contenido dentro del
  primero antes de intentar leer el recurso. Si la ruta cae afuera, se
  responde 400 en vez de servir el archivo.

**Verificación:**
- Subir un .html devuelve 400 (rechazado).
- Subir un .jpg válido devuelve 302 (aceptado), guardado con un nombre
  tipo uuid.jpg dentro del directorio de uploads.
- El intento de path traversal queda bloqueado tanto por el propio Tomcat
  (400, detecta el ../ tras limpiar el path) como por la verificación que
  agregué.

---

## Ejercicio 4 — Server Side Template Injection (SSTI)

**CWE-1336: Improper Neutralization of Special Elements Used in a Template Engine**
(en la práctica equivale a **CWE-94: Improper Control of Generation of
Code**, porque el motor de expresiones termina ejecutando código
arbitrario)

### La vulnerabilidad

En Ejercicio4/.../config/SpelEvaluator.java, el texto de búsqueda que
ingresa el usuario se pasa directo al parser de expresiones SpEL de Spring
y se evalúa con un StandardEvaluationContext, que permite invocar métodos
y acceder a clases estáticas sin ninguna restricción:

```java
ExpressionParser parser = new SpelExpressionParser();
StandardEvaluationContext standardContext = new StandardEvaluationContext();
var expr = parser.parseExpression(expression); // "expression" = input del usuario
Object result = expr.getValue(standardContext);
```

Con ese contexto se puede usar el operador T() de SpEL para acceder a
cualquier clase de Java, lo que en la práctica habilita ejecución remota
de código en el servidor.

### Cómo explotarla (contra main)

1. Levantar el servicio con docker-compose up en Ejercicio4/ (puerto 8080).
2. Confirmar que el input se evalúa como expresión, con un payload
   aritmético simple:

   ```
   GET /?buscar=7*7
   ```

   El mensaje que devuelve es "Resultados buscando por: 49" en vez de
   tratar "7*7" como texto literal, lo que confirma que se está evaluando.

3. Explotarlo para ejecutar comandos del sistema operativo, usando la
   clase java.lang.Runtime:

   ```
   GET /?buscar=T(java.lang.Runtime).getRuntime().exec('id')
   ```

   Este payload logra que el servidor ejecute el comando id del sistema
   operativo. Con ProcessBuilder o variantes de exec con arreglos de
   argumentos se puede llegar a ejecución de comandos arbitrarios con los
   permisos del proceso Java.

### Cómo lo arreglé

La única forma segura de eliminar un SSTI es no evaluar nunca input de
usuario como código o expresión. Por eso saqué por completo el uso de
SpEL sobre el texto de búsqueda:

- Eliminé la clase SpelEvaluator.java (el motor de evaluación de
  expresiones sobre input no confiable).
- FuncionController.java ya no llama a ningún evaluador de expresiones:
  recorro todas las funciones con un for normal y me quedo con las que
  tienen el texto buscado dentro del nombre (nombre.contains(texto)),
  igual que en los demás ejercicios de búsqueda.
- Actualicé index.html para reflejar que la vulnerabilidad quedó
  mitigada, y quité la dependencia spring-expression del pom.xml, que ya
  no se usa.

**Verificación:**
- buscar=7*7 ya no se evalúa a 49; se busca literalmente esa cadena en los
  nombres de función (sin coincidencias).
- El payload de ejecución de Runtime también se busca de forma literal,
  sin coincidencias y sin ejecutar nada.

---

## Ejercicio 5 — Almacenamiento inseguro de credenciales

**CWE-798: Use of Hard-coded Credentials**, **CWE-327: Use of a Broken or
Risky Cryptographic Algorithm**, **CWE-256: Unprotected Storage of
Credentials** y **CWE-200: Exposure of Sensitive Information**

### La vulnerabilidad

En Ejercicio5/.../config/EncryptionService.java las contraseñas no se
hasheaban, se cifraban de forma reversible, y con varios problemas serios
encima:

1. **Clave estática embebida en el código fuente**, visible para cualquiera
   con acceso al repositorio. Encima la clase exponía métodos que
   devolvían esa clave en texto plano, en hex y en bytes.
2. **AES en modo ECB**, que es determinístico: dos contraseñas iguales
   producen el mismo texto cifrado, filtrando patrones y facilitando
   ataques de diccionario offline sobre los valores cifrados.
3. Al ser cifrado y no hash, cualquiera con la clave (que estaba
   hardcodeada) podía descifrar todas las contraseñas de todos los
   usuarios en texto plano. Una contraseña nunca debería poder
   recuperarse, solo verificarse.
4. AuthController.java además exponía la contraseña cifrada al cliente en
   la respuesta HTML después del login o el registro, un dato sensible que
   no debería llegar nunca al navegador.

### Cómo explotarla (contra main)

1. Levantar el servicio con docker-compose up en Ejercicio5/ (puerto 8080).
2. Registrar un usuario de prueba:

   ```bash
   curl -X POST http://localhost:8080/register \
     --data-urlencode "username=alice" \
     --data-urlencode "password=Sup3rSecreta!" \
     --data-urlencode "confirmPwd=Sup3rSecreta!"
   ```

   La respuesta incluye el campo "Password cifrada" con el valor en Base64
   del texto cifrado con AES/ECB, un dato que jamás debería exponerse al
   cliente.
3. **Descifrar con la clave hardcodeada:** como la clave está en el código
   fuente, cualquiera con acceso al repositorio (o simplemente leyendo el
   .class o el JAR) puede descifrar cualquier contraseña almacenada, por
   ejemplo con OpenSSL, tal como sugiere el propio comentario del código
   original. Ni siquiera hace falta acceso directo a la base de datos:
   alcanza con que el ciphertext se filtre por algún otro medio, como la
   inyección SQL del Ejercicio 1.
4. **Determinismo del modo ECB:** registrar dos usuarios distintos con la
   misma contraseña y comparar el campo "Password cifrada" de cada uno:
   son idénticos. Esto le permite a un atacante que compromete la base de
   datos detectar qué usuarios comparten contraseña sin descifrar nada.

### Cómo lo arreglé

Reemplacé el esquema de cifrado reversible por hashing de una vía con sal
aleatoria, usando BCrypt (spring-security-crypto):

- Agregué la dependencia org.springframework.security:spring-security-crypto
  en el pom.xml.
- Eliminé por completo EncryptionService.java (clave hardcodeada, modo
  ECB y los métodos que exponían la clave).
- En AuthController.java agregué un BCryptPasswordEncoder como campo de la
  clase. BCrypt genera una sal aleatoria distinta para cada contraseña y
  aplica varias rondas de hashing, así que dos contraseñas iguales
  terminan en hashes distintos, y el proceso es intencionalmente costoso
  de forzar por fuerza bruta.
- En el registro, la contraseña se hashea antes de guardarse: nunca se
  almacena ni se puede recuperar en texto plano.
- En el login, se verifica comparando contra el hash en vez de descifrar
  y comparar: el servidor ya no necesita (ni puede) reconstruir la
  contraseña original.
- Saqué el atributo que mandaba la contraseña cifrada al modelo, y quité
  de index.html el bloque que se la mostraba al usuario después de
  loguearse o registrarse.

**Verificación:**
- Registrar un usuario nuevo funciona bien, y la respuesta ya no contiene
  ningún valor de contraseña cifrada ni hasheada.
- El login con la contraseña correcta funciona ("Bienvenido, alice").
- El login con una contraseña incorrecta se rechaza.
- Lo que queda guardado en la base es ahora un hash BCrypt (formato
  $2a$10$...), no reversible sin fuerza bruta, y dos registros con la
  misma contraseña terminan con hashes distintos gracias a la sal
  aleatoria.

---

## Resumen

| Ejercicio | Vulnerabilidad | CWE | Mitigación |
|---|---|---|---|
| 1 | Inyección SQL | CWE-89 | Consultas parametrizadas y whitelist de columnas/orden |
| 2 | XSS almacenado | CWE-79 | Se sacó el filtro safe, vuelve a estar activo el autoescapado de Jinja2 |
| 3 | File Upload (tipo sin restricción y path traversal) | CWE-434, CWE-22 | Whitelist de extensión y content-type, nombre de archivo generado por el servidor, validación de que el path quede contenido en el directorio de uploads (subida y lectura) |
| 4 | SSTI (SpEL injection) | CWE-1336 / CWE-94 | Se eliminó la evaluación de expresiones sobre el input del usuario; ahora es una búsqueda de texto literal |
| 5 | Almacenamiento inseguro de contraseñas | CWE-798, CWE-327, CWE-256, CWE-200 | El cifrado reversible con clave hardcodeada se reemplazó por hashing con BCrypt; se dejó de exponer el valor al cliente |
