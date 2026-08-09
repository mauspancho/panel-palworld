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
