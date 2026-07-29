import * as React from "react";
import { ClipboardList, FileJson, FileText, Play, Radio, RotateCcw, Settings, Square, Trash2, Wrench } from "lucide-react";
import { api } from "../lib/api";
import type { ServerView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { StatusBadge } from "../components/StatusBadge";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { ConfirmDialog } from "../components/ui/confirm-dialog";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";

type PendingAction = {
  server: ServerView;
  action: "stop" | "restart" | "install" | "delete";
};

const serverColumns = (
  busy: number | null,
  isAdmin: boolean,
  runAction: (server: ServerView, action: "start" | "stop" | "restart" | "update" | "install" | "delete") => void,
  setPending: (pending: PendingAction) => void,
  onOpenRcon: (id: number) => void,
  onOpenLogs: (id: number) => void,
  onOpenProfiles: (id: number) => void,
  openConfig: (server: ServerView) => void
): DataTableColumn<ServerView>[] => [
  { key: "name", header: "Nombre", sortable: true, searchValue: (server) => server.name, render: (server) => <span className="font-medium">{server.name}</span> },
  { key: "type", header: "Gestor", sortable: true, searchValue: (server) => server.type, render: (server) => <span className="text-muted-foreground">{server.type}</span> },
  { key: "identifier", header: "Identificador", sortable: true, searchValue: (server) => server.serviceName || server.containerName || "", render: (server) => <span className="text-muted-foreground">{server.serviceName || server.containerName || "-"}</span> },
  { key: "rootPath", header: "Ruta", sortable: true, searchValue: (server) => server.rootPath, render: (server) => <span className="text-muted-foreground">{server.rootPath}</span> },
  { key: "status", header: "Estado", sortable: true, searchValue: (server) => server.statusLabel, render: (server) => <StatusBadge status={server.status} label={server.statusLabel} /> },
  {
    key: "actions",
    header: "Acciones",
    className: "text-right",
    render: (server) => (
      <div className="flex flex-wrap justify-end gap-2">
        <Button size="sm" variant="outline" disabled={busy === server.id} onClick={() => runAction(server, "start")}><Play className="h-3.5 w-3.5" />Iniciar</Button>
        <Button size="sm" variant="outline" disabled={busy === server.id} onClick={() => setPending({ server, action: "stop" })}><Square className="h-3.5 w-3.5" />Detener</Button>
        <Button size="sm" variant="outline" disabled={busy === server.id} onClick={() => setPending({ server, action: "restart" })}><RotateCcw className="h-3.5 w-3.5" />Reiniciar</Button>
        <Button size="sm" variant="outline" disabled={busy === server.id} onClick={() => runAction(server, "update")}><Wrench className="h-3.5 w-3.5" />Actualizar</Button>
        {isAdmin ? <Button size="sm" variant="outline" disabled={busy === server.id} onClick={() => setPending({ server, action: "install" })}><ClipboardList className="h-3.5 w-3.5" />Instalar</Button> : null}
        <Button size="sm" variant="outline" onClick={() => onOpenRcon(server.id)}><Radio className="h-3.5 w-3.5" />RCON</Button>
        <Button size="sm" variant="outline" onClick={() => onOpenLogs(server.id)}><FileText className="h-3.5 w-3.5" />Logs</Button>
        {isAdmin ? <Button size="sm" variant="outline" onClick={() => onOpenProfiles(server.id)}><FileJson className="h-3.5 w-3.5" />Perfiles</Button> : null}
        {isAdmin ? <Button size="sm" variant="outline" onClick={() => openConfig(server)}><Settings className="h-3.5 w-3.5" />Configuracion</Button> : null}
        {isAdmin ? <Button size="sm" variant="destructive" disabled={busy === server.id} onClick={() => setPending({ server, action: "delete" })}><Trash2 className="h-3.5 w-3.5" />Eliminar</Button> : null}
      </div>
    )
  }
];

export function ServersPage({
  onOpenRcon,
  onOpenLogs,
  onOpenProfiles,
  isAdmin
}: {
  onOpenRcon: (id: number) => void;
  onOpenLogs: (id: number) => void;
  onOpenProfiles: (id: number) => void;
  isAdmin: boolean;
}) {
  const [servers, setServers] = React.useState<ServerView[] | null>(null);
  const [pending, setPending] = React.useState<PendingAction | null>(null);
  const [busy, setBusy] = React.useState<number | null>(null);
  const { toast } = useToast();

  const load = React.useCallback(() => api.servers().then(setServers), []);

  React.useEffect(() => {
    load().catch((error) => toast({ title: "No se pudieron cargar servidores", description: error.message, variant: "error" }));
  }, [load, toast]);

  const runAction = async (server: ServerView, action: "start" | "stop" | "restart" | "update" | "install" | "delete") => {
    setBusy(server.id);
    try {
      const result = action === "delete" ? await api.deleteServer(server.id) : await api.serverAction(server.id, action);
      if (!result.success) throw new Error(result.error || result.message);
      toast({ title: "Accion ejecutada", description: `${server.name}: ${result.message}` });
      await load();
    } catch (error) {
      toast({ title: "La accion fallo", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(null);
      setPending(null);
    }
  };

  const openConfig = (server: ServerView) => {
    window.location.href = `/servers/${server.id}/config`;
  };

  return (
    <div>
      <SectionHeader title="Servidores" description="Control de servidores registrados, con acciones reales ejecutadas por el backend." />
      <Card>
        <CardContent className="p-0">
          {!servers ? (
            <div className="space-y-3 p-5"><Skeleton className="h-12" /><Skeleton className="h-12" /><Skeleton className="h-12" /></div>
          ) : servers.length === 0 ? (
            <div className="p-5"><EmptyState title="Sin servidores" description="Todavia no hay servidores registrados en el panel." /></div>
          ) : (
            <div className="p-5">
              <DataTable
                data={servers}
                columns={serverColumns(busy, isAdmin, runAction, setPending, onOpenRcon, onOpenLogs, onOpenProfiles, openConfig)}
                getRowKey={(server) => server.id}
                searchPlaceholder="Filtrar servidores"
                minWidth="980px"
              />
            </div>
          )}
        </CardContent>
      </Card>
      <ConfirmDialog
        open={Boolean(pending)}
        busy={pending ? busy === pending.server.id : false}
        title="Confirmar accion"
        description={pending ? `Se ejecutara ${pending.action} sobre ${pending.server.name}.` : ""}
        confirmLabel="Confirmar"
        onCancel={() => setPending(null)}
        onConfirm={() => pending && runAction(pending.server, pending.action)}
      />
    </div>
  );
}
