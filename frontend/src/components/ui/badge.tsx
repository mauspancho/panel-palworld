import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";
import { cn } from "../../lib/utils";

const badgeVariants = cva("inline-flex items-center rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset", {
  variants: {
    variant: {
      default: "bg-cyan-400/10 text-cyan-700 ring-cyan-400/25 dark:text-cyan-200 dark:ring-cyan-300/25",
      success: "bg-emerald-400/10 text-emerald-700 ring-emerald-400/25 dark:text-emerald-200 dark:ring-emerald-300/25",
      warning: "bg-amber-400/10 text-amber-700 ring-amber-400/25 dark:text-amber-200 dark:ring-amber-300/25",
      danger: "bg-rose-400/10 text-rose-700 ring-rose-400/25 dark:text-rose-200 dark:ring-rose-300/25",
      muted: "bg-muted text-muted-foreground ring-border"
    }
  },
  defaultVariants: {
    variant: "default"
  }
});

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}
