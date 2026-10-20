# Pruebas de integracion del Ejercicio 4: Server Side Template Injection (CWE-1336 / CWE-94)
#
# Se verifica que el texto de busqueda se trate como texto literal.
#
# Idea: se manda una expresion inofensiva que, SI el servidor la evalua, da como
# resultado el nombre de una funcion real ("Inception"). Si aparecen resultados
# es porque la expresion se evaluo.
#   - en main (vulnerable) los tests fallan
#   - en practico-2 (mitigada) pasan
#
# Como correrlas (desde la carpeta Ejercicio4, con la app levantada en Docker):
#   docker compose up -d --build
#   pytest -v
#   docker compose down -v
import requests


def buscar(base_url, texto):
    return requests.get(base_url + "/", params={"buscar": texto})


def test_concatenacion_de_spel_no_se_evalua(base_url):
    # si se evaluara daria "Inception" y encontraria resultados
    resp = buscar(base_url, "'Incep'+'tion'")

    assert resp.status_code == 200
    assert "Nombre de funcion:" not in resp.text


def test_acceso_a_runtime_no_se_evalua(base_url):
    # devuelve "Inception" solo si el servidor ejecuta la expresion
    resp = buscar(base_url, "T(java.lang.Runtime).getRuntime() != null ? 'Inception' : 'x'")

    assert resp.status_code == 200
    assert "Nombre de funcion:" not in resp.text
