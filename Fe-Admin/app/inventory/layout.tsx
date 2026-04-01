"use client";

import { AdminLayout } from "@/components/admin-layout";
import { Toaster } from "@/components/ui/sonner";

export default function InventoryLayout({
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
