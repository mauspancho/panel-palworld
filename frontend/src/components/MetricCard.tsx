import { type LucideIcon } from "lucide-react";
import { Card, CardContent } from "./ui/card";

type MetricCardProps = {
  title: string;
  value: string | number;
  detail?: string;
  icon: LucideIcon;
};

export function MetricCard({ title, value, detail, icon: Icon }: MetricCardProps) {
  return (
    <Card className="overflow-hidden">
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-sm text-muted-foreground">{title}</div>
            <div className="mt-2 text-3xl font-semibold tracking-normal">{value}</div>
            {detail ? <div className="mt-1 text-sm text-muted-foreground">{detail}</div> : null}
          </div>
          <div className="flex h-11 w-11 items-center justify-center rounded-md bg-cyan-400/10 text-cyan-200 ring-1 ring-cyan-300/20">
            <Icon className="h-5 w-5" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
