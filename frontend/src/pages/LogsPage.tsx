import * as React from "react";
import { Copy, RefreshCw, Terminal } from "lucide-react";
import { api } from "../lib/api";
import type { InternalServerLog, ServerLogsView, ServerView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { ServerSelect } from "../components/ServerSelect";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";
import { formatDateTime } from "../lib/utils";

export function LogsPage({
  selectedServerId,
  onSelectServer
}: {
  selectedServerId: number | null;
  onSelectServer: (id: number) => void;
}) {
  const [servers, setServers] = React.useState<ServerView[]>([]);
  const [lines, setLines] = React.useState(200);
  const [logs, setLogs] = React.useState<ServerLogsView | null>(null);
  const [loading, setLoading] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    api.servers().then((items) => {
      setServers(items);
      if (!selectedServerId && items[0]) {
        onSelectServer(items[0].id);
      }
    }).catch((error) => toast({ title: "No se pudieron cargar servidores", description: error.message, variant: "error" }));
  }, [onSelectServer, selectedServerId, toast]);

  const currentId = selectedServerId ?? servers[0]?.id ?? null;

  const loadLogs = React.useCallback(async () => {
    if (!currentId) return;
    setLoading(true);
    try {
      setLogs(await api.serverLogs(currentId, lines));
    } catch (error) {
      toast({ title: "No se pudieron cargar logs", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setLoading(false);
    }
  }, [currentId, lines, toast]);

  React.useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const serverOutput = logs?.combinedOutput?.trim() || "";
  const internalLogColumns: DataTableColumn<InternalServerLog>[] = [
    { key: "started", header: "Inicio", sortable: true, searchValue: (item) => formatDateTime(item.startedAt), sortValue: (item) => item.startedAt || "", render: (item) => formatDateTime(item.startedAt) },
    { key: "finished", header: "Fin", sortable: true, searchValue: (item) => formatDateTime(item.finishedAt), sortValue: (item) => item.finishedAt || "", render: (item) => formatDateTime(item.finishedAt) },
    { key: "action", header: "Accion", sortable: true, searchValue: (item) => item.action || "", render: (item) => item.action || "-" },
    { key: "status", header: "Estado", sortable: true, searchValue: (item) => item.status || "", render: (item) => item.status || "-" },
    { key: "user", header: "Usuario", sortable: true, searchValue: (item) => item.username || "", render: (item) => item.username || "-" },
    { key: "message", header: "Mensaje", sortable: true, searchValue: (item) => item.error || item.message || "", render: (item) => item.error || item.message || "-" }
  ];

  return (
    <div>
      <SectionHeader title="Logs del servidor" description="Consulta la salida del servicio/contenedor y las acciones internas registradas por el panel." />
      <div className="mb-4 grid gap-3 lg:grid-cols-[1fr_160px_auto]">
        <ServerSelect servers={servers} value={currentId} onChange={onSelectServer} />
        <select className="focus-ring h-10 rounded-md border border-input bg-background px-3 text-sm" value={lines} onChange={(event) => setLines(Number(event.target.value))}>
          <option value={100}>100 lineas</option>
          <option value={200}>200 lineas</option>
          <option value={500}>500 lineas</option>
          <option value={1000}>1000 lineas</option>
        </select>
        <Button onClick={loadLogs} disabled={loading || !currentId}>
          <RefreshCw className="h-4 w-4" />
          {loading ? "Cargando..." : "Refrescar"}
        </Button>
      </div>

      <div className="space-y-4">
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <CardTitle>{logs ? `Servidor: ${logs.serverName}` : "Servidor"}</CardTitle>
                <CardDescription>Ultimas {logs?.lines ?? lines} lineas solicitadas al backend.</CardDescription>
              </div>
              <Button variant="outline" size="sm" disabled={!serverOutput} onClick={() => navigator.clipboard.writeText(serverOutput)}>
                <Copy className="h-3.5 w-3.5" />
                Copiar
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            {loading && !logs ? (
              <Skeleton className="h-72" />
            ) : serverOutput ? (
              <pre className="max-h-[32rem] overflow-auto rounded-md border border-border bg-slate-950 p-4 font-mono text-xs leading-relaxed text-slate-100 scrollbar-soft">{serverOutput}</pre>
            ) : (
              <EmptyState title="Sin salida" description="No hay logs disponibles para este servidor o el comando no devolvio contenido." />
            )}
            {logs && !logs.success && logs.error ? (
              <div className="mt-3 rounded-md border border-rose-400/30 bg-rose-400/10 p-3 text-sm text-rose-200">{logs.error}</div>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Actividad interna</CardTitle>
            <CardDescription>Acciones ejecutadas desde el panel para este servidor.</CardDescription>
          </CardHeader>
          <CardContent>
            {!logs ? (
              <Skeleton className="h-40" />
            ) : logs.internalLogs.length === 0 ? (
              <EmptyState title="Sin actividad" description="Todavia no hay acciones internas para este servidor." />
            ) : (
              <DataTable data={logs.internalLogs} columns={internalLogColumns} getRowKey={(item, index) => `${item.startedAt}-${index}`} searchPlaceholder="Filtrar actividad interna" />
            )}
          </CardContent>
        </Card>

        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Terminal className="h-4 w-4" />
          Para systemd se usa journalctl; para Docker se usa docker logs.
        </div>
      </div>
    </div>
  );
}
