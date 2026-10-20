# Pruebas de seguridad del Ejercicio 2: XSS almacenado (CWE-79)
#
# Se verifica que lo que se guarda en la descripcion de una pelicula se muestre
# escapado y no como HTML (la mitigacion fue sacar el filtro "| safe" de edit.html).
#   - en main (vulnerable) los tests fallan
#   - en practico-2 (mitigada) pasan
#
# Como correrlas (desde la carpeta Ejercicio2):  pytest -v
from conftest import guardar_descripcion


def test_script_se_muestra_escapado(client):
    guardar_descripcion(client, "<script>alert(document.cookie)</script>")
    html = client.get("/edit/1").get_data(as_text=True)

    # el <script> no puede aparecer tal cual, tiene que estar escapado
    assert "<script>alert(document.cookie)</script>" not in html
    assert "&lt;script&gt;alert(document.cookie)&lt;/script&gt;" in html


def test_imagen_con_onerror_no_aparece_como_html(client):
    payload = "<img src=x onerror=alert(1)>"
    guardar_descripcion(client, payload)
    html = client.get("/edit/1").get_data(as_text=True)

    # si el payload aparece tal cual en la pagina el navegador lo ejecuta
    assert payload not in html
