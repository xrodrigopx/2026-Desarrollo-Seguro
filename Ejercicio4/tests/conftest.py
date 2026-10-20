# Configuracion comun de las pruebas de integracion (Ejercicios 3 y 4).
# Las pruebas le pegan por HTTP a la aplicacion que esta corriendo en Docker.
import os

import pytest
import requests

# se puede cambiar con la variable de entorno BASE_URL
BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080")


@pytest.fixture(scope="session")
def base_url():
    # si la app no esta levantada se saltean los tests
    try:
        requests.get(BASE_URL + "/", timeout=5)
    except requests.RequestException:
        pytest.skip("La aplicacion no responde en " + BASE_URL + ", levantarla con docker compose")
    return BASE_URL
