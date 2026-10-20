import os
import sqlite3
import sys

import pytest

# para poder hacer "import app" desde la carpeta tests
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import app as cine_app


@pytest.fixture
def db_path(tmp_path):
    # base de datos temporal para las pruebas
    path = str(tmp_path / "cine_test.db")
    conn = sqlite3.connect(path)
    conn.executescript("""
        CREATE TABLE peliculas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            genero TEXT NOT NULL,
            director TEXT NOT NULL
        );
        CREATE TABLE funciones (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pelicula_id INTEGER NOT NULL,
            fecha_hora TEXT NOT NULL,
            asientos_totales INTEGER NOT NULL DEFAULT 100,
            asientos_ocupados INTEGER NOT NULL DEFAULT 0
        );
        INSERT INTO peliculas (nombre, genero, director) VALUES
            ('Dune: Parte Dos', 'Ciencia Ficcion', 'Denis Villeneuve'),
            ('Oppenheimer', 'Drama', 'Christopher Nolan'),
            ('L''Auberge Espagnole', 'Comedia', 'Cedric Klapisch');
        INSERT INTO funciones (pelicula_id, fecha_hora, asientos_totales, asientos_ocupados) VALUES
            (1, '2026-07-13 14:30:00', 120, 85),
            (1, '2026-07-14 18:00:00', 120, 60),
            (2, '2026-07-13 16:00:00', 100, 92),
            (3, '2026-07-15 20:00:00', 80, 10);
    """)
    conn.commit()
    conn.close()
    return path


@pytest.fixture
def client(db_path):
    cine_app.app.config["DATABASE"] = db_path
    cine_app.app.config["TESTING"] = True
    return cine_app.app.test_client()
