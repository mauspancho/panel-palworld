import * as React from "react";
import { ArrowDown, ArrowUp, ArrowUpDown, Search } from "lucide-react";
import { cn } from "../lib/utils";

export type DataTableColumn<T> = {
  key: string;
  header: string;
  className?: string;
  sortable?: boolean;
  render: (item: T) => React.ReactNode;
  sortValue?: (item: T) => string | number | boolean | null | undefined;
  searchValue?: (item: T) => string | number | boolean | null | undefined;
};

type SortState = {
  key: string;
  direction: "asc" | "desc";
};

export function DataTable<T>({
  data,
  columns,
  getRowKey,
  searchPlaceholder = "Filtrar",
  className,
  minWidth,
  rowClassName,
  onRowClick
}: {
  data: T[];
  columns: DataTableColumn<T>[];
  getRowKey: (item: T, index: number) => React.Key;
  searchPlaceholder?: string;
  className?: string;
  minWidth?: string;
  rowClassName?: (item: T) => string;
  onRowClick?: (item: T) => void;
}) {
  const [query, setQuery] = React.useState("");
  const [sort, setSort] = React.useState<SortState | null>(null);

  const visibleRows = React.useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const filtered = normalizedQuery
      ? data.filter((item) =>
          columns.some((column) => {
            const rawValue = column.searchValue ? column.searchValue(item) : column.sortValue ? column.sortValue(item) : "";
            return String(rawValue ?? "").toLowerCase().includes(normalizedQuery);
          })
        )
      : [...data];

    if (!sort) {
      return filtered;
    }
    const column = columns.find((item) => item.key === sort.key);
    if (!column) {
      return filtered;
    }
    return filtered.sort((left, right) => {
      const leftValue = valueForSort(column, left);
      const rightValue = valueForSort(column, right);
      const result = compareValues(leftValue, rightValue);
      return sort.direction === "asc" ? result : -result;
    });
  }, [columns, data, query, sort]);

  function toggleSort(column: DataTableColumn<T>) {
    if (!column.sortable) {
      return;
    }
    setSort((current) => {
      if (!current || current.key !== column.key) {
        return { key: column.key, direction: "asc" };
      }
      if (current.direction === "asc") {
        return { key: column.key, direction: "desc" };
      }
      return null;
    });
  }

  return (
    <div className={cn("space-y-3", className)}>
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <input
            className="focus-ring h-9 w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={searchPlaceholder}
          />
        </div>
        <div className="text-sm text-muted-foreground">{visibleRows.length} de {data.length}</div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm" style={minWidth ? { minWidth } : undefined}>
          <thead className="text-left text-muted-foreground">
            <tr>
              {columns.map((column) => {
                const sorted = sort?.key === column.key ? sort.direction : null;
                return (
                  <th key={column.key} className={cn("pb-3", column.className)}>
                    {column.sortable ? (
                      <button className="inline-flex items-center gap-1 text-left hover:text-foreground" type="button" onClick={() => toggleSort(column)}>
                        {column.header}
                        {sorted === "asc" ? <ArrowUp className="h-3.5 w-3.5" /> : sorted === "desc" ? <ArrowDown className="h-3.5 w-3.5" /> : <ArrowUpDown className="h-3.5 w-3.5" />}
                      </button>
                    ) : (
                      column.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {visibleRows.map((item, index) => (
              <tr key={getRowKey(item, index)} className={cn(onRowClick && "cursor-pointer hover:bg-accent/60", rowClassName?.(item))} onClick={() => onRowClick?.(item)}>
                {columns.map((column) => (
                  <td key={column.key} className={cn("py-3", column.className)}>
                    {column.render(item)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function valueForSort<T>(column: DataTableColumn<T>, item: T) {
  return column.sortValue ? column.sortValue(item) : column.searchValue ? column.searchValue(item) : "";
}

function compareValues(left: string | number | boolean | null | undefined, right: string | number | boolean | null | undefined) {
  if (typeof left === "number" && typeof right === "number") {
    return left - right;
  }
  return String(left ?? "").localeCompare(String(right ?? ""), "es", { numeric: true, sensitivity: "base" });
}
