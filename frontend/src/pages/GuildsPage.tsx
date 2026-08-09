import * as React from "react";
import { Crown, Home, RefreshCw, Users } from "lucide-react";
import { api } from "../lib/api";
import type { GuildMemberView, GuildServerView, GuildView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";
import { cn } from "../lib/utils";

type GuildRow = {
  key: string;
  serverId: number;
  serverName: string;
  guild: GuildView;
};

const guildColumns: DataTableColumn<GuildRow>[] = [
  {
    key: "name",
    header: "Gremio",
    sortable: true,
    searchValue: (row) => row.guild.name,
    render: (row) => <span className="font-medium text-primary">{row.guild.name || "Sin nombre"}</span>
  },
  {
    key: "server",
    header: "Servidor",
    sortable: true,
    searchValue: (row) => row.serverName,
    render: (row) => <span className="text-muted-foreground">{row.serverName}</span>
  },
  {
    key: "leader",
    header: "Lider",
    sortable: true,
    searchValue: (row) => row.guild.leaderName,
    render: (row) => row.guild.leaderName ? <span>{row.guild.leaderName}</span> : <span className="text-muted-foreground">-</span>
  },
  {
    key: "bases",
    header: "Bases",
    sortable: true,
    sortValue: (row) => row.guild.bases,
    searchValue: (row) => row.guild.bases,
    render: (row) => <span className="font-semibold">{row.guild.bases}</span>
  },
  {
    key: "members",
    header: "Jugadores",
    sortable: true,
    sortValue: (row) => row.guild.memberCount || row.guild.members.length,
    searchValue: (row) => row.guild.members.map((member) => member.name).join(" "),
    render: (row) => <span>{row.guild.memberCount || row.guild.members.length}</span>
  }
];

const memberColumns: DataTableColumn<GuildMemberView>[] = [
  {
    key: "name",
    header: "Jugador",
    sortable: true,
    searchValue: (member) => member.name,
    render: (member) => (
      <div className="flex items-center gap-2">
        <span className="font-medium">{member.name || "Sin nombre"}</span>
        {member.leader ? <Badge variant="success">Lider</Badge> : null}
      </div>
    )
  },
  {
    key: "uid",
    header: "UID",
    sortable: true,
    searchValue: (member) => member.uid,
    render: (member) => <span className="font-mono text-xs text-muted-foreground">{member.uid || "-"}</span>
  }
];

export function GuildsPage() {
  const [data, setData] = React.useState<GuildServerView[] | null>(null);
  const [serverFilter, setServerFilter] = React.useState<number | "all">("all");
  const [selectedKey, setSelectedKey] = React.useState<string | null>(null);
  const { toast } = useToast();

  const load = React.useCallback(async () => {
    const next = await api.guilds();
    setData(next);
    setSelectedKey((current) => {
      const rows = flattenGuilds(next, serverFilter);
      return current && rows.some((row) => row.key === current) ? current : rows[0]?.key ?? null;
    });
  }, [serverFilter]);

  React.useEffect(() => {
    load().catch((error) => toast({ title: "No se pudieron cargar gremios", description: error.message, variant: "error" }));
  }, [load, toast]);

  const rows = React.useMemo(() => data ? flattenGuilds(data, serverFilter) : [], [data, serverFilter]);
  const selected = rows.find((row) => row.key === selectedKey) ?? rows[0] ?? null;
  const unavailable = data?.filter((server) => !server.available) ?? [];

  React.useEffect(() => {
    if (!selectedKey && rows.length > 0) {
      setSelectedKey(rows[0].key);
    }
  }, [rows, selectedKey]);

  const totals = React.useMemo(() => {
    return rows.reduce(
      (acc, row) => ({
        guilds: acc.guilds + 1,
        bases: acc.bases + row.guild.bases,
        members: acc.members + (row.guild.memberCount || row.guild.members.length)
      }),
      { guilds: 0, bases: 0, members: 0 }
    );
  }, [rows]);

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <SectionHeader title="Gremios" description="Gremios, bases y jugadores detectados desde world-stats.json por servidor." />
        <Button variant="outline" onClick={() => load().catch((error) => toast({ title: "No se pudieron cargar gremios", description: error.message, variant: "error" }))}>
          <RefreshCw className="h-4 w-4" />
          Refrescar
        </Button>
      </div>

      <div className="mb-4 grid gap-4 md:grid-cols-3">
        <SummaryCard icon={Crown} label="Gremios" value={totals.guilds} />
        <SummaryCard icon={Home} label="Bases" value={totals.bases} />
        <SummaryCard icon={Users} label="Jugadores en gremios" value={totals.members} />
      </div>

      <Card className="mb-4">
        <CardContent className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="text-sm font-medium">Servidor</div>
            <div className="text-sm text-muted-foreground">Filtra los gremios por servidor registrado.</div>
          </div>
          <select
            className="focus-ring h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground sm:max-w-sm"
            value={serverFilter}
            onChange={(event) => {
              const value = event.target.value === "all" ? "all" : Number(event.target.value);
              setServerFilter(value);
              setSelectedKey(null);
            }}
          >
            <option value="all">Todos los servidores</option>
            {(data ?? []).map((server) => (
              <option key={server.serverId} value={server.serverId}>{server.serverName}</option>
            ))}
          </select>
        </CardContent>
      </Card>

      {unavailable.length > 0 ? (
        <Card className="mb-4 border-amber-400/30 bg-amber-400/10">
          <CardContent className="space-y-1 p-5 text-sm text-amber-100">
            {unavailable.map((server) => (
              <div key={server.serverId}>{server.serverName}: {server.message}</div>
            ))}
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(360px,0.85fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Listado de gremios</CardTitle>
            <CardDescription>Selecciona un gremio para ver sus jugadores.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data ? (
              <div className="space-y-3"><Skeleton className="h-12" /><Skeleton className="h-12" /><Skeleton className="h-12" /></div>
            ) : rows.length === 0 ? (
              <EmptyState title="Sin gremios" description="No hay gremios disponibles en el JSON configurado." />
            ) : (
              <DataTable
                data={rows}
                columns={guildColumns}
                getRowKey={(row) => row.key}
                searchPlaceholder="Filtrar gremios"
                minWidth="760px"
                onRowClick={(row) => setSelectedKey(row.key)}
                rowClassName={(row) => cn(selected?.key === row.key && "bg-primary/10")}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{selected?.guild.name || "Jugadores"}</CardTitle>
            <CardDescription>
              {selected ? `${selected.serverName} - ${selected.guild.bases} base(s)` : "Selecciona un gremio para ver miembros."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {!selected ? (
              <EmptyState title="Sin seleccion" description="Elige un gremio de la tabla." />
            ) : selected.guild.members.length === 0 ? (
              <EmptyState title="Sin jugadores" description="El gremio no trae miembros en el JSON." />
            ) : (
              <DataTable
                data={selected.guild.members}
                columns={memberColumns}
                getRowKey={(member, index) => member.uid || `${member.name}-${index}`}
                searchPlaceholder="Filtrar jugadores"
                minWidth="420px"
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function SummaryCard({ icon: Icon, label, value }: { icon: React.ComponentType<{ className?: string }>; label: string; value: number }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between p-5">
        <div>
          <div className="text-sm text-muted-foreground">{label}</div>
          <div className="mt-2 text-3xl font-semibold tracking-normal">{value}</div>
        </div>
        <div className="flex h-12 w-12 items-center justify-center rounded-md bg-primary/10 text-primary ring-1 ring-primary/25">
          <Icon className="h-5 w-5" />
        </div>
      </CardContent>
    </Card>
  );
}

function flattenGuilds(data: GuildServerView[], serverFilter: number | "all") {
  return data
    .filter((server) => serverFilter === "all" || server.serverId === serverFilter)
    .flatMap((server) =>
      server.guilds.map((guild, index) => ({
        key: `${server.serverId}-${guild.id || guild.name || index}`,
        serverId: server.serverId,
        serverName: server.serverName,
        guild
      }))
    );
}
