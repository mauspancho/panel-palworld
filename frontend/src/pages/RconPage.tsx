import * as React from "react";
import { Clock3, Copy, Plus, Save, Send, Terminal, Trash2 } from "lucide-react";
import { api } from "../lib/api";
import type { AutoRestartConfig, RconConfig, RconPlayersView, RconWelcomeConfig, ServerView } from "../types";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { ServerSelect } from "../components/ServerSelect";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { ConfirmDialog } from "../components/ui/confirm-dialog";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";

const commandOptions = [
  { value: "ShowPlayers", label: "ShowPlayers", destructive: false },
  { value: "Broadcast", label: "Broadcast", destructive: false }
];

export function RconPage({
  selectedServerId,
  onSelectServer,
  commandsOnly = false,
  canManageConfig = false
}: {
  selectedServerId: number | null;
  onSelectServer: (id: number) => void;
  commandsOnly?: boolean;
  canManageConfig?: boolean;
}) {
  const [servers, setServers] = React.useState<ServerView[]>([]);
  const [config, setConfig] = React.useState<RconConfig | null>(null);
  const [welcomeConfig, setWelcomeConfig] = React.useState<RconWelcomeConfig | null>(null);
  const [autoRestartConfig, setAutoRestartConfig] = React.useState<AutoRestartConfig | null>(null);
  const [players, setPlayers] = React.useState<RconPlayersView | null>(null);
  const [command, setCommand] = React.useState("ShowPlayers");
  const [message, setMessage] = React.useState("");
  const [history, setHistory] = React.useState<Array<{ command: string; response: string; ok: boolean }>>([]);
  const [confirmOpen, setConfirmOpen] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [savingWelcome, setSavingWelcome] = React.useState(false);
  const [savingAutoRestart, setSavingAutoRestart] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    api.servers().then((items) => {
      setServers(items);
      if (!selectedServerId && items[0]) onSelectServer(items[0].id);
    }).catch((error) => toast({ title: "Error cargando servidores", description: error.message, variant: "error" }));
  }, [onSelectServer, selectedServerId, toast]);

  const currentId = selectedServerId ?? servers[0]?.id ?? null;

  React.useEffect(() => {
    if (!currentId) return;
    setPlayers(null);
    if (canManageConfig && !commandsOnly) {
      api.rconConfig(currentId).then(setConfig).catch(() => setConfig(null));
      api.rconWelcomeConfig(currentId).then(setWelcomeConfig).catch(() => setWelcomeConfig(null));
      api.autoRestartConfig(currentId).then(setAutoRestartConfig).catch(() => setAutoRestartConfig(null));
    } else {
      setConfig(null);
      setWelcomeConfig(null);
      setAutoRestartConfig(null);
    }
  }, [currentId, canManageConfig, commandsOnly]);

  const execute = async () => {
    if (!currentId) return;
    setBusy(true);
    try {
      if (command === "ShowPlayers") {
        const result = await api.rconPlayers(currentId);
        setPlayers(result);
        setHistory((items) => [{ command, response: result.raw || result.message, ok: result.success }, ...items].slice(0, 12));
      } else {
        const result = await api.rconBroadcast(currentId, message.trim());
        setHistory((items) => [{ command: `Broadcast ${message.trim()}`, response: result.message, ok: result.success }, ...items].slice(0, 12));
        if (result.success) setMessage("");
      }
    } catch (error) {
      const response = error instanceof Error ? error.message : "Error desconocido";
      setHistory((items) => [{ command, response, ok: false }, ...items].slice(0, 12));
    } finally {
      setBusy(false);
      setConfirmOpen(false);
    }
  };

  const saveConfig = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!currentId) return;
    const form = new FormData(event.currentTarget);
    try {
      const saved = await api.saveRconConfig(currentId, {
        enabled: form.get("enabled") === "on",
        host: String(form.get("host") || "127.0.0.1"),
        port: Number(form.get("port") || 25575),
        password: String(form.get("password") || "")
      });
      setConfig(saved);
      toast({ title: "RCON guardado", description: "Configuracion actualizada para este servidor." });
    } catch (error) {
      toast({ title: "No se pudo guardar RCON", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    }
  };

  const updateWelcomeMessage = (index: number, value: string) => {
    setWelcomeConfig((current) => {
      if (!current) return current;
      const messages = [...current.messages];
      messages[index] = value;
      return { ...current, messages };
    });
  };

  const addWelcomeMessage = () => {
    setWelcomeConfig((current) => {
      if (!current) return current;
      return { ...current, messages: [...current.messages, "Bienvenido {player} a {server}."] };
    });
  };

  const removeWelcomeMessage = (index: number) => {
    setWelcomeConfig((current) => {
      if (!current) return current;
      const messages = current.messages.filter((_, itemIndex) => itemIndex !== index);
      return { ...current, messages: messages.length ? messages : [""] };
    });
  };

  const saveWelcomeConfig = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!currentId || !welcomeConfig) return;
    const form = new FormData(event.currentTarget);
    const delaySeconds = Number(form.get("delaySeconds") || 20);
    const messages = welcomeConfig.messages.map((item) => item.trim()).filter(Boolean);
    setSavingWelcome(true);
    try {
      const saved = await api.saveRconWelcomeConfig(currentId, {
        enabled: form.get("enabled") === "on",
        delaySeconds,
        messages
      });
      setWelcomeConfig(saved);
      toast({ title: "Bienvenida guardada", description: "Los mensajes automaticos se actualizaron para este servidor." });
    } catch (error) {
      toast({ title: "No se pudo guardar la bienvenida", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setSavingWelcome(false);
    }
  };

  const saveAutoRestartConfig = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!currentId || !autoRestartConfig) return;
    const form = new FormData(event.currentTarget);
    const enabled = form.get("enabled") === "on";
    const time = String(form.get("time") || autoRestartConfig.time || "04:00");
    setSavingAutoRestart(true);
    try {
      const saved = await api.saveAutoRestartConfig(currentId, { enabled, time });
      setAutoRestartConfig(saved);
      toast({ title: "Reinicio automatico guardado", description: enabled ? `Se reiniciara diario a las ${saved.time}.` : "La programacion quedo desactivada." });
    } catch (error) {
      toast({ title: "No se pudo guardar el reinicio automatico", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setSavingAutoRestart(false);
    }
  };

  return (
    <div>
      <SectionHeader
        title={commandsOnly ? "Administracion de comandos" : "Consola RCON"}
        description="Solo se ejecutan comandos respaldados por endpoints reales del backend. No se exponen comandos arbitrarios peligrosos."
      />
      <div className="mb-4 max-w-xl">
        <ServerSelect servers={servers} value={currentId} onChange={onSelectServer} />
      </div>

      <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
        {!commandsOnly && canManageConfig ? (
          <Card>
            <CardHeader>
              <CardTitle>Configuracion RCON</CardTitle>
              <CardDescription>El password no se muestra despues de guardarlo.</CardDescription>
            </CardHeader>
            <CardContent>
              {!config ? <Skeleton className="h-52" /> : (
                <form className="space-y-4" onSubmit={saveConfig}>
                  <label className="flex items-center gap-2 text-sm">
                    <input name="enabled" type="checkbox" defaultChecked={config.enabled} className="h-4 w-4 rounded border-input bg-background" />
                    RCON activo
                  </label>
                  <div>
                    <label className="text-sm text-muted-foreground" htmlFor="host">IP / host</label>
                    <input id="host" name="host" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" defaultValue={config.host || "127.0.0.1"} required />
                  </div>
                  <div>
                    <label className="text-sm text-muted-foreground" htmlFor="port">Puerto</label>
                    <input id="port" name="port" type="number" min="1" max="65535" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" defaultValue={config.port || 25575} required />
                  </div>
                  <div>
                    <label className="text-sm text-muted-foreground" htmlFor="password">Password</label>
                    <input id="password" name="password" type="password" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" placeholder={config.passwordConfigured ? "Guardado; deja vacio para conservar" : "Password RCON"} />
                  </div>
                  <Button><Save className="h-4 w-4" />Guardar RCON</Button>
                </form>
              )}
            </CardContent>
          </Card>
        ) : null}

        <Card className={commandsOnly || !canManageConfig ? "xl:col-span-2" : ""}>
          <CardHeader>
            <CardTitle>Consola</CardTitle>
            <CardDescription>Selector de comandos disponibles para el servidor elegido.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-3 md:grid-cols-[240px_1fr_auto]">
              <select className="focus-ring h-10 rounded-md border border-input bg-background px-3 text-sm" value={command} onChange={(event) => setCommand(event.target.value)}>
                {commandOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
              </select>
              <input
                className="focus-ring h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                placeholder={command === "Broadcast" ? "Mensaje para Broadcast" : "Sin parametros"}
                disabled={command !== "Broadcast"}
              />
              <Button disabled={busy || !currentId || (command === "Broadcast" && !message.trim())} onClick={() => setConfirmOpen(true)}>
                <Send className="h-4 w-4" />Ejecutar
              </Button>
            </div>

            <div className="rounded-lg border border-border bg-muted/40 p-4 font-mono text-sm">
              <div className="mb-2 flex items-center justify-between text-muted-foreground">
                <span className="flex items-center gap-2"><Terminal className="h-4 w-4" />Respuesta</span>
                <Button variant="ghost" size="sm" onClick={() => navigator.clipboard.writeText(history[0]?.response || "")} disabled={!history[0]}>
                  <Copy className="h-3.5 w-3.5" />Copiar
                </Button>
              </div>
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap text-foreground scrollbar-soft">{history[0]?.response || "Sin ejecuciones todavia."}</pre>
            </div>

            {players?.success ? (
              <div className="text-sm text-muted-foreground">{players.players.length} jugador(es) conectado(s).</div>
            ) : players ? (
              <EmptyState title="RCON no disponible" description={players.message} />
            ) : null}
          </CardContent>
        </Card>
      </div>

      {!commandsOnly && canManageConfig ? (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle>Reinicio automatico diario</CardTitle>
            <CardDescription>Envia un aviso por Broadcast 15 minutos antes y reinicia el servidor a la hora seleccionada.</CardDescription>
          </CardHeader>
          <CardContent>
            {!autoRestartConfig ? <Skeleton className="h-44" /> : (
              <form className="space-y-4" onSubmit={saveAutoRestartConfig}>
                <div className="grid gap-4 md:grid-cols-[1fr_220px]">
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      name="enabled"
                      type="checkbox"
                      checked={autoRestartConfig.enabled}
                      onChange={(event) => setAutoRestartConfig({ ...autoRestartConfig, enabled: event.target.checked })}
                      className="h-4 w-4 rounded border-input bg-background"
                    />
                    Programar reinicio diario
                  </label>
                  <div>
                    <label className="text-sm text-muted-foreground" htmlFor="autoRestartTime">Hora de reinicio</label>
                    <input
                      id="autoRestartTime"
                      name="time"
                      type="time"
                      className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3"
                      value={autoRestartConfig.time || "04:00"}
                      onChange={(event) => setAutoRestartConfig({ ...autoRestartConfig, time: event.target.value })}
                      required
                    />
                  </div>
                </div>
                <div className="rounded-lg border border-border bg-muted/30 p-3 text-sm text-muted-foreground">
                  <div className="flex items-center gap-2 text-foreground">
                    <Clock3 className="h-4 w-4 text-cyan-300" />
                    Aviso automatico
                  </div>
                  <div className="mt-2">15 minutos antes se enviara: "En 15 min se reiniciara el servidor de forma automatica. Toma precauciones."</div>
                  <div className="mt-2">Ultimo aviso: {autoRestartConfig.lastWarningDate || "-"} · Ultimo reinicio: {autoRestartConfig.lastRunDate || "-"}</div>
                </div>
                <Button type="submit" disabled={savingAutoRestart}>
                  <Save className="h-4 w-4" />Guardar reinicio
                </Button>
              </form>
            )}
          </CardContent>
        </Card>
      ) : null}

      {!commandsOnly && canManageConfig ? (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle>Mensajes automaticos al entrar</CardTitle>
            <CardDescription>Se detecta cuando entra un jugador, se espera el tiempo configurado y se envia un Broadcast por RCON. Los mensajes rotan en orden.</CardDescription>
          </CardHeader>
          <CardContent>
            {!welcomeConfig ? <Skeleton className="h-64" /> : (
              <form className="space-y-4" onSubmit={saveWelcomeConfig}>
                <div className="grid gap-4 md:grid-cols-[1fr_220px]">
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      name="enabled"
                      type="checkbox"
                      checked={welcomeConfig.enabled}
                      onChange={(event) => setWelcomeConfig({ ...welcomeConfig, enabled: event.target.checked })}
                      className="h-4 w-4 rounded border-input bg-background"
                    />
                    Enviar bienvenida automatica
                  </label>
                  <div>
                    <label className="text-sm text-muted-foreground" htmlFor="delaySeconds">Espera en segundos</label>
                    <input
                      id="delaySeconds"
                      name="delaySeconds"
                      type="number"
                      min="0"
                      max="3600"
                      className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3"
                      value={welcomeConfig.delaySeconds}
                      onChange={(event) => setWelcomeConfig({ ...welcomeConfig, delaySeconds: Number(event.target.value || 0) })}
                    />
                  </div>
                </div>

                <div className="rounded-lg border border-border bg-muted/30 p-3 text-sm text-muted-foreground">
                  Variables disponibles: <span className="font-mono text-foreground">{"{player}"}</span>, <span className="font-mono text-foreground">{"{playerId}"}</span>, <span className="font-mono text-foreground">{"{platform}"}</span>, <span className="font-mono text-foreground">{"{server}"}</span>.
                </div>

                <div className="space-y-3">
                  {welcomeConfig.messages.map((item, index) => (
                    <div className="grid gap-2 md:grid-cols-[1fr_auto]" key={index}>
                      <input
                        className="focus-ring h-10 rounded-md border border-input bg-background px-3 text-sm"
                        value={item}
                        maxLength={300}
                        onChange={(event) => updateWelcomeMessage(index, event.target.value)}
                        placeholder="Ej. Bienvenido {player}, disfruta {server}."
                      />
                      <Button type="button" variant="outline" size="icon" onClick={() => removeWelcomeMessage(index)} aria-label="Eliminar mensaje">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="secondary" onClick={addWelcomeMessage} disabled={welcomeConfig.messages.length >= 20}>
                    <Plus className="h-4 w-4" />Agregar mensaje
                  </Button>
                  <Button type="submit" disabled={savingWelcome || welcomeConfig.messages.every((item) => !item.trim())}>
                    <Save className="h-4 w-4" />Guardar bienvenida
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      ) : null}
      <ConfirmDialog
        open={confirmOpen}
        busy={busy}
        title="Ejecutar comando"
        description={`Se enviara ${command} al servidor seleccionado.`}
        confirmLabel="Ejecutar"
        onCancel={() => setConfirmOpen(false)}
        onConfirm={execute}
      />
    </div>
  );
}
