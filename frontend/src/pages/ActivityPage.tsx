import * as React from "react";
import { api } from "../lib/api";
import type { ActivityItem, AuditItem, PagedActivity, PagedAudit } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Skeleton } from "../components/ui/skeleton";
import { formatDateTime } from "../lib/utils";

export function ActivityPage({ isAdmin = false }: { isAdmin?: boolean }) {
  const [size, setSize] = React.useState(10);
  const [page, setPage] = React.useState(0);
  const [data, setData] = React.useState<PagedActivity | null>(null);
  const [audit, setAudit] = React.useState<PagedAudit | null>(null);
  const [auditPage, setAuditPage] = React.useState(0);
  const [auditSize, setAuditSize] = React.useState(10);

  React.useEffect(() => {
    api.activity(page, size).then(setData);
  }, [page, size]);

  React.useEffect(() => {
    if (isAdmin) {
      api.audit(auditPage, auditSize).then(setAudit);
    }
  }, [isAdmin, auditPage, auditSize]);

  const activityColumns: DataTableColumn<ActivityItem>[] = [
    { key: "date", header: "Fecha", sortable: true, searchValue: (item) => formatDateTime(item.startedAt), sortValue: (item) => item.startedAt || "", render: (item) => formatDateTime(item.startedAt) },
    { key: "server", header: "Servidor", sortable: true, searchValue: (item) => item.serverName || "Servidor eliminado", render: (item) => item.serverName || "Servidor eliminado" },
    { key: "action", header: "Accion", sortable: true, searchValue: (item) => item.action || "", render: (item) => item.action || "-" },
    { key: "status", header: "Estado", sortable: true, searchValue: (item) => item.status || "", render: (item) => item.status || "-" },
    { key: "user", header: "Usuario", sortable: true, searchValue: (item) => item.username || "", render: (item) => item.username || "-" }
  ];
  const auditColumns: DataTableColumn<AuditItem>[] = [
    { key: "date", header: "Fecha", sortable: true, searchValue: (item) => formatDateTime(item.createdAt), sortValue: (item) => item.createdAt || "", render: (item) => formatDateTime(item.createdAt) },
    { key: "actor", header: "Actor", sortable: true, searchValue: (item) => item.actorUsername || "", render: (item) => item.actorUsername || "-" },
    { key: "target", header: "Afectado", sortable: true, searchValue: (item) => item.targetUsername || "", render: (item) => item.targetUsername || "-" },
    { key: "action", header: "Accion", sortable: true, searchValue: (item) => item.action || "", render: (item) => item.action || "-" },
    { key: "status", header: "Estado", sortable: true, searchValue: (item) => item.status || "", render: (item) => item.status || "-" },
    { key: "detail", header: "Detalle", sortable: true, searchValue: (item) => item.description || "", render: (item) => item.description || "-" }
  ];

  return (
    <div className="space-y-4">
      <SectionHeader title="Historial y auditoria" description="Actividad registrada por el backend con paginacion real." />
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-3">
            <CardTitle>Actividad reciente</CardTitle>
            <select className="focus-ring h-9 rounded-md border border-input bg-background px-2 text-sm" value={size} onChange={(event) => { setPage(0); setSize(Number(event.target.value)); }}>
              <option value={10}>10 lineas</option>
              <option value={50}>50 lineas</option>
              <option value={100}>100 lineas</option>
            </select>
          </div>
        </CardHeader>
        <CardContent>
          {!data ? <Skeleton className="h-40" /> : data.items.length === 0 ? <EmptyState title="Sin actividad" description="Todavia no hay registros." /> : (
            <DataTable data={data.items} columns={activityColumns} getRowKey={(item, index) => `${item.startedAt}-${index}`} searchPlaceholder="Filtrar actividad" />
          )}
          {data ? (
            <div className="mt-4 flex items-center justify-between">
              <div className="text-sm text-muted-foreground">Pagina {data.page.page + 1} de {Math.max(data.page.totalPages, 1)}</div>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>Anterior</Button>
                <Button variant="outline" size="sm" disabled={page + 1 >= data.page.totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
      {isAdmin ? (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between gap-3">
              <CardTitle>Auditoria de usuarios</CardTitle>
              <select className="focus-ring h-9 rounded-md border border-input bg-background px-2 text-sm" value={auditSize} onChange={(event) => { setAuditPage(0); setAuditSize(Number(event.target.value)); }}>
                <option value={10}>10 lineas</option>
                <option value={50}>50 lineas</option>
                <option value={100}>100 lineas</option>
              </select>
            </div>
          </CardHeader>
          <CardContent>
            {!audit ? <Skeleton className="h-40" /> : audit.items.length === 0 ? <EmptyState title="Sin auditoria" description="Todavia no hay eventos de usuario." /> : (
              <DataTable data={audit.items} columns={auditColumns} getRowKey={(item, index) => `${item.createdAt}-${index}`} searchPlaceholder="Filtrar auditoria" />
            )}
            {audit ? (
              <div className="mt-4 flex items-center justify-between">
                <div className="text-sm text-muted-foreground">Pagina {audit.page.page + 1} de {Math.max(audit.page.totalPages, 1)}</div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={auditPage === 0} onClick={() => setAuditPage((value) => Math.max(0, value - 1))}>Anterior</Button>
                  <Button variant="outline" size="sm" disabled={auditPage + 1 >= audit.page.totalPages} onClick={() => setAuditPage((value) => value + 1)}>Siguiente</Button>
                </div>
              </div>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
