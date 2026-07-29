import * as React from "react";
import { Activity, CircleAlert, Radio, Server, Users } from "lucide-react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../lib/api";
import type { ActivityItem, DashboardView, PlayerAnalyticsRange, PlayerAverageView, RconPlayer, ServerView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { MetricCard } from "../components/MetricCard";
import { SectionHeader } from "../components/SectionHeader";
import { StatusBadge } from "../components/StatusBadge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Skeleton } from "../components/ui/skeleton";
import { formatDateTime } from "../lib/utils";

type DashboardPlayerRow = {
  server: ServerView;
  player: RconPlayer | null;
  status: string;
  message: string;
};

export function DashboardPage({ onOpenRcon }: { onOpenRcon: (id: number) => void }) {
  const [data, setData] = React.useState<DashboardView | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [activityPage, setActivityPage] = React.useState(0);
  const [activitySize, setActivitySize] = React.useState(10);
  const [averageRange, setAverageRange] = React.useState<PlayerAnalyticsRange>("day");
  const [playerAverage, setPlayerAverage] = React.useState<PlayerAverageView | null>(null);
  const [playerStates, setPlayerStates] = React.useState<Record<number, { count: number | null; totalTracked: number | null; status: "loading" | "ok" | "disabled" | "error"; message?: string; players: RconPlayer[] }>>({});

  React.useEffect(() => {
    let cancelled = false;

    function refreshDashboard() {
      api.dashboard(activityPage, activitySize)
        .then((result) => {
          if (!cancelled) {
            setData(result);
            setError(null);
          }
        })
        .catch((err) => {
          if (!cancelled) {
            setError(err instanceof Error ? err.message : "Error cargando dashboard.");
          }
        });
    }

    refreshDashboard();
    const timer = window.setInterval(refreshDashboard, 60000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [activityPage, activitySize]);

  React.useEffect(() => {
    let cancelled = false;
    setPlayerAverage(null);
    api.playerAverage(averageRange)
      .then((result) => {
        if (!cancelled) setPlayerAverage(result);
      })
      .catch(() => {
        if (!cancelled) setPlayerAverage(null);
      });
    return () => {
      cancelled = true;
    };
  }, [averageRange]);

  React.useEffect(() => {
    if (!data) return;

    let cancelled = false;
    const rconServers = data.servers.filter((server) => server.rconEnabled);

    async function refreshPlayers() {
      if (rconServers.length === 0) {
        setPlayerStates({});
        return;
      }

      setPlayerStates((current) => {
        const next = { ...current };
        rconServers.forEach((server) => {
          next[server.id] = next[server.id] ?? { count: null, totalTracked: null, status: "loading", players: [] };
        });
        return next;
      });

      const results = await Promise.all(
        rconServers.map(async (server) => {
          try {
            const registry = await api.playerRegistry(server.id, "day");
            const activePlayers = registry.players
              .filter((player) => player.active)
              .map((player) => ({
                name: player.name,
                playerId: player.playerId || "",
                platformId: player.platformId || "",
                raw: player.key
              }));
            return {
              id: server.id,
              count: registry.activePlayers,
              totalTracked: registry.totalPlayers,
              status: "ok" as const,
              message: "OK",
              players: activePlayers
            };
          } catch (err) {
            return {
              id: server.id,
              count: null,
              totalTracked: null,
              status: "error" as const,
              message: err instanceof Error ? err.message : "No se pudo consultar el registro de jugadores.",
              players: []
            };
          }
        })
      );

      if (cancelled) return;
      setPlayerStates(Object.fromEntries(results.map((result) => [result.id, result])));
    }

    refreshPlayers();
    const timer = window.setInterval(refreshPlayers, 15000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [data]);

  if (error) {
    return <EmptyState title="No se pudo conectar con el backend" description={error} />;
  }

  if (!data) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-64" />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
        </div>
      </div>
    );
  }

  const hasSeries = data.activitySeries.length > 0;
  const servers = data.servers;
  const running = data.servers.find((server) => server.status === "RUNNING");
  const playerSummary = Object.values(playerStates).reduce(
    (summary, item) => ({
      total: summary.total + (item.count ?? 0),
      tracked: summary.tracked + (item.totalTracked ?? 0),
      okServers: summary.okServers + (item.status === "ok" ? 1 : 0),
      loading: summary.loading || item.status === "loading"
    }),
    { total: 0, tracked: 0, okServers: 0, loading: false }
  );

  function playersLabel(serverId: number) {
    const server = servers.find((item) => item.id === serverId);
    if (server?.rconEnabled && server.status === "STOPPED") return "Servidor detenido";
    const state = playerStates[serverId];
    if (!state) return "Sin RCON";
    if (state.status === "loading") return "Consultando";
    if (state.status === "error") return "Error RCON";
    return `${state.count ?? 0} de ${state.totalTracked ?? 0}`;
  }

  function connectedSummaryLabel() {
    if (playerSummary.loading) {
      return "...";
    }
    return `${playerSummary.total} de ${playerSummary.tracked}`;
  }

  function averageLabel() {
    if (!playerAverage) {
      return "...";
    }
    return playerAverage.averagePlayers.toLocaleString("es-MX", { maximumFractionDigits: 1 });
  }

  function averageDetail() {
    if (!playerAverage) {
      return "Calculando promedio";
    }
    if (playerAverage.sampleCount === 0) {
      return `Sin snapshots en ${playerAverage.label.toLowerCase()}`;
    }
    return `Pico ${playerAverage.peakPlayers} · ${playerAverage.sampleCount} punto(s)`;
  }

  const rconServers = data.servers.filter((server) => server.rconEnabled);
  const playerRows: DashboardPlayerRow[] = rconServers.flatMap((server): DashboardPlayerRow[] => {
    if (server.status === "STOPPED") {
      return [{ server, player: null, status: "Servidor detenido", message: "RCON se consulta solo cuando el servidor esta encendido." }];
    }
    const state = playerStates[server.id];
    if (!state || state.status === "loading") {
      return [{ server, player: null, status: "Consultando", message: "Actualizando lista de jugadores." }];
    }
    if (state.status === "error") {
      return [{ server, player: null, status: "Error RCON", message: state.message || "No se pudo consultar RCON." }];
    }
    if (state.players.length === 0) {
      return [{ server, player: null, status: "Sin jugadores", message: "No hay jugadores conectados." }];
    }
    return state.players.map((player) => ({ server, player, status: "Conectado", message: "" }));
  });
  const playerColumns: DataTableColumn<DashboardPlayerRow>[] = [
    { key: "server", header: "Servidor", sortable: true, searchValue: (row) => row.server.name, render: (row) => <span className="font-medium">{row.server.name}</span> },
    { key: "name", header: "Nombre", sortable: true, searchValue: (row) => row.player?.name || row.status, render: (row) => row.player?.name || row.status },
    { key: "playerId", header: "Player UID", sortable: true, searchValue: (row) => row.player?.playerId || row.message || "", render: (row) => <span className="font-mono text-xs">{row.player?.playerId || row.message || "-"}</span> },
    { key: "platform", header: "Plataforma", sortable: true, searchValue: (row) => row.player?.platformId || "", render: (row) => row.player?.platformId || "-" },
    {
      key: "status",
      header: "Estado",
      sortable: true,
      searchValue: (row) => row.status,
      render: (row) => row.status === "Conectado" ? (
        <span className="text-emerald-500">Conectado</span>
      ) : row.status === "Error RCON" ? (
        <Button variant="ghost" size="sm" onClick={() => onOpenRcon(row.server.id)}>Revisar RCON</Button>
      ) : (
        <span className="text-muted-foreground">{row.status}</span>
      )
    }
  ];
  const serverColumns: DataTableColumn<ServerView>[] = [
    { key: "name", header: "Servidor", sortable: true, searchValue: (server) => server.name, render: (server) => <span className="font-medium">{server.name}</span> },
    { key: "status", header: "Estado", sortable: true, searchValue: (server) => server.statusLabel, render: (server) => <StatusBadge status={server.status} label={server.statusLabel} /> },
    { key: "players", header: "Jugadores", sortable: true, searchValue: (server) => playersLabel(server.id), sortValue: (server) => playerStates[server.id]?.count ?? -1, render: (server) => playersLabel(server.id) },
    { key: "publicPort", header: "Puerto juego", sortable: true, searchValue: (server) => server.publicPort ?? "", sortValue: (server) => server.publicPort ?? 0, render: (server) => server.publicPort ?? "-" },
    {
      key: "rcon",
      header: "RCON",
      sortable: true,
      searchValue: (server) => server.rconEnabled ? "Configurado" : "No configurado",
      render: (server) => server.rconEnabled ? (
        <Button variant="ghost" size="sm" onClick={() => onOpenRcon(server.id)}>
          {playerStates[server.id]?.status === "error" ? "Revisar RCON" : "Abrir RCON"}
        </Button>
      ) : (
        <span className="text-muted-foreground">No configurado</span>
      )
    }
  ];
  const activityColumns: DataTableColumn<ActivityItem>[] = [
    { key: "date", header: "Fecha", sortable: true, searchValue: (item) => formatDateTime(item.startedAt), sortValue: (item) => item.startedAt || "", render: (item) => formatDateTime(item.startedAt) },
    { key: "server", header: "Servidor", sortable: true, searchValue: (item) => item.serverName || "Servidor eliminado", render: (item) => item.serverName || "Servidor eliminado" },
    { key: "action", header: "Accion", sortable: true, searchValue: (item) => item.action || "", render: (item) => item.action || "-" },
    { key: "status", header: "Estado", sortable: true, searchValue: (item) => item.status || "", render: (item) => item.status || "-" },
    { key: "user", header: "Usuario", sortable: true, searchValue: (item) => item.username || "", render: (item) => item.username || "-" }
  ];

  function formatSnapshotTick(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  function formatSnapshotDateTime(value: string | undefined) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString([], { month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit" });
  }

  function renderSnapshotTooltip({
    active,
    payload,
    label
  }: {
    active?: boolean;
    payload?: Array<{ value?: number; payload?: { players?: string[] } }>;
    label?: string;
  }) {
    if (!active || !payload?.length) return null;
    const point = payload[0];
    const players = point.payload?.players ?? [];
    return (
      <div className="rounded-md border border-border bg-card p-3 text-sm text-card-foreground shadow-panel">
        <div className="font-medium">{formatSnapshotDateTime(label)}</div>
        <div className="mt-1 text-muted-foreground">{point.value ?? 0} usuario(s) conectados</div>
        {players.length > 0 ? (
          <div className="mt-2 space-y-1">
            {players.slice(0, 8).map((player) => <div key={player}>{player}</div>)}
            {players.length > 8 ? <div className="text-muted-foreground">+{players.length - 8} mas</div> : null}
          </div>
        ) : (
          <div className="mt-2 text-muted-foreground">Sin usuarios conectados</div>
        )}
      </div>
    );
  }

  return (
    <div>
      <SectionHeader title="Dashboard" description="Estado general con datos reales del backend y actividad registrada por el panel." />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <MetricCard title="Servidores" value={data.stats.totalServers} detail={`${data.stats.runningServers} encendido(s)`} icon={Server} />
        <MetricCard title="RCON activo" value={data.stats.rconEnabledServers} detail="Configurado por servidor" icon={Radio} />
        <MetricCard
          title="Jugadores activos"
          value={connectedSummaryLabel()}
          detail={playerSummary.okServers > 0 ? "conectados de jugadores con historial" : "Sin datos RCON activos"}
          icon={Users}
        />
        <Card className="overflow-hidden">
          <CardContent className="p-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <div className="text-sm text-muted-foreground">Promedio jugadores</div>
                  <select
                    className="focus-ring h-7 rounded-md border border-input bg-background px-2 text-xs"
                    value={averageRange}
                    onChange={(event) => setAverageRange(event.target.value as PlayerAnalyticsRange)}
                    aria-label="Rango del promedio de jugadores"
                  >
                    <option value="day">Dia</option>
                    <option value="week">Semana</option>
                    <option value="month">Mes</option>
                  </select>
                </div>
                <div className="mt-2 text-3xl font-semibold tracking-normal">{averageLabel()}</div>
                <div className="mt-1 text-sm text-muted-foreground">{averageDetail()}</div>
              </div>
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-cyan-400/10 text-cyan-200 ring-1 ring-cyan-300/20">
                <Activity className="h-5 w-5" />
              </div>
            </div>
          </CardContent>
        </Card>
        <MetricCard title="Alertas" value={data.stats.errorServers} detail="Estados con error" icon={CircleAlert} />
      </div>

      <div className="mt-5 grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader>
            <CardTitle>Usuarios conectados</CardTitle>
            <CardDescription>Actividad calculada desde las sesiones de conexion guardadas.</CardDescription>
          </CardHeader>
          <CardContent>
            {hasSeries ? (
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={data.activitySeries}>
                    <defs>
                      <linearGradient id="actions" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#22d3ee" stopOpacity={0.55} />
                        <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.16)" />
                    <XAxis dataKey="date" tickFormatter={formatSnapshotTick} tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fill: "#94a3b8", fontSize: 12 }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <Tooltip content={renderSnapshotTooltip} />
                    <Area type="monotone" dataKey="actions" stroke="#22d3ee" fillOpacity={1} fill="url(#actions)" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <EmptyState title="Sin sesiones registradas" description="Cuando el monitor detecte conexiones, la grafica se llenara desde el historial guardado." />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Servidor destacado</CardTitle>
            <CardDescription>{running ? "Primer servidor encendido detectado." : "No hay servidores encendidos."}</CardDescription>
          </CardHeader>
          <CardContent>
            {running ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="font-semibold">{running.name}</div>
                    <div className="text-sm text-muted-foreground">{running.rootPath}</div>
                  </div>
                  <StatusBadge status={running.status} label={running.statusLabel} />
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="rounded-md border border-border bg-muted/40 p-3">
                    <div className="text-muted-foreground">Puerto juego</div>
                    <div className="mt-1 font-semibold">{running.publicPort ?? "-"}</div>
                  </div>
                  <div className="rounded-md border border-border bg-muted/40 p-3">
                    <div className="text-muted-foreground">Puerto RCON</div>
                    <div className="mt-1 font-semibold">{running.rconPort ?? "-"}</div>
                  </div>
                  <div className="rounded-md border border-border bg-muted/40 p-3">
                    <div className="text-muted-foreground">Jugadores activos</div>
                    <div className="mt-1 font-semibold">{playersLabel(running.id)}</div>
                  </div>
                  <div className="rounded-md border border-border bg-muted/40 p-3">
                    <div className="text-muted-foreground">Actualizacion</div>
                    <div className="mt-1 font-semibold">1 min</div>
                  </div>
                </div>
                <Button variant="outline" onClick={() => onOpenRcon(running.id)}>Abrir RCON</Button>
              </div>
            ) : (
              <EmptyState title="Sin servidor activo" description="Inicia un servidor para ver su estado operativo aqui." />
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="mt-5">
        <CardHeader>
          <CardTitle>Jugadores conectados</CardTitle>
          <CardDescription>Lista activa del registro de presencia actualizado por RCON en backend.</CardDescription>
        </CardHeader>
        <CardContent>
          {rconServers.length > 0 ? (
            <DataTable
              data={playerRows}
              columns={playerColumns}
              getRowKey={(row) => `${row.server.id}-${row.player?.playerId || row.player?.raw || row.status}`}
              searchPlaceholder="Filtrar jugadores"
            />
          ) : (
            <EmptyState title="Sin RCON configurado" description="Configura RCON en al menos un servidor para consultar jugadores desde el dashboard." />
          )}
        </CardContent>
      </Card>

      <Card className="mt-5">
        <CardHeader>
          <CardTitle>Servidores en vivo</CardTitle>
          <CardDescription>Jugadores activos desde el registro de presencia del backend.</CardDescription>
        </CardHeader>
        <CardContent>
          <DataTable
            data={data.servers}
            columns={serverColumns}
            getRowKey={(server) => server.id}
            searchPlaceholder="Filtrar servidores"
          />
        </CardContent>
      </Card>

      <Card className="mt-5">
        <CardHeader>
          <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
            <CardTitle>Actividad reciente</CardTitle>
            <select
              className="focus-ring h-9 rounded-md border border-input bg-background px-2 text-sm"
              value={activitySize}
              onChange={(event) => {
                setActivityPage(0);
                setActivitySize(Number(event.target.value));
              }}
            >
              <option value={10}>10 lineas</option>
              <option value={50}>50 lineas</option>
              <option value={100}>100 lineas</option>
            </select>
          </div>
        </CardHeader>
        <CardContent>
          <DataTable
            data={data.recentActivity}
            columns={activityColumns}
            getRowKey={(item, index) => `${item.startedAt}-${index}`}
            searchPlaceholder="Filtrar actividad"
          />
          {data.recentActivity.length === 0 ? <EmptyState title="Sin actividad" description="Todavia no hay acciones registradas." /> : null}
          <div className="mt-4 flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
            <div className="text-sm text-muted-foreground">Pagina {data.page.page + 1} de {Math.max(data.page.totalPages, 1)}</div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={activityPage === 0} onClick={() => setActivityPage((value) => Math.max(0, value - 1))}>Anterior</Button>
              <Button variant="outline" size="sm" disabled={activityPage + 1 >= data.page.totalPages} onClick={() => setActivityPage((value) => value + 1)}>Siguiente</Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
