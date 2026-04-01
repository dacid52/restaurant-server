"use client";

import { AdminLayout } from "@/components/admin-layout";
import { Toaster } from "@/components/ui/toaster";

export default function KitchenLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AdminLayout>
      {children}
      <Toaster />
    </AdminLayout>
  );
}
