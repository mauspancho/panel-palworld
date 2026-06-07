# Palworld Admin Panel

Panel web para administrar servidores dedicados de Palworld en Linux.

El panel permite registrar servidores existentes y tambien crear servidores nuevos en dos modos:

- `SYSTEMD`: servidor Palworld nativo administrado con systemd.
- `DOCKER`: servidor Palworld levantado con Docker Compose.

## Caracteristicas

- Dashboard con estado, iniciar, detener, reiniciar, actualizar e instalar/crear.
- Registro de servidores systemd y Docker.
- Instalacion de servidores systemd con SteamCMD.
- Generacion de unidades systemd con `User` y `Group`.
- Generacion de `docker-compose.yml` para servidores Docker.
- Editor de `PalWorldSettings.ini` con formulario y modo avanzado.
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

```bash
mvn clean package
```

El JAR queda en:

```text
target/palworld-admin-0.1.0.jar
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
```

La app usa `sudo -n` por defecto. Esto evita que la web pida password. Si falta una regla sudoers, la accion falla y muestra el comando que no tiene permiso.

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

## Seguridad

- No subas `data/`, `target/`, `tools/`, logs ni archivos de mundo al repositorio.
- Cambia `PALWORLD_ADMIN_PASSWORD` antes de produccion.
- No uses `/`, `/home` ni `/opt` como ruta raiz del servidor.
- Usa rutas especificas como `/opt/palworld-servers/server01`.
- Las contrasenas del panel se almacenan con BCrypt.
- Los nombres de servicios, contenedores y usuarios se validan.
- Para produccion, considera reemplazar reglas amplias de `chown/chmod` por un helper root-owned que valide rutas.
