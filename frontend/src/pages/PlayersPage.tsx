import * as React from "react";
import { Send } from "lucide-react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../lib/api";
import type { PlayerAnalyticsRange, PlayerDurationAnalytics, PlayerDurationView, PlayerRegistryView, PlayerSessionView, ServerView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { ServerSelect } from "../components/ServerSelect";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";

export function PlayersPage({
  selectedServerId,
  onSelectServer
}: {
  selectedServerId: number | null;
  onSelectServer: (id: number) => void;
}) {
  const [servers, setServers] = React.useState<ServerView[]>([]);
  const [message, setMessage] = React.useState("");
  const [analyticsRange, setAnalyticsRange] = React.useState<PlayerAnalyticsRange>("week");
  const [analytics, setAnalytics] = React.useState<PlayerDurationAnalytics | null>(null);
  const [registry, setRegistry] = React.useState<PlayerRegistryView | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = React.useState(false);
  const [selectedPlayerKey, setSelectedPlayerKey] = React.useState<string>("");
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
    setAnalyticsLoading(true);
    api.playerAnalytics(currentId, analyticsRange)
      .then((result) => {
        setAnalytics(result);
        setSelectedPlayerKey((current) => result.players.some((player) => player.key === current) ? current : result.players[0]?.key ?? "");
      })
      .catch((error) => toast({ title: "No se pudo cargar analitica", description: error.message, variant: "error" }))
      .finally(() => setAnalyticsLoading(false));
    api.playerRegistry(currentId, analyticsRange)
      .then((result) => {
        setRegistry(result);
        setSelectedPlayerKey((current) => current || (result.players[0]?.key ?? ""));
      })
      .catch((error) => toast({ title: "No se pudo cargar registro de jugadores", description: error.message, variant: "error" }))
  }, [currentId, analyticsRange, toast]);

  const sendMessage = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!currentId || !message.trim()) return;
    try {
      const result = await api.rconBroadcast(currentId, message.trim());
      toast({ title: result.success ? "Mensaje enviado" : "No se pudo enviar", description: result.message, variant: result.success ? "success" : "error" });
      if (result.success) setMessage("");
    } catch (error) {
      toast({ title: "Error enviando mensaje", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    }
  };

  const selectedAnalyticsPlayer = analytics?.players.find((player) => player.key === selectedPlayerKey) ?? analytics?.players[0] ?? null;
  const selectedRegisteredPlayer = registry?.players.find((player) => player.key === selectedPlayerKey) ?? null;
  const playerOptions = analytics?.players.length ? analytics.players : registry?.players ?? [];
  const sessionRows = selectedRegisteredPlayer?.sessions.length ? selectedRegisteredPlayer.sessions : selectedAnalyticsPlayer?.sessions ?? [];
  const sessionChartData = sessionRows
    .slice()
    .reverse()
    .map((session, index) => ({
      id: `${session.startedAt}-${session.endedAt ?? "active"}-${index}`,
      label: sessionChartLabel(session.startedAt, index),
      startedAt: session.startedAt,
      endedAt: session.endedAt,
      active: Boolean(session.active),
      hours: session.hours
    }));
  const topPlayers = (analytics?.players ?? []).slice(0, 12).map((player) => ({ name: player.name, hours: player.totalHours }));
  const sessionColumns: DataTableColumn<PlayerSessionView>[] = [
    { key: "day", header: "Dia", sortable: true, searchValue: (session) => formatSessionDate(session.startedAt), sortValue: (session) => session.startedAt, render: (session) => <span className="font-medium">{formatSessionDate(session.startedAt)}</span> },
    { key: "start", header: "Entrada", sortable: true, searchValue: (session) => formatSessionTime(session.startedAt), sortValue: (session) => session.startedAt, render: (session) => formatSessionTime(session.startedAt) },
    { key: "end", header: "Desconexion", sortable: true, searchValue: (session) => session.endedAt ? formatSessionTime(session.endedAt) : "Conectado", sortValue: (session) => session.endedAt || "", render: (session) => session.endedAt ? formatSessionTime(session.endedAt) : "Conectado" },
    { key: "status", header: "Estado", sortable: true, searchValue: (session) => session.active ? "Activo" : "Desconectado", sortValue: (session) => session.active ? 1 : 0, render: (session) => session.active ? <span className="text-emerald-300">Activo</span> : <span className="text-muted-foreground">Desconectado</span> },
    { key: "duration", header: "Duracion", sortable: true, searchValue: (session) => formatHours(session.hours), sortValue: (session) => session.hours, render: (session) => formatHours(session.hours) }
  ];
  const playerColumns: DataTableColumn<PlayerDurationView>[] = [
    { key: "name", header: "Jugador", sortable: true, searchValue: (player) => player.name, render: (player) => <span className="font-medium">{player.name}</span> },
    { key: "playerId", header: "Player UID", sortable: true, searchValue: (player) => player.playerId || "", render: (player) => <span className="text-muted-foreground">{player.playerId || "-"}</span> },
    { key: "platformId", header: "Plataforma", sortable: true, searchValue: (player) => player.platformId || "", render: (player) => <span className="text-muted-foreground">{player.platformId || "-"}</span> },
    { key: "hours", header: "Tiempo estimado", sortable: true, searchValue: (player) => formatHours(player.totalHours), sortValue: (player) => player.totalHours, render: (player) => formatHours(player.totalHours) }
  ];

  function formatHours(hours: number) {
    return `${hours.toFixed(hours >= 10 ? 1 : 2)} h`;
  }

  function formatBucket(value: string) {
    if (analyticsRange === "day") {
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    }
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString([], { month: "short", day: "2-digit" });
  }

  function formatSessionDate(value: string) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString([], { weekday: "short", month: "short", day: "2-digit" });
  }

  function formatSessionTime(value: string) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  function sessionChartLabel(value: string, index: number) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return `Sesion ${index + 1}`;
    if (analyticsRange === "day") {
      return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    }
    return date.toLocaleString([], { month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit" });
  }

  function renderSessionTooltip({
    active,
    payload
  }: {
    active?: boolean;
    payload?: Array<{ payload?: { startedAt: string; endedAt: string | null; active: boolean; hours: number } }>;
  }) {
    if (!active || !payload?.length || !payload[0].payload) return null;
    const session = payload[0].payload;
    return (
      <div className="rounded-md border border-border bg-card p-3 text-sm text-card-foreground shadow-panel">
        <div className="font-medium">{formatSessionDate(session.startedAt)}</div>
        <div className="mt-1 text-muted-foreground">Entrada: {formatSessionTime(session.startedAt)}</div>
        <div className="text-muted-foreground">Desconexion: {session.endedAt ? formatSessionTime(session.endedAt) : "Conectado"}</div>
        <div className="mt-2">Duracion: {formatHours(session.hours)}</div>
        <div className="text-muted-foreground">Estado: {session.active ? "Activo" : "Desconectado"}</div>
      </div>
    );
  }

  function sessionRangeDescription() {
    if (analyticsRange === "day") {
      return "Sesiones registradas durante el dia seleccionado por el rango actual.";
    }
    if (analyticsRange === "month") {
      return "Sesiones registradas durante los ultimos 30 dias.";
    }
    return "Sesiones registradas durante los ultimos 7 dias.";
  }

  return (
    <div>
      <SectionHeader title="Jugadores" description="Jugadores conectados y horas estimadas por snapshots RCON cada 15 minutos." />
      <div className="mb-4 grid gap-3 lg:grid-cols-[minmax(260px,360px)_1fr]">
        <ServerSelect servers={servers} value={currentId} onChange={onSelectServer} />
        <form className="flex gap-2" onSubmit={sendMessage}>
          <input className="focus-ring h-10 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm" value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Mensaje Broadcast" maxLength={300} />
          <Button disabled={!currentId || !message.trim()}><Send className="h-4 w-4" />Enviar</Button>
        </form>
      </div>

      <Card className="mb-4">
        <CardHeader>
          <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
            <div>
              <CardTitle>Horas por jugador</CardTitle>
              <CardDescription>Cada snapshot donde aparece un jugador suma {analytics?.snapshotMinutes ?? 15} minutos.</CardDescription>
            </div>
            <select
              className="focus-ring h-10 rounded-md border border-input bg-background px-3 text-sm"
              value={analyticsRange}
              onChange={(event) => setAnalyticsRange(event.target.value as PlayerAnalyticsRange)}
            >
              <option value="day">Dia</option>
              <option value="week">Semana</option>
              <option value="month">Mes</option>
            </select>
          </div>
        </CardHeader>
        <CardContent>
          {analyticsLoading && !analytics ? <Skeleton className="h-72" /> : null}
          {analytics && analytics.players.length === 0 ? (
            <EmptyState title="Sin historial de jugadores" description="Cuando existan snapshots de RCON para este servidor, se calcularan las horas jugadas." />
          ) : null}
          {analytics && analytics.players.length > 0 ? (
            <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
              <Card>
                <CardHeader>
                  <CardTitle>Top jugadores</CardTitle>
                  <CardDescription>Jugadores con mas horas estimadas dentro del rango.</CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="h-72">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={topPlayers} layout="vertical" margin={{ left: 8, right: 16 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.16)" />
                        <XAxis type="number" tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                        <YAxis dataKey="name" type="category" width={110} tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                        <Tooltip formatter={(value) => formatHours(Number(value))} contentStyle={{ background: "hsl(var(--card))", color: "hsl(var(--card-foreground))", border: "1px solid hsl(var(--border))", borderRadius: 8 }} />
                        <Bar dataKey="hours" fill="#22d3ee" radius={[0, 4, 4, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                    <div>
                      <CardTitle>Detalle individual</CardTitle>
                      <CardDescription>Horas estimadas del jugador seleccionado.</CardDescription>
                    </div>
                    <select
                      className="focus-ring h-9 rounded-md border border-input bg-background px-2 text-sm"
                      value={selectedPlayerKey || selectedAnalyticsPlayer?.key || ""}
                      onChange={(event) => setSelectedPlayerKey(event.target.value)}
                    >
                      {playerOptions.map((player) => <option key={player.key} value={player.key}>{player.name}</option>)}
                    </select>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="h-72">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={selectedAnalyticsPlayer?.series ?? []}>
                        <defs>
                          <linearGradient id="playerHours" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#34d399" stopOpacity={0.5} />
                            <stop offset="95%" stopColor="#34d399" stopOpacity={0.03} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.16)" />
                        <XAxis dataKey="bucket" tickFormatter={formatBucket} tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                        <YAxis tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                        <Tooltip labelFormatter={formatBucket} formatter={(value) => formatHours(Number(value))} contentStyle={{ background: "hsl(var(--card))", color: "hsl(var(--card-foreground))", border: "1px solid hsl(var(--border))", borderRadius: 8 }} />
                        <Area type="monotone" dataKey="hours" stroke="#34d399" fill="url(#playerHours)" />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                </CardContent>
              </Card>

              <Card className="xl:col-span-2">
                <CardHeader>
                  <CardTitle>Horarios conectados/desconectados</CardTitle>
                  <CardDescription>{selectedRegisteredPlayer || selectedAnalyticsPlayer ? (selectedRegisteredPlayer ?? selectedAnalyticsPlayer)?.name : "Jugador seleccionado"}</CardDescription>
                </CardHeader>
                <CardContent>
                  {sessionRows.length > 0 ? (
                    <div className="space-y-4">
                      <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={sessionChartData} margin={{ left: 8, right: 16 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.16)" />
                            <XAxis dataKey="label" tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                            <YAxis tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                            <Tooltip content={renderSessionTooltip} />
                            <Bar dataKey="hours" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                      <div className="text-sm text-muted-foreground">{sessionRangeDescription()}</div>
                      <DataTable
                        data={sessionRows}
                        columns={sessionColumns}
                        getRowKey={(session) => `${session.startedAt}-${session.endedAt}`}
                        searchPlaceholder="Filtrar sesiones"
                      />
                    </div>
                  ) : (
                    <EmptyState title="Sin horarios registrados" description="Cuando el monitor detecte conexiones por RCON, aqui se mostraran entrada y desconexion de cada sesion." />
                  )}
                </CardContent>
              </Card>

              <Card className="xl:col-span-2">
                <CardHeader>
                  <CardTitle>Jugadores</CardTitle>
                  <CardDescription>Lista base con horas estimadas para el rango seleccionado.</CardDescription>
                </CardHeader>
                <CardContent>
                  <DataTable
                    data={analytics.players}
                    columns={playerColumns}
                    getRowKey={(player) => player.key}
                    searchPlaceholder="Filtrar jugadores"
                  />
                </CardContent>
              </Card>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
