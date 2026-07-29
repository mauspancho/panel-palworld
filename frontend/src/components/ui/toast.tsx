import * as React from "react";
import { CheckCircle2, XCircle } from "lucide-react";
import { cn } from "../../lib/utils";

type Toast = {
  id: number;
  title: string;
  description?: string;
  variant?: "success" | "error";
};

type ToastContextValue = {
  toast: (toast: Omit<Toast, "id">) => void;
};

const ToastContext = React.createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = React.useState<Toast[]>([]);

  const toast = React.useCallback((item: Omit<Toast, "id">) => {
    const id = Date.now();
    setItems((current) => [...current, { ...item, id }]);
    window.setTimeout(() => {
      setItems((current) => current.filter((toastItem) => toastItem.id !== id));
    }, 4200);
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex w-[min(420px,calc(100vw-2rem))] flex-col gap-2">
        {items.map((item) => (
          <div
            key={item.id}
            className={cn(
              "rounded-lg border bg-card/95 p-4 text-sm text-card-foreground shadow-panel backdrop-blur",
              item.variant === "error" ? "border-rose-400/30" : "border-cyan-300/20"
            )}
          >
            <div className="flex gap-3">
              {item.variant === "error" ? (
                <XCircle className="mt-0.5 h-4 w-4 text-rose-300" />
              ) : (
                <CheckCircle2 className="mt-0.5 h-4 w-4 text-emerald-300" />
              )}
              <div>
                <div className="font-medium">{item.title}</div>
                {item.description ? <div className="mt-1 text-muted-foreground">{item.description}</div> : null}
              </div>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const value = React.useContext(ToastContext);
  if (!value) {
    throw new Error("useToast debe usarse dentro de ToastProvider.");
  }
  return value;
}
