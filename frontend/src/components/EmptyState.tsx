import { Info } from "lucide-react";

export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex min-h-36 items-center justify-center rounded-lg border border-dashed border-border bg-muted/40 p-6 text-center">
      <div>
        <Info className="mx-auto h-5 w-5 text-cyan-200" />
        <div className="mt-3 font-medium">{title}</div>
        <div className="mt-1 text-sm text-muted-foreground">{description}</div>
      </div>
    </div>
  );
}
