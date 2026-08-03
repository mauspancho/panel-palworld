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
export PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS='http://localhost:[*],http://127.0.0.1:[*],http://192.168.*:[*],http://10.*:[*],http://172.*:[*],https://pal.linuxred.lat,https://*.linuxred.lat'
```

La app usa `sudo -n` por defecto. Esto evita que la web pida password. Si falta una regla sudoers, la accion falla y muestra el comando que no tiene permiso.

`PALWORLD_CORS_ALLOWED_ORIGIN` y `PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS` solo son necesarios cuando el frontend corre en un origen distinto al backend, por ejemplo durante desarrollo con Vite. Si ves `Invalid CORS request`, agrega el origen real del navegador. Ejemplos:

```bash
export PALWORLD_CORS_ALLOWED_ORIGIN=http://TU_IP:5173
export PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS='http://TU_IP:[*],https://panel.example.com:[*]'
```

Para Cloudflare Tunnel con el dominio `https://pal.linuxred.lat`, en el servicio systemd del backend puedes dejar:

```ini
Environment=PALWORLD_CORS_ALLOWED_ORIGIN=https://pal.linuxred.lat
Environment=PALWORLD_CORS_ALLOWED_ORIGIN_PATTERNS=https://pal.linuxred.lat,https://*.linuxred.lat
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
