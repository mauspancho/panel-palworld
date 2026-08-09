# Palworld Admin Panel

Panel web para administrar servidores dedicados de Palworld en Linux.

El panel permite registrar servidores existentes y tambien crear servidores nuevos en dos modos:

- `SYSTEMD`: servidor Palworld nativo administrado con systemd.
- `DOCKER`: servidor Palworld levantado con Docker Compose.

## Caracteristicas

- Dashboard con estado, iniciar, detener, reiniciar, actualizar e instalar/crear.
- Dashboard moderno con conteo de jugadores activos por servidor usando RCON.
- Registro de servidores systemd y Docker.
- Instalacion de servidores systemd con SteamCMD.
- Generacion de unidades systemd con `User` y `Group`.
- Generacion de `docker-compose.yml` para servidores Docker.
- Editor de `PalWorldSettings.ini` con formulario y modo avanzado.
- Conexion RCON por servidor para ver jugadores y enviar mensajes Broadcast.
- Respaldo automatico antes de guardar configuracion.
- Detiene el servidor antes de guardar `PalWorldSettings.ini` y lo inicia despues.
- Deteccion y restauracion de backups de mundos.
- Vista de logs del servidor y logs de actividad del panel.
- Login de administrador.
- Base de datos H2 local en archivo.

## Requisitos

- Debian/Linux recomendado para uso real.
- Java 17 o superior.
- Maven 3.9 o superior para compilar.
- SteamCMD si se usaran servidores `SYSTEMD`.
- Docker y Docker Compose si se usaran servidores `DOCKER`.

## Compilar

Backend:

```bash
mvn clean package
```

El JAR queda en:

```text
target/palworld-admin-0.1.0.jar
```

El JAR incluye la interfaz React compilada en `/`. Despues de iniciar sesion, el dashboard moderno carga desde el mismo backend.

Frontend:

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

El frontend compilado queda en:

```text
frontend/dist/
```

Si quieres que el frontend quede dentro del JAR, compila React y copia el contenido de `frontend/dist/` a `src/main/resources/static/` antes de ejecutar `mvn clean package`.

Para desarrollo local del frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

Por defecto Vite usa:

```text
http://localhost:5173
```

Y proxya el backend hacia:

```text
http://localhost:8080
```

## Ejecutar

Configura usuario y password inicial del panel:

```bash
export PALWORLD_ADMIN_USER=admin
export PALWORLD_ADMIN_PASSWORD='change-this-password'
java -jar target/palworld-admin-0.1.0.jar --server.port=8030
```

Abre:

```text
http://SERVER_IP:8030
```

La base de datos se guarda en:

```text
./data/palworld-admin.mv.db
```

El usuario inicial solo se crea si no existe ningun usuario en la base.

## Variables de entorno

```bash
export PALWORLD_ADMIN_USER=admin
export PALWORLD_ADMIN_PASSWORD='change-this-password'

export PALWORLD_DEFAULT_RUN_USER=palworld
export PALWORLD_DEFAULT_RUN_GROUP=palworld
export PALWORLD_DEFAULT_BASE_PATH=/opt/palworld-servers
export PALWORLD_DEFAULT_STEAMCMD_PATH=/usr/games/steamcmd
export PALWORLD_DEFAULT_PUBLIC_PORT=8211
export PALWORLD_DEFAULT_USE_PERF_THREADS=true
export PALWORLD_DEFAULT_PUBLIC_LOBBY=true

export PALWORLD_SYSTEMCTL_COMMAND=/usr/bin/systemctl
export PALWORLD_JOURNALCTL_COMMAND=/usr/bin/journalctl
export PALWORLD_CP_COMMAND=/usr/bin/cp
export PALWORLD_CHOWN_COMMAND=/usr/bin/chown
export PALWORLD_CHMOD_COMMAND=/usr/bin/chmod

export PALWORLD_CORS_ALLOWED_ORIGIN=http://localhost:5173
export PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS='http://localhost:[*],http://127.0.0.1:[*],http://192.168.*:[*],http://10.*:[*],http://172.*:[*],https://pal.example.com,https://*.example.com'
```

La app usa `sudo -n` por defecto. Esto evita que la web pida password. Si falta una regla sudoers, la accion falla y muestra el comando que no tiene permiso.

`PALWORLD_CORS_ALLOWED_ORIGIN` y `PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS` solo son necesarios cuando el frontend corre en un origen distinto al backend, por ejemplo durante desarrollo con Vite. Si ves `Invalid CORS request`, agrega el origen real del navegador. Ejemplos:

```bash
export PALWORLD_CORS_ALLOWED_ORIGIN=http://TU_IP:5173
export PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS='http://TU_IP:[*],https://panel.example.com:[*]'
```

Para Cloudflare Tunnel con un dominio como `https://pal.example.com`, en el servicio systemd del backend puedes dejar:

```ini
Environment=PALWORLD_CORS_ALLOWED_ORIGIN=https://pal.example.com
Environment=PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS=https://pal.example.com,https://*.example.com
```

Despues aplica:

```bash
sudo systemctl daemon-reload
sudo systemctl restart palworld-admin.service
```

En produccion con Nginx sirviendo frontend y proxyando `/api`, `/login` y `/logout` en el mismo dominio, normalmente no hace falta cambiar CORS. No abras el frontend con `file://`; usa Nginx o `pnpm dev`.

## Servicio systemd para el panel

Ejemplo para ejecutar el panel como servicio:

```ini
[Unit]
Description=Palworld Admin Panel
After=network.target

[Service]
Type=simple
User=palworld-admin
Group=palworld-admin
WorkingDirectory=/opt/palworld-admin
Environment=PALWORLD_ADMIN_USER=admin
Environment=PALWORLD_ADMIN_PASSWORD=change-this-password
ExecStart=/usr/bin/java -jar /opt/palworld-admin/palworld-admin-0.1.0.jar --server.port=8030
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Instalacion:

```bash
sudo useradd -r -m -d /opt/palworld-admin -s /usr/sbin/nologin palworld-admin
sudo mkdir -p /opt/palworld-admin
sudo cp target/palworld-admin-0.1.0.jar /opt/palworld-admin/
sudo chown -R palworld-admin:palworld-admin /opt/palworld-admin
sudo nano /etc/systemd/system/palworld-admin.service
sudo systemctl daemon-reload
sudo systemctl enable --now palworld-admin.service
```

## Frontend React

La nueva interfaz vive en:

```text
frontend/
```

Tecnologias usadas:

- React con Vite.
- Tailwind CSS.
- Componentes locales estilo shadcn/ui.
- Lucide para iconos.
- Recharts para graficas con datos reales.
- API REST contra Spring Boot.
- Selector de tema: sistema, claro y oscuro.

El frontend no almacena ni muestra passwords RCON. Para RCON solo consulta si existe password configurado y envia cambios al backend por sesion autenticada.

Variables disponibles:

```bash
# Dejar vacio si Nginx sirve frontend y API en el mismo dominio
VITE_API_BASE_URL=

# Solo para desarrollo con Vite
VITE_API_PROXY_TARGET=http://localhost:8080
```

## Despliegue Debian 13 con Nginx

Ejemplo de build en el servidor:

```bash
cd /opt/palworld-admin-src
mvn clean package
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

Copiar artefactos:

```bash
sudo mkdir -p /opt/palworld-admin /var/www/palworld-admin
sudo cp /opt/palworld-admin-src/target/palworld-admin-0.1.0.jar /opt/palworld-admin/
sudo rsync -a --delete /opt/palworld-admin-src/frontend/dist/ /var/www/palworld-admin/
sudo chown -R palworld-admin:palworld-admin /opt/palworld-admin
sudo chown -R www-data:www-data /var/www/palworld-admin
```

Ejemplo Nginx:

```nginx
server {
    listen 80;
    server_name panel.example.com;

    root /var/www/palworld-admin;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /login {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /logout {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /css/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri /index.html;
    }
}
```

Validar y recargar:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

El servicio systemd del backend no necesita cambiar si ya ejecuta el JAR en `127.0.0.1:8080` o `0.0.0.0:8080`. Si expones todo por Nginx, se recomienda dejar el backend escuchando solo en localhost:

```ini
ExecStart=/usr/bin/java -jar /opt/palworld-admin/palworld-admin-0.1.0.jar --server.address=127.0.0.1 --server.port=8080
```

## Usuario Linux para servidores Palworld

Para servidores `SYSTEMD`, Palworld no debe correr como root. Crea un usuario dedicado:

```bash
sudo useradd -r -m -d /opt/palworld-servers -s /usr/sbin/nologin palworld
sudo mkdir -p /opt/palworld-servers
sudo chown -R palworld:palworld /opt/palworld-servers
```

En el formulario de cada servidor puedes configurar:

- Usuario Linux
- Grupo Linux
- Ruta raiz
- Puerto publico
- Ruta de SteamCMD

## Registrar servidor systemd

Campos tipicos:

- Nombre visible: `Server 01`
- Tipo: `SYSTEMD`
- Servicio Linux/systemd: `palworld-server01.service`
- Ruta raiz: `/opt/palworld-servers/server01`
- Usuario Linux: `palworld`
- Grupo Linux: `palworld`
- Puerto publico: `8211`
- Ruta SteamCMD: `/usr/games/steamcmd`

Al pulsar **Instalar/crear servidor**, el panel:

1. Valida que el usuario y grupo Linux existan.
2. Crea la ruta raiz si no existe.
3. Genera un archivo `.service` local.
4. Copia el `.service` a `/etc/systemd/system/`.
5. Ejecuta `systemctl daemon-reload`.
6. Ejecuta `systemctl enable`.
7. Ejecuta SteamCMD:

```bash
<steamcmdPath> +force_install_dir <rootPath> +login anonymous +app_update 2394010 validate +quit
```

8. Ejecuta:

```bash
chown -R <linuxUser>:<linuxGroup> <rootPath>
chmod +x <rootPath>/PalServer.sh
```

9. Inicia el servicio con systemd.

Ejemplo de unidad generada:

```ini
[Unit]
Description=Palworld Dedicated Server - Server 01
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
User=palworld
Group=palworld
WorkingDirectory=/opt/palworld-servers/server01
Environment=SteamAppId=2394010
ExecStart=/bin/bash /opt/palworld-servers/server01/PalServer.sh -publiclobby -publicport=8211 -useperfthreads
Restart=on-failure
RestartSec=10
LimitNOFILE=100000

[Install]
WantedBy=multi-user.target
```

## Registrar servidor Docker

Campos tipicos:

- Nombre visible: `Server Docker 01`
- Tipo: `DOCKER`
- Contenedor Docker: `palworld-server01`
- Docker Compose/project: `palworld-server01`
- Ruta raiz: `/opt/palworld-servers/docker-server01`
- Puerto publico: `8211`

Al pulsar **Instalar/crear servidor**, el panel:

1. Crea la ruta raiz si no existe.
2. Genera `<rootPath>/docker-compose.yml`.
3. Ejecuta:

```bash
docker compose -p <project> -f <rootPath>/docker-compose.yml up -d
```

Para Docker no se genera servicio systemd del servidor.

## RCON

Cada servidor puede guardar una configuracion RCON propia:

- IP / host
- Puerto
- Password
- Activo / inactivo

En el dashboard, cada fila de servidor tiene un boton **RCON** que abre una pantalla de configuracion para ese servidor. Desde esa pantalla puedes:

- Guardar o editar IP / host.
- Guardar o editar puerto.
- Guardar o cambiar el password.
- Activar o desactivar RCON para ese servidor.

El dashboard no muestra IP ni password RCON. En el bloque **RCON y jugadores** puedes elegir el servidor desde un combo y operar solo sobre el servidor seleccionado:

- Ver jugadores conectados.
- Refrescar manualmente la lista.
- Enviar mensajes Broadcast.

La lista de jugadores se actualiza automaticamente cada 1 minuto. El panel usa el comando RCON:

```text
ShowPlayers
```

Para mensajes usa:

```text
Broadcast <mensaje>
```

No se exponen comandos de teleport ni comandos arbitrarios desde la interfaz, porque algunos comandos requieren ser ejecutados por un administrador dentro del juego y no sirven desde RCON.

Para que RCON funcione, el servidor Palworld debe tener RCON habilitado en `PalWorldSettings.ini`, por ejemplo:

```text
RCONEnabled=True
RCONPort=25575
AdminPassword="password-rcon"
```

El puerto RCON debe estar accesible desde donde corre el panel. Si el panel corre en el mismo servidor, normalmente puedes usar:

```text
127.0.0.1
```

No abras RCON a internet sin firewall o VPN.

## Estadisticas persistentes de jugadores y gremios

PanelPalworld muestra estadisticas persistentes desde un archivo externo llamado `world-stats.json`. La aplicacion Java/Spring no ejecuta Python, no llama PalSav, no lee `Level.sav`, no lee `Level-min.json` y no modifica archivos del mundo. Java unicamente lee el JSON configurado por servidor.

La fuente oficial del generador esta versionada en este repositorio:

```text
tools/palworld-stats/palworld_stats.py
```

### Arquitectura

```text
Palworld
   |
   | Level.sav activo
   v
palworld_stats.py
   |
   | shutil.copy2()
   v
<PALWORLD_STATS_DIR>/work/Level.sav
   |
   | PalSav convert
   v
<PALWORLD_STATS_DIR>/work/Level-min.json
   |
   | ijson
   | CharacterSaveParameterMap
   | GroupSaveDataMap
   v
filtrado y calculo
   |
   v
world-stats.json.tmp
   |
   | os.replace()
   v
world-stats.json
   |
   v
PanelPalworld Java/Spring
```

### Requisitos Debian

Instala los paquetes base:

```bash
sudo apt update

sudo apt install -y \
  git \
  python3 \
  python3-venv \
  python3-dev \
  build-essential
```

Uso de cada paquete:

- `git`: clonar PalSav y el repositorio de PanelPalworld.
- `python3`: ejecutar el generador `palworld_stats.py`.
- `python3-venv`: crear el entorno aislado para PalSav.
- `python3-dev`: compilar dependencias Python con extensiones nativas si el entorno lo requiere.
- `build-essential`: provee compilador y herramientas de build usadas por algunas dependencias.

### Instalacion de PalSav

Define estos placeholders antes de seguir:

```text
<PALWORLD_USER>   usuario Linux que ejecuta el servidor Palworld
<PALWORLD_ROOT>   ruta raiz del servidor Palworld
<PARSER_DIR>      ruta donde se instalara PalSav
```

Valor recomendado para el parser:

```text
/opt/palworld-save-parser
```

Crea el directorio:

```bash
sudo mkdir -p <PARSER_DIR>

sudo chown \
  <PALWORLD_USER>:<PALWORLD_USER> \
  <PARSER_DIR>
```

Clona PalSav:

```bash
git clone \
  --branch palsav-unified \
  https://github.com/CyrixJD115/PalSav.git \
  <PARSER_DIR>/PalSav
```

Crea el entorno Python:

```bash
python3 -m venv \
  <PARSER_DIR>/venv
```

Actualiza herramientas base:

```bash
<PARSER_DIR>/venv/bin/pip install --upgrade \
  pip \
  setuptools \
  wheel
```

Instala `palooz`:

```bash
<PARSER_DIR>/venv/bin/pip install \
  <PARSER_DIR>/PalSav/palooz
```

Instala PalSav:

```bash
<PARSER_DIR>/venv/bin/pip install \
  <PARSER_DIR>/PalSav
```

Instala dependencias adicionales del generador:

```bash
<PARSER_DIR>/venv/bin/pip install \
  requests \
  ijson
```

Verifica el ejecutable:

```bash
<PARSER_DIR>/venv/bin/palsav -h
```

Debe mostrar comandos como:

```text
backup
convert
diag
validate
```

Verifica imports:

```bash
<PARSER_DIR>/venv/bin/python -c \
"import palsav, palooz, orjson, requests, ijson; print('OK')"
```

Resultado esperado:

```text
OK
```

### Localizar WORLD_ID

Busca el `Level.sav` activo:

```bash
find \
  <PALWORLD_ROOT>/Pal/Saved/SaveGames/0 \
  -name Level.sav \
  -type f
```

Pueden aparecer rutas bajo `backup/world/...`; no uses esas para la configuracion principal. Debe usarse el `Level.sav` directamente bajo:

```text
<PALWORLD_ROOT>/Pal/Saved/SaveGames/0/<WORLD_ID>/Level.sav
```

### Configuracion

El script usa estas variables:

```text
PALWORLD_ROOT       ruta raiz de Palworld
PALWORLD_WORLD_ID   ID del mundo
PALWORLD_STATS_DIR  directorio donde se escribira world-stats.json
PALWORLD_PARSER_DIR ruta donde esta instalado PalSav
```

Si `PALWORLD_STATS_DIR` no esta definido, el script usa:

```text
<PALWORLD_ROOT>/jugadores
```

Si `PALWORLD_PARSER_DIR` no esta definido, el script usa:

```text
/opt/palworld-save-parser
```

Crea el archivo:

```text
/etc/default/panelpalworld-save-stats
```

Contenido generico:

```bash
PALWORLD_ROOT=/ruta/al/servidor/palworld
PALWORLD_WORLD_ID=ID_DEL_MUNDO
PALWORLD_STATS_DIR=/ruta/al/servidor/palworld/jugadores
PALWORLD_PARSER_DIR=/opt/palworld-save-parser
```

Ejemplo conceptual:

```text
PALWORLD_ROOT=/home/usuario/palworld
PALWORLD_WORLD_ID=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
PALWORLD_STATS_DIR=/home/usuario/palworld/jugadores
PALWORLD_PARSER_DIR=/opt/palworld-save-parser
```

`PALWORLD_STATS_DIR` es la carpeta. El archivo final sera:

```text
<PALWORLD_STATS_DIR>/world-stats.json
```

Protege el archivo de configuracion:

```bash
sudo chown root:root \
  /etc/default/panelpalworld-save-stats

sudo chmod 644 \
  /etc/default/panelpalworld-save-stats
```

No coloques contrasenas, IPs publicas, `AdminPassword` ni datos reales de jugadores en este archivo.

### Script palworld_stats.py

Crea el directorio de salida:

```bash
sudo install -d \
  -o <PALWORLD_USER> \
  -g <PALWORLD_USER> \
  -m 755 \
  <PALWORLD_ROOT>/jugadores
```

Copia el script oficial desde el repositorio:

```bash
sudo install \
  -o <PALWORLD_USER> \
  -g <PALWORLD_USER> \
  -m 750 \
  tools/palworld-stats/palworld_stats.py \
  <PALWORLD_ROOT>/jugadores/palworld_stats.py
```

Si instalas desde una copia clonada de PanelPalworld, ejecuta el comando anterior desde la raiz del repositorio o cambia la ruta origen al path donde clonaste el proyecto.

Codigo completo versionado:

```python
#!/usr/bin/env python3

import json
import os
import shutil
import subprocess
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import ijson


PALWORLD_ROOT_ENV = os.environ.get("PALWORLD_ROOT", "").strip()
WORLD_ID = os.environ.get("PALWORLD_WORLD_ID", "").strip()
PARSER_DIR_ENV = os.environ.get("PALWORLD_PARSER_DIR", "/opt/palworld-save-parser").strip()
STATS_DIR_ENV = os.environ.get("PALWORLD_STATS_DIR", "").strip()

if not PALWORLD_ROOT_ENV:
    print("ERROR: PALWORLD_ROOT no esta configurado.", file=sys.stderr)
    sys.exit(1)

if not WORLD_ID:
    print("ERROR: PALWORLD_WORLD_ID no esta configurado.", file=sys.stderr)
    sys.exit(1)

PALWORLD_ROOT = Path(PALWORLD_ROOT_ENV).expanduser().resolve()
PARSER_DIR = Path(PARSER_DIR_ENV).expanduser().resolve()

if STATS_DIR_ENV:
    BASE_DIR = Path(STATS_DIR_ENV).expanduser().resolve()
else:
    BASE_DIR = PALWORLD_ROOT / "jugadores"

SOURCE_SAV = PALWORLD_ROOT / "Pal" / "Saved" / "SaveGames" / "0" / WORLD_ID / "Level.sav"
WORK_DIR = BASE_DIR / "work"
WORK_SAV = WORK_DIR / "Level.sav"
WORK_JSON = WORK_DIR / "Level-min.json"
OUTPUT_JSON = BASE_DIR / "world-stats.json"
OUTPUT_TMP = BASE_DIR / "world-stats.json.tmp"
PALSAV = PARSER_DIR / "venv" / "bin" / "palsav"

PLAYER_PATH = "properties.worldSaveData.value.CharacterSaveParameterMap.value.item"
GROUP_PATH = "properties.worldSaveData.value.GroupSaveDataMap.value.item"


def log(message):
    timestamp = datetime.now().astimezone().isoformat(timespec="seconds")
    print(f"[{timestamp}] {message}", flush=True)


def validate_configuration():
    if not SOURCE_SAV.exists():
        raise FileNotFoundError(f"No existe Level.sav: {SOURCE_SAV}")
    if not SOURCE_SAV.is_file():
        raise RuntimeError(f"Level.sav no es un archivo regular: {SOURCE_SAV}")
    if not os.access(SOURCE_SAV, os.R_OK):
        raise PermissionError(f"No hay permiso de lectura sobre: {SOURCE_SAV}")
    if not PALSAV.exists():
        raise FileNotFoundError(f"No existe PalSav: {PALSAV}")
    if not os.access(PALSAV, os.X_OK):
        raise PermissionError(f"PalSav no es ejecutable: {PALSAV}")


def copy_save():
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    BASE_DIR.mkdir(parents=True, exist_ok=True)

    log(f"Copiando Level.sav desde {SOURCE_SAV}")
    shutil.copy2(SOURCE_SAV, WORK_SAV)

    size_mb = WORK_SAV.stat().st_size / 1024 / 1024
    log(f"Save copiado: {size_mb:.2f} MB")


def convert_save():
    log("Convirtiendo copia de Level.sav con PalSav...")

    command = [
        str(PALSAV),
        "convert",
        str(WORK_SAV),
        "--to-json",
        "--custom-properties",
        ".worldSaveData.GroupSaveDataMap,.worldSaveData.CharacterSaveParameterMap.Value.RawData",
        "--minify-json",
        "--output",
        str(WORK_JSON),
        "--force",
    ]

    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=600,
        check=False,
    )

    if result.stdout.strip():
        log("PalSav stdout: " + result.stdout.strip())

    if result.returncode != 0:
        error = result.stderr.strip() or result.stdout.strip() or "Error desconocido"
        raise RuntimeError(f"PalSav termino con codigo {result.returncode}: {error}")

    if not WORK_JSON.exists():
        raise RuntimeError("PalSav termino sin generar el JSON temporal.")

    size_mb = WORK_JSON.stat().st_size / 1024 / 1024
    log(f"JSON temporal generado: {size_mb:.2f} MB")


def read_players():
    players = {}
    log("Leyendo jugadores...")

    with WORK_JSON.open("rb") as file:
        for entry in ijson.items(file, PLAYER_PATH):
            try:
                player_uid = entry["key"]["PlayerUId"]["value"].lower()
                save_parameter = entry["value"]["RawData"]["value"]["object"]["SaveParameter"]["value"]
                is_player = save_parameter.get("IsPlayer", {}).get("value", False)

                if not is_player:
                    continue

                nickname = save_parameter.get("NickName", {}).get("value", "")
                players[player_uid] = nickname
            except (KeyError, TypeError, AttributeError):
                continue

    log(f"Jugadores unicos encontrados: {len(players)}")
    return players


def read_guilds(players):
    guilds = {}
    memberships = defaultdict(list)

    log("Leyendo gremios...")

    with WORK_JSON.open("rb") as file:
        for entry in ijson.items(file, GROUP_PATH):
            try:
                value = entry["value"]
                group_type = value["GroupType"]["value"]["value"]

                if group_type != "EPalGroupType::Guild":
                    continue

                raw = value["RawData"]["value"]
                guild_id = str(raw.get("group_id") or entry.get("key"))
                guild_name = raw.get("guild_name", "")
                base_ids = raw.get("base_ids") or []
                guild_players = raw.get("players") or []
                admin_uid = str(raw.get("admin_player_uid", "")).lower()

                guilds[guild_id] = {
                    "name": guild_name,
                    "bases": len(base_ids),
                    "admin": admin_uid,
                }

                for member in guild_players:
                    try:
                        member_uid = str(member.get("player_uid", "")).lower()

                        if member_uid not in players:
                            continue

                        player_info = member.get("player_info", {})
                        last_online = player_info.get("last_online_real_time", 0) or 0
                        memberships[member_uid].append((int(last_online), guild_id))
                    except (TypeError, ValueError, AttributeError):
                        continue
            except (KeyError, TypeError, ValueError, AttributeError):
                continue

    log(f"Registros Guild encontrados: {len(guilds)}")
    return guilds, memberships


def determine_current_memberships(players, memberships):
    current_members = defaultdict(set)

    for player_uid in players:
        records = memberships.get(player_uid, [])
        if not records:
            continue

        max_last_online = max(last_online for last_online, guild_id in records)
        latest_guilds = {
            guild_id
            for last_online, guild_id in records
            if last_online == max_last_online
        }

        for guild_id in latest_guilds:
            current_members[guild_id].add(player_uid)

    return current_members


def build_result(players, guilds, current_members):
    active_guilds = {}
    active_players = set()

    for guild_id, member_uids in current_members.items():
        guild = guilds.get(guild_id)
        if guild is None:
            continue
        if guild["bases"] <= 0:
            continue

        active_guilds[guild_id] = guild
        active_players.update(member_uids)

    guild_rows = []

    for guild_id, guild in active_guilds.items():
        member_uids = current_members[guild_id]
        leader_uid = guild.get("admin", "")
        leader_name = players.get(leader_uid, "(desconocido)")
        members = []

        for member_uid in member_uids:
            member_name = players.get(member_uid, "(desconocido)")
            members.append({
                "uid": member_uid,
                "name": member_name,
                "leader": member_uid == leader_uid,
            })

        members.sort(key=lambda member: (member["name"] or "").lower())

        guild_rows.append({
            "id": guild_id,
            "name": guild["name"] or "Gremio sin nombre",
            "leader": {
                "uid": leader_uid,
                "name": leader_name,
            },
            "bases": guild["bases"],
            "memberCount": len(members),
            "members": members,
        })

    guild_rows.sort(key=lambda guild: (-guild["memberCount"], guild["name"].lower()))

    save_modified = datetime.fromtimestamp(SOURCE_SAV.stat().st_mtime).astimezone().isoformat(timespec="seconds")
    generated_at = datetime.now().astimezone().isoformat(timespec="seconds")

    return {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "saveLastModified": save_modified,
        "totalPlayers": len(players),
        "playersWithBase": len(active_players),
        "totalGuilds": len(active_guilds),
        "guilds": guild_rows,
    }


def write_result(result):
    log(f"Generando {OUTPUT_JSON}")

    with OUTPUT_TMP.open("w", encoding="utf-8") as file:
        json.dump(result, file, ensure_ascii=False, indent=2)
        file.flush()
        os.fsync(file.fileno())

    os.replace(OUTPUT_TMP, OUTPUT_JSON)
    log("world-stats.json actualizado correctamente")


def cleanup():
    for path in (WORK_JSON, WORK_SAV, OUTPUT_TMP):
        try:
            if path.exists():
                path.unlink()
        except OSError as exc:
            log(f"No se pudo eliminar {path}: {exc}")


def main():
    log("Iniciando actualizacion de estadisticas Palworld")

    try:
        validate_configuration()
        copy_save()
        convert_save()

        players = read_players()
        guilds, memberships = read_guilds(players)
        current_members = determine_current_memberships(players, memberships)
        result = build_result(players, guilds, current_members)
        write_result(result)

        log(
            "Resultado: "
            f"totalPlayers={result['totalPlayers']}, "
            f"playersWithBase={result['playersWithBase']}, "
            f"totalGuilds={result['totalGuilds']}"
        )

        return 0
    except subprocess.TimeoutExpired:
        log("ERROR: PalSav excedio el tiempo maximo de ejecucion")
        return 1
    except Exception as exc:
        log(f"ERROR: {type(exc).__name__}: {exc}")
        return 1
    finally:
        cleanup()


if __name__ == "__main__":
    sys.exit(main())
```

### Prueba manual

Carga variables:

```bash
set -a
source /etc/default/panelpalworld-save-stats
set +a
```

Ejecuta:

```bash
<PALWORLD_PARSER_DIR>/venv/bin/python \
  <PALWORLD_STATS_DIR>/palworld_stats.py
```

Salida aproximada:

```text
Iniciando actualizacion de estadisticas Palworld
Copiando Level.sav...
Save copiado...
Convirtiendo copia de Level.sav con PalSav...
JSON temporal generado...
Leyendo jugadores...
Jugadores unicos encontrados: X
Leyendo gremios...
Registros Guild encontrados: X
world-stats.json actualizado correctamente
Resultado: totalPlayers=X, playersWithBase=X, totalGuilds=X
```

Verifica el archivo final:

```bash
ls -lh \
  <PALWORLD_STATS_DIR>/world-stats.json
```

Valida la sintaxis:

```bash
python3 -m json.tool \
  <PALWORLD_STATS_DIR>/world-stats.json
```

### Formato world-stats.json

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-01-01T12:00:00-06:00",
  "saveLastModified": "2026-01-01T11:59:00-06:00",
  "totalPlayers": 100,
  "playersWithBase": 30,
  "totalGuilds": 8,
  "guilds": [
    {
      "id": "guild-id",
      "name": "Ejemplo Guild",
      "leader": {
        "uid": "player-uid",
        "name": "Jugador"
      },
      "bases": 5,
      "memberCount": 3,
      "members": [
        {
          "uid": "player-uid",
          "name": "Jugador",
          "leader": true
        }
      ]
    }
  ]
}
```

Campos:

- `totalPlayers`: total de jugadores reales encontrados en `CharacterSaveParameterMap`, deduplicados por `PlayerUId`.
- `playersWithBase`: cantidad de jugadores cuyo gremio actual tiene al menos una base.
- `totalGuilds`: cantidad de gremios actuales que tienen al menos una base.
- `guilds`: detalle de esos gremios, sus bases, lider y miembros.

### Logica real del calculo

No se obtiene `totalGuilds` contando directamente todos los registros `EPalGroupType::Guild`, porque `Level.sav` puede conservar registros historicos.

El script:

1. obtiene jugadores reales;
2. identifica cada `PlayerUId`;
3. lee todos los `Guild`;
4. obtiene sus miembros;
5. utiliza `last_online_real_time`;
6. determina la membresia mas reciente de cada jugador;
7. agrupa jugadores por su Guild actual;
8. elimina de los resultados Guilds con `base_ids == 0`;
9. genera el resultado definitivo.

Regla de bases:

```text
Guild con 0 bases:
  no aparece en guilds[]
  no incrementa totalGuilds
  sus jugadores no incrementan playersWithBase

Guild con >= 1 base:
  aparece en guilds[]
  incrementa totalGuilds
  sus miembros incrementan playersWithBase
```

Los jugadores de gremios sin base siguen contando dentro de:

```text
totalPlayers
```

### Servicio systemd

Crea:

```text
/etc/systemd/system/panelpalworld-save-stats.service
```

Contenido:

```ini
[Unit]
Description=PanelPalworld - Generador de estadisticas del save
After=palworld.service

[Service]
Type=oneshot

User=<PALWORLD_USER>
Group=<PALWORLD_USER>

EnvironmentFile=/etc/default/panelpalworld-save-stats

WorkingDirectory=<PALWORLD_STATS_DIR>

ExecStart=<PALWORLD_PARSER_DIR>/venv/bin/python <PALWORLD_STATS_DIR>/palworld_stats.py

TimeoutStartSec=15min

Nice=10
IOSchedulingClass=idle

NoNewPrivileges=true
```

Importante: `WorkingDirectory` y `ExecStart` no expanden variables del `EnvironmentFile` como lo haria un shell. Sustituye estos placeholders por valores reales al crear el unit file:

```text
<PALWORLD_STATS_DIR>
<PALWORLD_PARSER_DIR>
<PALWORLD_USER>
```

No uses `$PALWORLD_STATS_DIR` directamente en `ExecStart`.

Detalles:

- `Type=oneshot`: ejecuta el proceso y termina. Es normal ver `inactive (dead)` cuando termino correctamente; valida `status=0/SUCCESS`.
- `Nice=10`: reduce prioridad de CPU.
- `IOSchedulingClass=idle`: reduce prioridad de acceso a disco.
- `TimeoutStartSec=15min`: evita procesos bloqueados indefinidamente.

Recarga systemd:

```bash
sudo systemctl daemon-reload
```

Prueba el service antes del timer:

```bash
sudo systemctl start \
  panelpalworld-save-stats.service
```

Revisa estado:

```bash
systemctl status \
  panelpalworld-save-stats.service \
  --no-pager
```

### Timer systemd cada 20 minutos

Crea:

```text
/etc/systemd/system/panelpalworld-save-stats.timer
```

Contenido:

```ini
[Unit]
Description=PanelPalworld - Actualizar estadisticas Palworld cada 20 minutos

[Timer]
OnBootSec=2min
OnUnitActiveSec=20min
AccuracySec=15s
Persistent=true
Unit=panelpalworld-save-stats.service

[Install]
WantedBy=timers.target
```

Comportamiento:

- primera ejecucion aproximadamente 2 minutos despues del arranque;
- despues se ejecuta cada 20 minutos;
- `Persistent=true` conserva el comportamiento despues de reinicios.

Activa el timer:

```bash
sudo systemctl enable --now \
  panelpalworld-save-stats.timer
```

Verifica:

```bash
systemctl status \
  panelpalworld-save-stats.timer \
  --no-pager
```

Lista proximas ejecuciones:

```bash
systemctl list-timers \
  panelpalworld-save-stats.timer
```

### Logs

Ultimas lineas:

```bash
journalctl \
  -u panelpalworld-save-stats.service \
  -n 100 \
  --no-pager
```

En tiempo real:

```bash
journalctl \
  -u panelpalworld-save-stats.service \
  -f
```

### Integracion con PanelPalworld

En PanelPalworld configura la ruta completa del archivo por servidor desde `Servidores` -> `Editar servidor`:

```text
<PALWORLD_STATS_DIR>/world-stats.json
```

No configures solamente:

```text
<PALWORLD_STATS_DIR>
```

Ejemplo generico:

```text
/home/usuario/palworld/jugadores/world-stats.json
```

Si la ruta no esta configurada, el archivo no existe, no tiene permisos de lectura o el JSON no es valido, el dashboard sigue cargando y muestra un estado controlado como `No configurado`.

El usuario que ejecuta `palworld-admin.service` necesita permiso de lectura sobre el JSON y permiso de ejecucion sobre sus directorios padre. Ejemplo con ACLs:

```bash
sudo apt install -y acl
sudo setfacl -m u:<PANEL_USER>:rx /home/usuario
sudo setfacl -m u:<PANEL_USER>:rx /home/usuario/palworld
sudo setfacl -m u:<PANEL_USER>:rx /home/usuario/palworld/jugadores
sudo setfacl -m u:<PANEL_USER>:r /home/usuario/palworld/jugadores/world-stats.json
```

El dashboard muestra:

```text
Jugadores
Totales         100
Total gremios     8
```

La pagina `Gremios` muestra el detalle de gremios, cantidad de bases y jugadores de cada gremio desde `guilds[]`.

### Seguridad

El script nunca modifica:

```text
<PALWORLD_ROOT>/Pal/Saved/SaveGames/0/<WORLD_ID>/Level.sav
```

Solo copia:

```python
shutil.copy2(
    SOURCE_SAV,
    WORK_SAV
)
```

PalSav trabaja sobre:

```text
<PALWORLD_STATS_DIR>/work/Level.sav
```

No trabaja sobre el archivo activo. No uses `--from-json` ni operaciones que reconstruyan el save activo.

La escritura del JSON es atomica:

```text
world-stats.json.tmp
        |
        | escritura completa
        v
os.replace()
        |
        v
world-stats.json
```

Esto evita que Java lea un JSON incompleto.

Si falla lectura, copia, PalSav, JSON, parsing o escritura, el script termina con codigo distinto de cero y no borra el ultimo `world-stats.json` valido. Asi PanelPalworld puede seguir mostrando la ultima informacion disponible.

Despues de cada ejecucion se eliminan temporales:

```text
<PALWORLD_STATS_DIR>/work/Level.sav
<PALWORLD_STATS_DIR>/work/Level-min.json
<PALWORLD_STATS_DIR>/world-stats.json.tmp
```

Se mantiene:

```text
<PALWORLD_STATS_DIR>/world-stats.json
```

### Rendimiento

El JSON temporal puede ser muy grande. En pruebas, un `Level.sav` relativamente pequeno puede producir varios cientos de MB de JSON.

Por eso:

- Java no procesa `Level.sav`;
- se usa `ijson` para lectura incremental;
- se ejecuta periodicamente;
- el timer recomendado es cada 20 minutos;
- se usa `Nice=10`;
- se usa `IOSchedulingClass=idle`;
- los temporales se eliminan al finalizar.

Mejora futura posible, no implementada aqui:

```text
Eliminar la conversion completa a JSON y utilizar PalSav directamente como libreria Python para extraer unicamente CharacterSaveParameterMap y GroupSaveDataMap.
```

### Estructura final

Servidor Palworld:

```text
<PALWORLD_ROOT>/
|-- Pal/
|   `-- Saved/
|       `-- SaveGames/
|           `-- 0/
|               `-- <WORLD_ID>/
|                   `-- Level.sav
|
`-- jugadores/
    |-- palworld_stats.py
    |-- world-stats.json
    `-- work/
```

Parser:

```text
<PALWORLD_PARSER_DIR>/
|-- PalSav/
`-- venv/
```

Systemd:

```text
/etc/default/
`-- panelpalworld-save-stats

/etc/systemd/system/
|-- panelpalworld-save-stats.service
`-- panelpalworld-save-stats.timer
```

Repositorio PanelPalworld:

```text
tools/
`-- palworld-stats/
    `-- palworld_stats.py
```

## Permisos sudo sin password

El panel necesita ejecutar algunas acciones con privilegios root. Como una web no puede escribir passwords interactivos, se usa:

```bash
sudo -n
```

Eso significa:

- Si sudoers esta correcto, la accion se ejecuta sin pedir password.
- Si sudoers no coincide, la accion falla con `sudo: a password is required`.

Primero identifica el usuario Linux que ejecuta el panel:

```bash
whoami
```

Si el panel corre como servicio:

```bash
ps -eo user,cmd | grep palworld-admin
```

Comprueba las rutas reales:

```bash
command -v systemctl
command -v journalctl
command -v cp
command -v chown
command -v chmod
```

Edita sudoers con `visudo`:

```bash
sudo visudo -f /etc/sudoers.d/99-palworld-admin
```

Ejemplo para controles basicos, usando el usuario `palworld-admin`:

```sudoers
palworld-admin ALL=(root) NOPASSWD: /usr/bin/systemctl start palworld*.service
palworld-admin ALL=(root) NOPASSWD: /usr/bin/systemctl stop palworld*.service
palworld-admin ALL=(root) NOPASSWD: /usr/bin/systemctl restart palworld*.service
palworld-admin ALL=(root) NOPASSWD: /usr/bin/systemctl status palworld*.service
palworld-admin ALL=(root) NOPASSWD: /usr/bin/systemctl enable palworld*.service
palworld-admin ALL=(root) NOPASSWD: /usr/bin/systemctl daemon-reload
palworld-admin ALL=(root) NOPASSWD: /usr/bin/journalctl -u palworld*.service *
```

Ejemplo adicional para crear/instalar servidores bajo `/opt/palworld-servers`:

```sudoers
palworld-admin ALL=(root) NOPASSWD: /usr/bin/cp /opt/palworld-servers/*/*.service /etc/systemd/system/palworld*.service
palworld-admin ALL=(root) NOPASSWD: /usr/bin/chown -R palworld\:palworld /opt/palworld-servers/*
palworld-admin ALL=(root) NOPASSWD: /usr/bin/chmod +x /opt/palworld-servers/*/PalServer.sh
```

Si corres el panel manualmente con tu usuario, cambia `palworld-admin` por el usuario real que devuelve `whoami`.

Ejemplo para un panel ejecutado por `usuario-local` y servidores bajo `/home/usuario-local`:

```sudoers
usuario-local ALL=(root) NOPASSWD: /usr/bin/systemctl start palworld*.service
usuario-local ALL=(root) NOPASSWD: /usr/bin/systemctl stop palworld*.service
usuario-local ALL=(root) NOPASSWD: /usr/bin/systemctl restart palworld*.service
usuario-local ALL=(root) NOPASSWD: /usr/bin/systemctl status palworld*.service
usuario-local ALL=(root) NOPASSWD: /usr/bin/systemctl enable palworld*.service
usuario-local ALL=(root) NOPASSWD: /usr/bin/systemctl daemon-reload
usuario-local ALL=(root) NOPASSWD: /usr/bin/journalctl -u palworld*.service *
usuario-local ALL=(root) NOPASSWD: /usr/bin/cp /home/usuario-local/*/*.service /etc/systemd/system/palworld*.service
usuario-local ALL=(root) NOPASSWD: /usr/bin/chown -R usuario-local\:usuario-local /home/usuario-local/*
usuario-local ALL=(root) NOPASSWD: /usr/bin/chmod +x /home/usuario-local/*/PalServer.sh
```

Valida sudoers:

```bash
sudo visudo -c
```

Prueba sin usar el panel:

```bash
sudo -n /usr/bin/systemctl stop palworld.service
sudo -n /usr/bin/systemctl start palworld.service
```

Si ese comando pide password, la regla sudoers no coincide con el usuario, la ruta del binario o el nombre del servicio.

## Rutas de Palworld

Todas se calculan desde `rootPath`:

```text
<rootPath>/Pal/Saved/Config/LinuxServer/PalWorldSettings.ini
<rootPath>/DefaultPalWorldSettings.ini
<rootPath>/Pal/Saved/SaveGames/0/
<rootPath>/Pal/Saved/SaveGames/0/<WORLD_ID>/backup
```

## Editor de configuracion

El editor trabaja sobre:

```text
<rootPath>/Pal/Saved/Config/LinuxServer/PalWorldSettings.ini
```

Antes de guardar crea:

```text
PalWorldSettings.ini.bak-YYYYMMDD-HHmmss
```

Si el servidor esta encendido, el panel:

1. Detiene el servidor.
2. Guarda el archivo completo sin eliminar opciones no editadas.
3. Inicia el servidor nuevamente.

## Perfiles de configuracion

Los perfiles permiten guardar configuraciones completas reutilizables de `PalWorldSettings.ini` para eventos, pruebas o cambios temporales. En el panel moderno hay una seccion **Perfiles** y cada fila de servidor tambien tiene boton **Perfiles**.

Por servidor se puede:

- Crear un perfil desde la configuracion activa.
- Ver, editar y duplicar perfiles.
- Aplicar un perfil al archivo activo.
- Restaurar `default`.
- Exportar e importar perfiles JSON.
- Ver diferencias entre el INI activo y el perfil seleccionado.

El perfil `default` se crea automaticamente la primera vez que se abre la lista de perfiles de un servidor. Se captura desde el `PalWorldSettings.ini` activo, se marca como predeterminado y activo, y no se vuelve a sobrescribir en arranques posteriores. `default` no puede eliminarse.

Los perfiles se guardan como JSON por servidor:

```text
data/config-profiles/server-<ID>/config-profiles.json
data/config-profiles/server-<ID>/profiles/<perfil>.json
```

Antes de aplicar un perfil se crea un respaldo:

```text
data/config-profile-backups/server-<ID>/
```

La escritura del perfil, indice, respaldo y archivo activo usa archivos temporales y movimiento atomico cuando el sistema de archivos lo permite. Si el contenido activo ya no coincide con el hash normalizado del perfil marcado como activo, el panel muestra:

```text
Configuracion modificada fuera del perfil activo.
```

Los perfiles contienen la configuracion completa administrada por el editor, pero no almacenan secretos. `AdminPassword` y `ServerPassword` se guardan vacios al crear, editar, importar o exportar un perfil. Al aplicar un perfil, el panel inyecta `AdminPassword` desde la configuracion RCON almacenada para ese servidor; si no existe password RCON, conserva el valor activo del `PalWorldSettings.ini`. `ServerPassword` tambien se conserva desde el archivo activo cuando ya tiene un valor.

Si el servidor esta encendido, aplicar un perfil detiene Palworld, escribe el `PalWorldSettings.ini` y vuelve a iniciar el servicio para que el cambio tome efecto. Si el servidor esta detenido, solo escribe la configuracion y queda lista para el siguiente inicio.

Las acciones de crear, editar, duplicar, aplicar, restaurar, importar, exportar y eliminar perfiles requieren rol `ADMIN` en el panel. Este repositorio no contiene bot ni comandos Discord; la integracion se implemento en la arquitectura real disponible: API REST de Spring Boot y pantalla React.

## Backups
 
El panel detecta backups en:

```text
<rootPath>/Pal/Saved/SaveGames/0/<WORLD_ID>/backup
```

Al restaurar:

1. Pide confirmacion explicita.
2. Detiene el servidor.
3. Crea respaldo del estado actual.
4. Copia el backup seleccionado.
5. Corrige permisos con el usuario/grupo configurados.
6. Puede iniciar el servidor al terminar.

## Actividad reciente

La actividad reciente del dashboard tiene paginacion. Puedes elegir mostrar:

- 10 lineas
- 50 lineas
- 100 lineas

Esto evita que el bloque crezca sin limite cuando el panel lleva mucho tiempo en uso.

## Seguridad

- No subas `data/`, `target/`, `tools/`, logs ni archivos de mundo al repositorio.
- Cambia `PALWORLD_ADMIN_PASSWORD` antes de produccion.
- No publiques passwords RCON.
- No uses `/`, `/home` ni `/opt` como ruta raiz del servidor.
- Usa rutas especificas como `/opt/palworld-servers/server01`.
- Las contrasenas del panel se almacenan con BCrypt.
- Los nombres de servicios, contenedores y usuarios se validan.
- Para produccion, considera reemplazar reglas amplias de `chown/chmod` por un helper root-owned que valide rutas.
