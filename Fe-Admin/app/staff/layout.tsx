"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AdminLayout } from "@/components/admin-layout";
import { Toaster } from "@/components/ui/sonner";
import { getUser, logout } from "@/lib/auth";

export default function StaffLayout({ children }: { children: React.ReactNode }) {
    const router = useRouter();

    useEffect(() => {
        const user = getUser();
        if (!user || user.roleName?.toUpperCase() !== "ADMIN") {
            // Non-admin reached staff page — force logout for security
            logout();
            router.push("/login");
        }
    }, [router]);

    return (
        <AdminLayout>
            {children}
            <Toaster position="top-right" richColors />
        </AdminLayout>
    );
}
