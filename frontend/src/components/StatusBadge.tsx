import { Badge } from "./ui/badge";
import type { ServerStatus } from "../types";

export function StatusBadge({ status, label }: { status: ServerStatus | string | null; label?: string | null }) {
  if (status === "RUNNING") return <Badge variant="success">{label || "Encendido"}</Badge>;
  if (status === "STOPPED") return <Badge variant="muted">{label || "Detenido"}</Badge>;
  if (status === "ERROR") return <Badge variant="danger">{label || "Error"}</Badge>;
  if (status === "RESTARTING") return <Badge variant="warning">{label || "Reiniciando"}</Badge>;
  return <Badge variant="warning">{label || "Desconocido"}</Badge>;
}
