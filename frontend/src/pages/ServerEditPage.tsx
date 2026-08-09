import * as React from "react";
import { ArrowLeft, Save } from "lucide-react";
import { api } from "../lib/api";
import type { ServerView } from "../types";
import { SectionHeader } from "../components/SectionHeader";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { useToast } from "../components/ui/toast";

type ServerFormState = {
  name: string;
  type: string;
  serviceName: string;
  containerName: string;
  composeProjectName: string;
  rootPath: string;
  steamcmdPath: string;
  linuxUser: string;
  linuxGroup: string;
  publicPort: string;
  updateCommand: string;
  worldStatsPath: string;
  enabled: boolean;
};

export function ServerEditPage({
  server,
  onBack,
  onSaved
}: {
  server?: ServerView;
  onBack: () => void;
  onSaved: (server: ServerView) => void;
}) {
  const [form, setForm] = React.useState<ServerFormState>(() => fromServer(server));
  const [busy, setBusy] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    setForm(fromServer(server));
  }, [server]);

  const update = (field: keyof ServerFormState, value: string | boolean) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusy(true);
    try {
      const payload = {
        name: form.name.trim(),
        type: form.type,
        serviceName: clean(form.serviceName),
        containerName: clean(form.containerName),
        composeProjectName: clean(form.composeProjectName),
        rootPath: form.rootPath.trim(),
        steamcmdPath: clean(form.steamcmdPath),
        linuxUser: clean(form.linuxUser),
        linuxGroup: clean(form.linuxGroup),
        publicPort: form.publicPort.trim() ? Number(form.publicPort) : null,
        updateCommand: clean(form.updateCommand),
        worldStatsPath: clean(form.worldStatsPath),
        enabled: form.enabled
      };
      const saved = server ? await api.updateServer(server.id, payload) : await api.createServer(payload);
      toast({ title: "Servidor guardado", description: saved.name });
      onSaved(saved);
    } catch (error) {
      toast({ title: "No se pudo guardar", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const creating = !server;
  const isSystemd = form.type === "SYSTEMD";

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <SectionHeader
          title={creating ? "Agregar servidor" : "Editar servidor"}
          description={creating ? "Registra un servidor adicional para controlarlo desde el panel." : "Ajusta los datos registrados sin salir de la interfaz moderna."}
        />
        <Button type="button" variant="outline" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" />
          Volver
        </Button>
      </div>

      <form onSubmit={save} className="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>Datos generales</CardTitle>
            <CardDescription>Nombre, tipo de gestor y estado visible en el panel.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            <Field label="Nombre" value={form.name} onChange={(value) => update("name", value)} required />
            <label className="text-sm text-muted-foreground">
              Tipo
              <select className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3 text-foreground" value={form.type} onChange={(event) => update("type", event.target.value)}>
                <option value="SYSTEMD">SYSTEMD</option>
                <option value="DOCKER">DOCKER</option>
              </select>
            </label>
            <Field label="Ruta raiz" value={form.rootPath} onChange={(value) => update("rootPath", value)} required />
            <Field label="Puerto publico" value={form.publicPort} onChange={(value) => update("publicPort", value)} type="number" />
            <label className="flex items-center gap-2 text-sm text-muted-foreground md:col-span-2">
              <input className="h-4 w-4 rounded border-input bg-background" type="checkbox" checked={form.enabled} onChange={(event) => update("enabled", event.target.checked)} />
              Servidor activo en el panel
            </label>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{isSystemd ? "Servicio systemd" : "Contenedor Docker"}</CardTitle>
            <CardDescription>Campos usados por los controles de iniciar, detener, reiniciar, actualizar e instalar.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 md:grid-cols-2">
            {isSystemd ? (
              <Field label="Nombre del servicio" value={form.serviceName} onChange={(value) => update("serviceName", value)} placeholder="palworld.service" required />
            ) : (
              <>
                <Field label="Nombre del contenedor" value={form.containerName} onChange={(value) => update("containerName", value)} placeholder="palworld" required />
                <Field label="Proyecto Docker Compose" value={form.composeProjectName} onChange={(value) => update("composeProjectName", value)} placeholder="palworld" required />
              </>
            )}
            <Field label="Ruta steamcmd" value={form.steamcmdPath} onChange={(value) => update("steamcmdPath", value)} placeholder="/usr/games/steamcmd" />
            <Field label="Usuario Linux" value={form.linuxUser} onChange={(value) => update("linuxUser", value)} placeholder="palworld" />
            <Field label="Grupo Linux" value={form.linuxGroup} onChange={(value) => update("linuxGroup", value)} placeholder="palworld" />
            <label className="text-sm text-muted-foreground md:col-span-2">
              Comando update Docker permitido
              <textarea className="focus-ring mt-1 h-24 w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs text-foreground" value={form.updateCommand} onChange={(event) => update("updateCommand", event.target.value)} placeholder="docker exec contenedor /ruta/update.sh" />
            </label>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Estadisticas externas</CardTitle>
            <CardDescription>Ruta del JSON generado fuera del panel. La app solo lee este archivo.</CardDescription>
          </CardHeader>
          <CardContent>
            <Field label="Ruta world-stats.json" value={form.worldStatsPath} onChange={(value) => update("worldStatsPath", value)} placeholder="/home/usuario/palworld/jugadores/world-stats.json" />
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onBack}>Cancelar</Button>
          <Button type="submit" disabled={busy}>
            <Save className="h-4 w-4" />
            Guardar servidor
          </Button>
        </div>
      </form>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  required,
  type = "text"
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  type?: string;
}) {
  return (
    <label className="text-sm text-muted-foreground">
      {label}
      <input
        className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3 text-foreground"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        required={required}
        type={type}
      />
    </label>
  );
}

function fromServer(server?: ServerView): ServerFormState {
  if (!server) {
    return {
      name: "",
      type: "SYSTEMD",
      serviceName: "palworld.service",
      containerName: "palworld",
      composeProjectName: "palworld",
      rootPath: "/opt/palworld-servers/server01",
      steamcmdPath: "/usr/games/steamcmd",
      linuxUser: "palworld",
      linuxGroup: "palworld",
      publicPort: "8211",
      updateCommand: "",
      worldStatsPath: "",
      enabled: true
    };
  }
  return {
    name: server.name,
    type: server.type === "DOCKER" ? "DOCKER" : "SYSTEMD",
    serviceName: server.serviceName ?? "",
    containerName: server.containerName ?? "",
    composeProjectName: server.composeProjectName ?? "",
    rootPath: server.rootPath,
    steamcmdPath: server.steamcmdPath ?? "",
    linuxUser: server.linuxUser ?? "",
    linuxGroup: server.linuxGroup ?? "",
    publicPort: server.publicPort == null ? "" : String(server.publicPort),
    updateCommand: server.updateCommand ?? "",
    worldStatsPath: server.worldStatsPath ?? "",
    enabled: server.enabled
  };
}

function clean(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}
