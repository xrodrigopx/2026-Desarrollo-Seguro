# Pruebas de seguridad del Ejercicio 1: SQL Injection (CWE-89)
#
# Se verifica que la mitigacion (consulta parametrizada y orden validado)
# no vuelva a romperse en una version nueva.
#   - en main (vulnerable) los tests fallan
#   - en practico-2 (mitigada) pasan
#
# Como correrlas (desde la carpeta Ejercicio1):  pytest -v
import sqlite3


def test_union_select_no_muestra_la_version_de_sqlite(client):
    # ataque por el parametro "buscar": agregar un UNION SELECT a la consulta
    payload = "%' UNION SELECT sqlite_version(), NULL, NULL-- "
    resp = client.get("/", query_string={"buscar": payload})
    html = resp.get_data(as_text=True)

    assert resp.status_code == 200
    # si aparece el numero de version es porque el UNION se ejecuto
    assert sqlite3.sqlite_version not in html


def test_sentido_malicioso_no_rompe_la_consulta(client):
    # ataque por el parametro "sentido": va directo al ORDER BY
    resp = client.get("/", query_string={"buscar": "Dune", "sentido": "ASC; DROP TABLE peliculas--"})

    # si el valor llega al ORDER BY la consulta falla, si se valida sigue andando
    assert resp.status_code == 200
    assert "Dune: Parte Dos" in resp.get_data(as_text=True)
