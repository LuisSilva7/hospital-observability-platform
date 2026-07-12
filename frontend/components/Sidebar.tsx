"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const navItems = [
  { href: "/", label: "Dashboard" },
  { href: "/services", label: "Serviços" },
  { href: "/logs", label: "Logs" },
  { href: "/rules", label: "Regras" },
  { href: "/alerts", label: "Alertas" },
  { href: "/automations", label: "Automações" },
  { href: "/settings", label: "Configuração" },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-64 shrink-0 border-r border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
      <div className="border-b border-gray-200 p-4 dark:border-gray-800">
        <h1 className="text-sm font-bold leading-tight">
          Hospital Observability
        </h1>
        <p className="mt-1 text-xs text-gray-500">
          Monitorização de fluxos de dados
        </p>
      </div>
      <nav className="p-2">
        {navItems.map((item) => {
          const active =
            item.href === "/"
              ? pathname === "/"
              : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`block rounded-md px-3 py-2 text-sm ${
                active
                  ? "bg-blue-50 font-medium text-blue-700 dark:bg-blue-950 dark:text-blue-300"
                  : "text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
              }`}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
