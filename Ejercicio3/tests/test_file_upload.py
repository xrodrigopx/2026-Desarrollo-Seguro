# Pruebas de integracion del Ejercicio 3: File Upload (CWE-434)
#
# Se verifica que solo se puedan subir imagenes y que el servidor le ponga el
# nombre al archivo (UUID).
#   - en main (vulnerable) los tests fallan
#   - en practico-2 (mitigada) pasan
#
# Como correrlas (desde la carpeta Ejercicio3, con la app levantada en Docker):
#   docker compose up -d --build
#   pytest -v
#   docker compose down -v
import re
import uuid

import requests

# imagen PNG valida de 1x1 pixel
PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
    "1f15c4890000000d49444154789c6360000002000001e221bc330000000049454e44ae426082"
)


def subir(base_url, nombre, contenido, content_type):
    return requests.post(
        base_url + "/upload/1",
        files={"afiche": (nombre, contenido, content_type)},
        allow_redirects=False,
    )


def test_archivo_html_se_rechaza(base_url):
    nombre = "test_" + uuid.uuid4().hex[:10] + ".html"
    resp = subir(base_url, nombre, b"<script>alert(document.cookie)</script>", "text/html")

    # en main devuelve 302 y deja guardado el archivo
    assert resp.status_code == 400


def test_imagen_se_guarda_con_nombre_uuid(base_url):
    nombre = "test_" + uuid.uuid4().hex[:10] + ".png"
    subir(base_url, nombre, PNG, "image/png")
    html = requests.get(base_url + "/", params={"buscar": "Inception"}).text

    # el nombre guardado tiene que ser un UUID y no el que mando el cliente
    assert nombre not in html
    assert re.search(r"/uploads/[0-9a-f-]{36}\.png", html) is not None
