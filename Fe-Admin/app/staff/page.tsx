"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import {
    Search,
    Plus,
    Trash2,
    Pencil,
    RefreshCw,
    Users,
    UserCog,
    Shield,
    Phone,
    Mail,
    MapPin,
    Calendar,
    Eye,
    EyeOff,
    KeyRound,
    ChefHat,
    CreditCard,
    Ban,
    CheckCircle,
    UserX,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Label } from "@/components/ui/label";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import api from "@/lib/axios";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";

// ─── Types ───────────────────────────────────────────────────────────────────
interface Role {
    id: number;
    name: string;
}

interface User {
    id: number;
    username: string;
    role_id: number;
    role_name: string;
    full_name: string;
    phone_number: string;
    age: number;
    email: string;
    address: string;
    email_verified: boolean;
    avatar_url: string;
    banned: boolean;
    ban_reason: string;
    banned_at: string;
    created_at: string;
    updated_at: string;
}

// Roles shown in the admin panel (exclude CUSTOMER)
const STAFF_ROLE_NAMES = ["ADMIN", "MANAGER", "CASHIER", "KITCHEN", "STAFF"];

const normalizeUser = (raw: Record<string, unknown>): User => ({
    id: raw.id as number,
    username: (raw.username as string) || "",
    role_id: (raw.role_id ?? raw.roleId ?? 0) as number,
    role_name: ((raw.role_name ?? raw.roleName ?? "UNKNOWN") as string),
    full_name: ((raw.full_name ?? raw.fullName ?? "") as string),
    phone_number: ((raw.phone_number ?? raw.phoneNumber ?? "") as string),
    age: (raw.age ?? 0) as number,
    email: (raw.email ?? "") as string,
    address: (raw.address ?? "") as string,
    email_verified: (raw.email_verified ?? raw.emailVerified ?? false) as boolean,
    avatar_url: ((raw.avatar_url ?? raw.avatarUrl ?? "") as string),
    banned: (raw.banned ?? false) as boolean,
    ban_reason: ((raw.ban_reason ?? raw.banReason ?? "") as string),
    banned_at: ((raw.banned_at ?? raw.bannedAt ?? "") as string),
    created_at: ((raw.created_at ?? raw.createdAt ?? "") as string),
    updated_at: ((raw.updated_at ?? raw.updatedAt ?? "") as string),
});

const formatDate = (dateString: string): string => {
    if (!dateString) return "—";
    return new Date(dateString).toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
    });
};

const ROLE_COLORS: Record<string, string> = {
    ADMIN:    "bg-red-500/10 text-red-600 border-red-500/20",
    MANAGER:  "bg-purple-500/10 text-purple-600 border-purple-500/20",
    CASHIER:  "bg-green-500/10 text-green-600 border-green-500/20",
    KITCHEN:  "bg-orange-500/10 text-orange-600 border-orange-500/20",
    STAFF:    "bg-blue-500/10 text-blue-600 border-blue-500/20",
    CUSTOMER: "bg-gray-500/10 text-gray-600 border-gray-500/20",
};

const getRoleBadgeColor = (roleName: string): string =>
    ROLE_COLORS[(roleName || "").toUpperCase()] ?? "bg-muted text-muted-foreground";

const getRoleIcon = (roleName: string) => {
    switch ((roleName || "").toUpperCase()) {
        case "ADMIN":    return <Shield className="h-3 w-3" />;
        case "MANAGER":  return <UserCog className="h-3 w-3" />;
        case "CASHIER":  return <CreditCard className="h-3 w-3" />;
        case "KITCHEN":  return <ChefHat className="h-3 w-3" />;
        default:         return <Users className="h-3 w-3" />;
    }
};

const getInitials = (name: string): string => {
    if (!name) return "NA";
    return name.split(" ").map((n) => n[0]).join("").toUpperCase().slice(0, 2);
};

const resolveAvatarSrc = (url: string) => {
    if (!url) return "";
    if (url.startsWith("/api/")) {
        return `${window.location.protocol}//${window.location.hostname}:3000${url}`;
    }
    return url;
};

// ─── Form state ───────────────────────────────────────────────────────────────
const emptyForm = () => ({
    username: "",
    password: "",
    confirmPassword: "",
    roleId: 0,
    fullName: "",
    phoneNumber: "",
    age: "",
    email: "",
    address: "",
    avatarUrl: "",
});

type UserForm = ReturnType<typeof emptyForm>;

// ─── Password input with show/hide toggle ─────────────────────────────────────
function PasswordInput({
    id,
    value,
    onChange,
    placeholder,
}: {
    id: string;
    value: string;
    onChange: (v: string) => void;
    placeholder?: string;
}) {
    const [show, setShow] = useState(false);
    return (
        <div className="relative">
            <Input
                id={id}
                type={show ? "text" : "password"}
                placeholder={placeholder ?? "••••••••"}
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="pr-10"
            />
            <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                onClick={() => setShow((s) => !s)}
                tabIndex={-1}
            >
                {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
        </div>
    );
}

// ─── Customer Tab ─────────────────────────────────────────────────────────────
function CustomerTab() {
    const [customers, setCustomers] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState("");
    const [filterBanned, setFilterBanned] = useState<"all" | "active" | "banned">("all");

    const [banOpen, setBanOpen] = useState(false);
    const [unbanOpen, setUnbanOpen] = useState(false);
    const [selectedCustomer, setSelectedCustomer] = useState<User | null>(null);
    const [banReason, setBanReason] = useState("");
    const [actionLoading, setActionLoading] = useState(false);
    const [detailOpen, setDetailOpen] = useState(false);

    const fetchCustomers = useCallback(async () => {
        try {
            setLoading(true);
            const res = await api.get("/users/customers");
            const data: User[] = Array.isArray(res.data)
                ? (res.data as Record<string, unknown>[]).map(normalizeUser)
                : [];
            setCustomers(data);
        } catch {
            toast.error("Không thể tải danh sách khách hàng");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchCustomers(); }, [fetchCustomers]);

    const filtered = customers.filter((c) => {
        const q = searchQuery.toLowerCase();
        const matchSearch =
            (c.full_name || "").toLowerCase().includes(q) ||
            (c.username  || "").toLowerCase().includes(q) ||
            (c.email     || "").toLowerCase().includes(q) ||
            (c.phone_number || "").includes(q);
        const matchBan =
            filterBanned === "all" ||
            (filterBanned === "active"  && !c.banned) ||
            (filterBanned === "banned"  &&  c.banned);
        return matchSearch && matchBan;
    });

    const handleBan = async () => {
        if (!selectedCustomer) return;
        setActionLoading(true);
        try {
            await api.put(`/users/${selectedCustomer.id}/ban`, { reason: banReason.trim() || null });
            toast.success(`Đã khóa tài khoản ${selectedCustomer.full_name || selectedCustomer.username}`);
            setBanOpen(false);
            setBanReason("");
            await fetchCustomers();
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
                || "Lỗi khi khóa tài khoản";
            toast.error(msg);
        } finally {
            setActionLoading(false);
        }
    };

    const handleUnban = async () => {
        if (!selectedCustomer) return;
        setActionLoading(true);
        try {
            await api.put(`/users/${selectedCustomer.id}/unban`);
            toast.success(`Đã mở khóa tài khoản ${selectedCustomer.full_name || selectedCustomer.username}`);
            setUnbanOpen(false);
            await fetchCustomers();
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
                || "Lỗi khi mở khóa";
            toast.error(msg);
        } finally {
            setActionLoading(false);
        }
    };

    const stats = {
        total:  customers.length,
        active: customers.filter((c) => !c.banned).length,
        banned: customers.filter((c) =>  c.banned).length,
    };

    if (loading) {
        return (
            <div className="space-y-4">
                {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-14 rounded-lg" />)}
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Stats */}
            <div className="grid grid-cols-3 gap-3">
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 pt-3 px-4">
                        <CardTitle className="text-xs font-medium text-muted-foreground">Tổng khách hàng</CardTitle>
                        <Users className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent className="px-4 pb-3">
                        <div className="text-2xl font-bold">{stats.total}</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 pt-3 px-4">
                        <CardTitle className="text-xs font-medium text-muted-foreground">Đang hoạt động</CardTitle>
                        <CheckCircle className="h-4 w-4 text-green-500" />
                    </CardHeader>
                    <CardContent className="px-4 pb-3">
                        <div className="text-2xl font-bold text-green-600">{stats.active}</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 pt-3 px-4">
                        <CardTitle className="text-xs font-medium text-muted-foreground">Đã bị khóa</CardTitle>
                        <Ban className="h-4 w-4 text-red-500" />
                    </CardHeader>
                    <CardContent className="px-4 pb-3">
                        <div className="text-2xl font-bold text-red-600">{stats.banned}</div>
                    </CardContent>
                </Card>
            </div>

            {/* Toolbar */}
            <div className="flex flex-col md:flex-row md:items-center gap-4">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                        placeholder="Tìm theo tên, username, email, SĐT..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="pl-10"
                    />
                </div>
                <Select value={filterBanned} onValueChange={(v) => setFilterBanned(v as "all" | "active" | "banned")}>
                    <SelectTrigger className="w-44">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="all">Tất cả</SelectItem>
                        <SelectItem value="active">Đang hoạt động</SelectItem>
                        <SelectItem value="banned">Đã bị khóa</SelectItem>
                    </SelectContent>
                </Select>
                <Button variant="outline" size="icon" onClick={fetchCustomers} title="Làm mới">
                    <RefreshCw className="h-4 w-4" />
                </Button>
            </div>

            {/* Table */}
            {filtered.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 border rounded-lg text-center gap-3">
                    <UserX className="h-10 w-10 text-muted-foreground" />
                    <p className="text-muted-foreground text-sm">Không tìm thấy khách hàng nào</p>
                </div>
            ) : (
                <div className="border rounded-lg overflow-hidden">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Khách hàng</TableHead>
                                <TableHead>Email / SĐT</TableHead>
                                <TableHead>Xác thực</TableHead>
                                <TableHead>Trạng thái</TableHead>
                                <TableHead>Ngày tạo</TableHead>
                                <TableHead className="text-center w-28">Thao tác</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {filtered.map((c) => (
                                <TableRow
                                    key={c.id}
                                    className={`cursor-pointer hover:bg-muted/50 ${c.banned ? "bg-red-50/50" : ""}`}
                                    onClick={() => { setSelectedCustomer(c); setDetailOpen(true); }}
                                >
                                    <TableCell>
                                        <div className="flex items-center gap-3">
                                            <Avatar>
                                                <AvatarImage src={resolveAvatarSrc(c.avatar_url)} />
                                                <AvatarFallback className={`text-sm font-medium ${c.banned ? "bg-red-100 text-red-600" : "bg-primary/10 text-primary"}`}>
                                                    {getInitials(c.full_name)}
                                                </AvatarFallback>
                                            </Avatar>
                                            <div>
                                                <p className="font-medium leading-tight">{c.full_name || "—"}</p>
                                                <p className="text-xs text-muted-foreground">@{c.username}</p>
                                            </div>
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        <div className="text-xs space-y-0.5">
                                            {c.email && <p className="flex items-center gap-1 text-muted-foreground"><Mail className="h-3 w-3 shrink-0" />{c.email}</p>}
                                            {c.phone_number && <p className="flex items-center gap-1 text-muted-foreground"><Phone className="h-3 w-3 shrink-0" />{c.phone_number}</p>}
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        {c.email_verified
                                            ? <Badge className="gap-1 bg-green-500/10 text-green-700 border-green-500/20"><CheckCircle className="h-3 w-3" />Xác thực</Badge>
                                            : <Badge className="bg-yellow-500/10 text-yellow-700 border-yellow-500/20">Chưa xác thực</Badge>
                                        }
                                    </TableCell>
                                    <TableCell>
                                        {c.banned
                                            ? <Badge className="gap-1 bg-red-500/10 text-red-700 border-red-500/20"><Ban className="h-3 w-3" />Đã khóa</Badge>
                                            : <Badge className="bg-green-500/10 text-green-700 border-green-500/20">Hoạt động</Badge>
                                        }
                                    </TableCell>
                                    <TableCell className="text-muted-foreground text-sm">{formatDate(c.created_at)}</TableCell>
                                    <TableCell onClick={(e) => e.stopPropagation()}>
                                        <div className="flex items-center justify-center gap-1">
                                            {c.banned ? (
                                                <Button
                                                    variant="ghost" size="sm"
                                                    className="text-green-600 hover:text-green-700 hover:bg-green-50 gap-1 text-xs"
                                                    title="Mở khóa"
                                                    onClick={() => { setSelectedCustomer(c); setUnbanOpen(true); }}
                                                >
                                                    <CheckCircle className="h-3.5 w-3.5" />Mở khóa
                                                </Button>
                                            ) : (
                                                <Button
                                                    variant="ghost" size="sm"
                                                    className="text-red-600 hover:text-red-700 hover:bg-red-50 gap-1 text-xs"
                                                    title="Khóa tài khoản"
                                                    onClick={() => { setSelectedCustomer(c); setBanReason(""); setBanOpen(true); }}
                                                >
                                                    <Ban className="h-3.5 w-3.5" />Khóa
                                                </Button>
                                            )}
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </div>
            )}

            {/* Ban Dialog */}
            <AlertDialog open={banOpen} onOpenChange={setBanOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle className="flex items-center gap-2 text-red-600">
                            <Ban className="h-5 w-5" />
                            Khóa tài khoản khách hàng
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            Bạn có chắc muốn khóa tài khoản của{" "}
                            <strong>{selectedCustomer?.full_name || selectedCustomer?.username}</strong>?
                            Họ sẽ không thể đăng nhập cho đến khi được mở khóa.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <div className="px-1 pb-2">
                        <Label htmlFor="ban-reason" className="text-sm font-medium">Lý do khóa (tuỳ chọn)</Label>
                        <Textarea
                            id="ban-reason"
                            placeholder="Ví dụ: Vi phạm quy định đặt bàn nhiều lần..."
                            value={banReason}
                            onChange={(e) => setBanReason(e.target.value)}
                            className="mt-1.5 resize-none"
                            rows={3}
                        />
                    </div>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleBan}
                            disabled={actionLoading}
                            className="bg-red-600 text-white hover:bg-red-700"
                        >
                            {actionLoading ? "Đang xử lý..." : "Xác nhận khóa"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Unban Dialog */}
            <AlertDialog open={unbanOpen} onOpenChange={setUnbanOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle className="flex items-center gap-2 text-green-600">
                            <CheckCircle className="h-5 w-5" />
                            Mở khóa tài khoản
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            Mở khóa tài khoản của{" "}
                            <strong>{selectedCustomer?.full_name || selectedCustomer?.username}</strong>?
                            Họ sẽ có thể đăng nhập bình thường trở lại.
                            {selectedCustomer?.ban_reason && (
                                <span className="block mt-2 text-xs text-muted-foreground">
                                    Lý do khóa trước: {selectedCustomer.ban_reason}
                                </span>
                            )}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleUnban}
                            disabled={actionLoading}
                            className="bg-green-600 text-white hover:bg-green-700"
                        >
                            {actionLoading ? "Đang xử lý..." : "Mở khóa"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Detail Dialog */}
            <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Thông tin khách hàng</DialogTitle>
                    </DialogHeader>
                    {selectedCustomer && (
                        <div className="space-y-5">
                            <div className="flex items-center gap-4">
                                <Avatar className="h-14 w-14">
                                    <AvatarImage src={resolveAvatarSrc(selectedCustomer.avatar_url)} />
                                    <AvatarFallback className={selectedCustomer.banned ? "bg-red-100 text-red-600 text-lg font-semibold" : "bg-primary/10 text-primary text-lg font-semibold"}>
                                        {getInitials(selectedCustomer.full_name)}
                                    </AvatarFallback>
                                </Avatar>
                                <div>
                                    <h3 className="font-semibold text-base">{selectedCustomer.full_name || "—"}</h3>
                                    <p className="text-sm text-muted-foreground">@{selectedCustomer.username}</p>
                                    <div className="flex gap-2 mt-1">
                                        <Badge className="bg-gray-500/10 text-gray-600 border-gray-500/20 gap-1">
                                            <Users className="h-3 w-3" />CUSTOMER
                                        </Badge>
                                        {selectedCustomer.banned && (
                                            <Badge className="bg-red-500/10 text-red-700 border-red-500/20 gap-1">
                                                <Ban className="h-3 w-3" />Đã khóa
                                            </Badge>
                                        )}
                                    </div>
                                </div>
                            </div>
                            <Separator />
                            <div className="space-y-2 text-sm">
                                {selectedCustomer.email && (
                                    <div className="flex items-center gap-3">
                                        <Mail className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedCustomer.email}</span>
                                        {selectedCustomer.email_verified && <Badge className="text-xs bg-green-500/10 text-green-700">✓ Xác thực</Badge>}
                                    </div>
                                )}
                                {selectedCustomer.phone_number && (
                                    <div className="flex items-center gap-3">
                                        <Phone className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedCustomer.phone_number}</span>
                                    </div>
                                )}
                                {selectedCustomer.address && (
                                    <div className="flex items-center gap-3">
                                        <MapPin className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedCustomer.address}</span>
                                    </div>
                                )}
                                <div className="flex items-center gap-3">
                                    <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
                                    <span>Tham gia: {formatDate(selectedCustomer.created_at)}</span>
                                </div>
                                {selectedCustomer.banned && selectedCustomer.ban_reason && (
                                    <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-md">
                                        <p className="text-xs font-semibold text-red-700 mb-1">Lý do bị khóa:</p>
                                        <p className="text-sm text-red-600">{selectedCustomer.ban_reason}</p>
                                        {selectedCustomer.banned_at && (
                                            <p className="text-xs text-red-400 mt-1">Khóa lúc: {formatDate(selectedCustomer.banned_at)}</p>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                    <DialogFooter className="gap-2">
                        <Button variant="outline" onClick={() => setDetailOpen(false)}>Đóng</Button>
                        {selectedCustomer && (
                            selectedCustomer.banned ? (
                                <Button
                                    className="bg-green-600 hover:bg-green-700 text-white gap-1"
                                    onClick={() => { setDetailOpen(false); setUnbanOpen(true); }}
                                >
                                    <CheckCircle className="h-4 w-4" />Mở khóa
                                </Button>
                            ) : (
                                <Button
                                    variant="destructive"
                                    className="gap-1"
                                    onClick={() => { setDetailOpen(false); setBanReason(""); setBanOpen(true); }}
                                >
                                    <Ban className="h-4 w-4" />Khóa tài khoản
                                </Button>
                            )
                        )}
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

// ─── Main Page ────────────────────────────────────────────────────────────────
export default function StaffManagementPage() {
    const [users, setUsers]   = useState<User[]>([]);
    const [roles, setRoles]   = useState<Role[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError]   = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [filterRole, setFilterRole]   = useState<string>("all");

    const [createOpen, setCreateOpen] = useState(false);
    const [editOpen, setEditOpen]     = useState(false);
    const [deleteOpen, setDeleteOpen] = useState(false);
    const [detailOpen, setDetailOpen] = useState(false);
    const [pwdOpen, setPwdOpen]       = useState(false);

    const [selectedUser, setSelectedUser] = useState<User | null>(null);
    const [formLoading, setFormLoading]   = useState(false);
    const [form, setForm] = useState<UserForm>(emptyForm());
    const [newPwd, setNewPwd]             = useState("");
    const [newPwdConfirm, setNewPwdConfirm] = useState("");

    const patch = (fields: Partial<UserForm>) =>
        setForm((f) => ({ ...f, ...fields }));

    // ─── Fetch ───────────────────────────────────────────────────────────────
    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const [usersRes, rolesRes] = await Promise.all([
                api.get("/users"),
                api.get("/users/roles"),
            ]);

            const allRoles: Role[] = Array.isArray(rolesRes.data) ? rolesRes.data : [];
            const staffRoles = allRoles.filter((r) =>
                STAFF_ROLE_NAMES.includes((r.name || "").toUpperCase())
            );
            setRoles(
                staffRoles.length > 0
                    ? staffRoles
                    : STAFF_ROLE_NAMES.map((n, i) => ({ id: i + 1, name: n }))
            );

            const normalizedUsers: User[] = Array.isArray(usersRes.data)
                ? (usersRes.data as Record<string, unknown>[]).map(normalizeUser)
                : [];
            setUsers(
                normalizedUsers.filter((u) =>
                    STAFF_ROLE_NAMES.includes((u.role_name || "").toUpperCase())
                )
            );
        } catch (err) {
            const msg = err instanceof Error ? err.message : "Không thể tải dữ liệu";
            setError(msg);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    // ─── Filtered list ────────────────────────────────────────────────────────
    const filteredUsers = users.filter((u) => {
        const q = searchQuery.toLowerCase();
        const matches =
            (u.full_name || "").toLowerCase().includes(q) ||
            (u.username   || "").toLowerCase().includes(q) ||
            (u.email      || "").toLowerCase().includes(q);
        const roleMatch = filterRole === "all" || u.role_id === parseInt(filterRole);
        return matches && roleMatch;
    });

    // ─── Create ───────────────────────────────────────────────────────────────
    const handleCreate = async () => {
        if (!form.username.trim())            return toast.error("Vui lòng nhập tên đăng nhập");
        if (!form.password)                   return toast.error("Vui lòng nhập mật khẩu");
        if (form.password.length < 6)         return toast.error("Mật khẩu ít nhất 6 ký tự");
        if (form.password !== form.confirmPassword) return toast.error("Mật khẩu xác nhận không khớp");
        if (!form.fullName.trim())            return toast.error("Vui lòng nhập họ tên");
        if (!form.roleId)                     return toast.error("Vui lòng chọn vai trò");

        setFormLoading(true);
        try {
            await api.post("/users", {
                username:    form.username.trim(),
                password:    form.password,
                roleId:      form.roleId,
                fullName:    form.fullName.trim(),
                phoneNumber: form.phoneNumber || null,
                age:         form.age ? parseInt(form.age) : null,
                email:       form.email || null,
                address:     form.address || null,
                avatarUrl:   form.avatarUrl || null,
            });
            toast.success("Tạo nhân viên thành công");
            setCreateOpen(false);
            setForm(emptyForm());
            await fetchData();
        } catch (err: unknown) {
            const msg =
                (err as { response?: { data?: { message?: string } } })?.response?.data?.message
                || (err instanceof Error ? err.message : "Lỗi khi tạo nhân viên");
            toast.error(msg);
        } finally {
            setFormLoading(false);
        }
    };

    // ─── Update ───────────────────────────────────────────────────────────────
    const handleUpdate = async () => {
        if (!selectedUser)         return;
        if (!form.fullName.trim()) return toast.error("Vui lòng nhập họ tên");
        if (!form.roleId)          return toast.error("Vui lòng chọn vai trò");

        setFormLoading(true);
        try {
            await api.put(`/users/${selectedUser.id}`, {
                roleId:      form.roleId,
                fullName:    form.fullName.trim(),
                phoneNumber: form.phoneNumber || null,
                age:         form.age ? parseInt(form.age) : null,
                email:       form.email || null,
                address:     form.address || null,
                avatarUrl:   form.avatarUrl || null,
            });
            toast.success("Cập nhật nhân viên thành công");
            setEditOpen(false);
            setForm(emptyForm());
            setSelectedUser(null);
            await fetchData();
        } catch (err: unknown) {
            const msg =
                (err as { response?: { data?: { message?: string } } })?.response?.data?.message
                || (err instanceof Error ? err.message : "Lỗi khi cập nhật");
            toast.error(msg);
        } finally {
            setFormLoading(false);
        }
    };

    // ─── Change password ──────────────────────────────────────────────────────
    const handleChangePassword = async () => {
        if (!selectedUser)              return;
        if (!newPwd)                    return toast.error("Vui lòng nhập mật khẩu mới");
        if (newPwd.length < 6)          return toast.error("Mật khẩu ít nhất 6 ký tự");
        if (newPwd !== newPwdConfirm)   return toast.error("Xác nhận mật khẩu không khớp");

        setFormLoading(true);
        try {
            await api.put(`/users/${selectedUser.id}`, { password: newPwd });
            toast.success("Đổi mật khẩu thành công");
            setPwdOpen(false);
            setNewPwd("");
            setNewPwdConfirm("");
        } catch (err: unknown) {
            const msg =
                (err as { response?: { data?: { message?: string } } })?.response?.data?.message
                || (err instanceof Error ? err.message : "Lỗi khi đổi mật khẩu");
            toast.error(msg);
        } finally {
            setFormLoading(false);
        }
    };

    // ─── Delete ───────────────────────────────────────────────────────────────
    const handleDelete = async () => {
        if (!selectedUser) return;
        setFormLoading(true);
        try {
            await api.delete(`/users/${selectedUser.id}`);
            toast.success("Xóa nhân viên thành công");
            setDeleteOpen(false);
            setSelectedUser(null);
            await fetchData();
        } catch (err: unknown) {
            const msg =
                (err as { response?: { data?: { message?: string } } })?.response?.data?.message
                || (err instanceof Error ? err.message : "Lỗi khi xóa nhân viên");
            toast.error(msg);
        } finally {
            setFormLoading(false);
        }
    };

    // ─── Dialog helpers ───────────────────────────────────────────────────────
    const openCreate = () => {
        setForm({ ...emptyForm(), roleId: roles[1]?.id ?? 0 });
        setCreateOpen(true);
    };

    const openEdit = (u: User) => {
        setSelectedUser(u);
        setForm({
            username:        u.username,
            password:        "",
            confirmPassword: "",
            roleId:          u.role_id,
            fullName:        u.full_name,
            phoneNumber:     u.phone_number || "",
            age:             u.age ? String(u.age) : "",
            email:           u.email || "",
            address:         u.address || "",
            avatarUrl:       u.avatar_url || "",
        });
        setEditOpen(true);
    };

    const openPwd = (u: User) => {
        setSelectedUser(u);
        setNewPwd("");
        setNewPwdConfirm("");
        setPwdOpen(true);
    };

    const openDelete = (u: User) => {
        setSelectedUser(u);
        setDeleteOpen(true);
    };

    const openDetail = (u: User) => {
        setSelectedUser(u);
        setDetailOpen(true);
    };

    // ─── Stats ────────────────────────────────────────────────────────────────
    const stats = {
        total:  users.length,
        byRole: STAFF_ROLE_NAMES.map((r) => ({
            name:  r,
            count: users.filter((u) => (u.role_name || "").toUpperCase() === r).length,
        })),
    };

    // ─── Avatar upload ────────────────────────────────────────────────────────
    const avatarInputRef = useRef<HTMLInputElement>(null);
    const [avatarUploading, setAvatarUploading] = useState(false);

    const handleAvatarUpload = async (file: File) => {
        if (!file.type.startsWith("image/")) return toast.error("Chỉ chấp nhận file ảnh");
        if (file.size > 5 * 1024 * 1024) return toast.error("Ảnh không được vượt quá 5MB");
        setAvatarUploading(true);
        try {
            const fd = new FormData();
            fd.append("image", file);
            const res = await api.post("/images/upload/users", fd, {
                headers: { "Content-Type": "multipart/form-data" },
            });
            patch({ avatarUrl: res.data.url });
            toast.success("Tải ảnh thành công");
        } catch {
            toast.error("Tải ảnh thất bại, vui lòng thử lại");
        } finally {
            setAvatarUploading(false);
        }
    };

    // ─── Shared info fields (create & edit) ───────────────────────────────────
    const renderInfoFields = (idPrefix: string) => (
        <div className="space-y-4">
            {/* Avatar upload */}
            <div className="flex flex-col items-center gap-2">
                <input
                    ref={avatarInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) handleAvatarUpload(f); e.target.value = ""; }}
                />
                <div className="relative">
                    <Avatar className="h-20 w-20">
                        <AvatarImage
                            src={resolveAvatarSrc(form.avatarUrl)}
                        />
                        <AvatarFallback className="bg-primary/10 text-primary text-xl font-semibold">
                            {getInitials(form.fullName || "?")}
                        </AvatarFallback>
                    </Avatar>
                    <button
                        type="button"
                        disabled={avatarUploading}
                        onClick={() => avatarInputRef.current?.click()}
                        className="absolute -bottom-1 -right-1 p-1.5 bg-white border border-border rounded-full shadow hover:bg-muted transition-colors"
                        title="Tải ảnh đại diện"
                    >
                        {avatarUploading ? (
                            <svg className="animate-spin h-3.5 w-3.5 text-muted-foreground" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                            </svg>
                        ) : (
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
                            </svg>
                        )}
                    </button>
                </div>
                {form.avatarUrl && (
                    <button type="button" onClick={() => patch({ avatarUrl: "" })} className="text-xs text-muted-foreground hover:text-destructive">
                        Xóa ảnh
                    </button>
                )}
            </div>
            <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                    <Label htmlFor={`${idPrefix}-fullName`}>Họ và tên *</Label>
                    <Input
                        id={`${idPrefix}-fullName`}
                        placeholder="Nguyễn Văn A"
                        value={form.fullName}
                        onChange={(e) => patch({ fullName: e.target.value })}
                    />
                </div>
                <div className="space-y-1">
                    <Label htmlFor={`${idPrefix}-role`}>Vai trò *</Label>
                    <Select
                        value={form.roleId ? String(form.roleId) : ""}
                        onValueChange={(v) => patch({ roleId: parseInt(v) })}
                    >
                        <SelectTrigger id={`${idPrefix}-role`}>
                            <SelectValue placeholder="Chọn vai trò" />
                        </SelectTrigger>
                        <SelectContent>
                            {roles.map((r) => (
                                <SelectItem key={r.id} value={String(r.id)}>
                                    <span className="flex items-center gap-2">
                                        {getRoleIcon(r.name)}
                                        {r.name}
                                    </span>
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                    <Label htmlFor={`${idPrefix}-phone`}>Số điện thoại</Label>
                    <Input
                        id={`${idPrefix}-phone`}
                        placeholder="0987654321"
                        value={form.phoneNumber}
                        onChange={(e) => patch({ phoneNumber: e.target.value })}
                    />
                </div>
                <div className="space-y-1">
                    <Label htmlFor={`${idPrefix}-age`}>Tuổi</Label>
                    <Input
                        id={`${idPrefix}-age`}
                        type="number"
                        min="16"
                        max="100"
                        placeholder="25"
                        value={form.age}
                        onChange={(e) => patch({ age: e.target.value })}
                    />
                </div>
            </div>
            <div className="space-y-1">
                <Label htmlFor={`${idPrefix}-email`}>Email</Label>
                <Input
                    id={`${idPrefix}-email`}
                    type="email"
                    placeholder="email@example.com"
                    value={form.email}
                    onChange={(e) => patch({ email: e.target.value })}
                />
            </div>
            <div className="space-y-1">
                <Label htmlFor={`${idPrefix}-address`}>Địa chỉ</Label>
                <Input
                    id={`${idPrefix}-address`}
                    placeholder="Hà Nội"
                    value={form.address}
                    onChange={(e) => patch({ address: e.target.value })}
                />
            </div>
        </div>
    );

    // ─── Loading skeleton ─────────────────────────────────────────────────────
    if (loading) {
        return (
            <div className="space-y-6">
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
                    {Array.from({ length: 6 }).map((_, i) => (
                        <Skeleton key={i} className="h-20 rounded-lg" />
                    ))}
                </div>
                <Skeleton className="h-96 rounded-lg" />
            </div>
        );
    }

    // ─── Render ───────────────────────────────────────────────────────────────
    return (
        <div className="space-y-6">
            <Tabs defaultValue="staff">
                <TabsList className="mb-4">
                    <TabsTrigger value="staff" className="gap-2">
                        <UserCog className="h-4 w-4" />
                        Nhân viên
                    </TabsTrigger>
                    <TabsTrigger value="customers" className="gap-2">
                        <Users className="h-4 w-4" />
                        Khách hàng
                    </TabsTrigger>
                </TabsList>

                {/* ══ TAB NHÂN VIÊN ══════════════════════════════════════════ */}
                <TabsContent value="staff" className="space-y-6 mt-0">
                    {/* Stats */}
                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
                        <Card>
                            <CardHeader className="flex flex-row items-center justify-between pb-2 pt-3 px-4">
                                <CardTitle className="text-xs font-medium text-muted-foreground">Tổng</CardTitle>
                                <Users className="h-4 w-4 text-muted-foreground" />
                            </CardHeader>
                            <CardContent className="px-4 pb-3">
                                <div className="text-2xl font-bold">{stats.total}</div>
                            </CardContent>
                        </Card>
                        {stats.byRole.map(({ name, count }) => (
                            <Card key={name}>
                                <CardHeader className="flex flex-row items-center justify-between pb-2 pt-3 px-4">
                                    <CardTitle className="text-xs font-medium text-muted-foreground">{name}</CardTitle>
                                    <span className={`rounded-full p-1 ${ROLE_COLORS[name]}`}>
                                        {getRoleIcon(name)}
                                    </span>
                                </CardHeader>
                                <CardContent className="px-4 pb-3">
                                    <div className={`text-2xl font-bold ${
                                        name === "ADMIN"   ? "text-red-600"    :
                                        name === "MANAGER" ? "text-purple-600" :
                                        name === "CASHIER" ? "text-green-600"  :
                                        name === "KITCHEN" ? "text-orange-600" : "text-blue-600"
                                    }`}>{count}</div>
                                </CardContent>
                            </Card>
                        ))}
                    </div>

                    {/* Toolbar */}
                    <div className="flex flex-col md:flex-row md:items-center gap-4">
                        <div className="relative flex-1 max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                            <Input
                                placeholder="Tìm theo tên, username, email..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="pl-10"
                            />
                        </div>
                        <Select value={filterRole} onValueChange={setFilterRole}>
                            <SelectTrigger className="w-44">
                                <SelectValue placeholder="Vai trò" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="all">Tất cả vai trò</SelectItem>
                                {roles.map((r) => (
                                    <SelectItem key={r.id} value={String(r.id)}>
                                        {r.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <div className="flex gap-2 ml-auto">
                            <Button variant="outline" size="icon" onClick={fetchData} title="Làm mới">
                                <RefreshCw className="h-4 w-4" />
                            </Button>
                            <Button onClick={openCreate}>
                                <Plus className="h-4 w-4 mr-1" />
                                Thêm nhân viên
                            </Button>
                        </div>
                    </div>

                    {/* Error */}
                    {error && (
                        <div className="bg-destructive/10 border border-destructive/20 rounded-lg p-4 flex justify-between items-center">
                            <p className="text-sm text-destructive">{error}</p>
                            <Button variant="outline" size="sm" onClick={fetchData}>Thử lại</Button>
                        </div>
                    )}

                    {/* Table */}
                    {filteredUsers.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-16 border rounded-lg text-center gap-3">
                            <Users className="h-10 w-10 text-muted-foreground" />
                            <p className="text-muted-foreground text-sm">
                                {searchQuery || filterRole !== "all"
                                    ? "Không tìm thấy nhân viên nào"
                                    : "Chưa có nhân viên nào"}
                            </p>
                            <Button variant="outline" size="sm" onClick={openCreate}>
                                <Plus className="h-4 w-4 mr-1" />
                                Thêm nhân viên
                            </Button>
                        </div>
                    ) : (
                        <div className="border rounded-lg overflow-hidden">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Nhân viên</TableHead>
                                        <TableHead>Tài khoản</TableHead>
                                        <TableHead>Vai trò</TableHead>
                                        <TableHead>Liên hệ</TableHead>
                                        <TableHead>Ngày tạo</TableHead>
                                        <TableHead className="text-center w-36">Thao tác</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {filteredUsers.map((u) => (
                                        <TableRow
                                            key={u.id}
                                            className="cursor-pointer hover:bg-muted/50"
                                            onClick={() => openDetail(u)}
                                        >
                                            <TableCell>
                                                <div className="flex items-center gap-3">
                                                    <Avatar>
                                                        <AvatarImage src={resolveAvatarSrc(u.avatar_url)} />
                                                        <AvatarFallback className="bg-primary/10 text-primary text-sm font-medium">
                                                            {getInitials(u.full_name)}
                                                        </AvatarFallback>
                                                    </Avatar>
                                                    <div>
                                                        <p className="font-medium leading-tight">{u.full_name || "—"}</p>
                                                        {u.age ? <p className="text-xs text-muted-foreground">{u.age} tuổi</p> : null}
                                                    </div>
                                                </div>
                                            </TableCell>
                                            <TableCell className="text-muted-foreground text-sm">@{u.username}</TableCell>
                                            <TableCell>
                                                <Badge className={`gap-1 ${getRoleBadgeColor(u.role_name)}`}>
                                                    {getRoleIcon(u.role_name)}
                                                    {u.role_name}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                <div className="text-xs space-y-0.5">
                                                    {u.email && (
                                                        <p className="flex items-center gap-1 text-muted-foreground">
                                                            <Mail className="h-3 w-3 shrink-0" />{u.email}
                                                        </p>
                                                    )}
                                                    {u.phone_number && (
                                                        <p className="flex items-center gap-1 text-muted-foreground">
                                                            <Phone className="h-3 w-3 shrink-0" />{u.phone_number}
                                                        </p>
                                                    )}
                                                </div>
                                            </TableCell>
                                            <TableCell className="text-muted-foreground text-sm">
                                                {formatDate(u.created_at)}
                                            </TableCell>
                                            <TableCell onClick={(e) => e.stopPropagation()}>
                                                <div className="flex items-center justify-center gap-1">
                                                    <Button variant="ghost" size="icon" title="Chỉnh sửa" onClick={() => openEdit(u)}>
                                                        <Pencil className="h-4 w-4" />
                                                    </Button>
                                                    <Button variant="ghost" size="icon" title="Đổi mật khẩu" onClick={() => openPwd(u)}>
                                                        <KeyRound className="h-4 w-4" />
                                                    </Button>
                                                    <Button
                                                        variant="ghost" size="icon"
                                                        title="Xóa"
                                                        className="text-destructive hover:text-destructive"
                                                        onClick={() => openDelete(u)}
                                                    >
                                                        <Trash2 className="h-4 w-4" />
                                                    </Button>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </div>
                    )}
                </TabsContent>

                {/* ══ TAB KHÁCH HÀNG ═════════════════════════════════════════ */}
                <TabsContent value="customers" className="mt-0">
                    <CustomerTab />
                </TabsContent>
            </Tabs>

            {/* ── Create Dialog ─────────────────────────────────────────────── */}
            <Dialog open={createOpen} onOpenChange={setCreateOpen}>
                <DialogContent className="max-w-lg">
                    <DialogHeader>
                        <DialogTitle>Thêm nhân viên mới</DialogTitle>
                        <DialogDescription>
                            Tạo tài khoản cho nhân viên. Mật khẩu được mã hóa BCrypt trước khi lưu.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-2">
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-1">
                                <Label htmlFor="c-username">Tên đăng nhập *</Label>
                                <Input
                                    id="c-username"
                                    placeholder="nhan_vien_01"
                                    value={form.username}
                                    onChange={(e) => patch({ username: e.target.value })}
                                />
                            </div>
                            <div className="space-y-1">
                                <Label>Mật khẩu *</Label>
                                <PasswordInput
                                    id="c-password"
                                    value={form.password}
                                    onChange={(v) => patch({ password: v })}
                                    placeholder="Tối thiểu 6 ký tự"
                                />
                            </div>
                        </div>
                        <div className="space-y-1">
                            <Label>Xác nhận mật khẩu *</Label>
                            <PasswordInput
                                id="c-confirm"
                                value={form.confirmPassword}
                                onChange={(v) => patch({ confirmPassword: v })}
                                placeholder="Nhập lại mật khẩu"
                            />
                        </div>
                        <Separator />
                        {renderInfoFields("c")}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setCreateOpen(false)}>Hủy</Button>
                        <Button onClick={handleCreate} disabled={formLoading}>
                            {formLoading ? "Đang tạo..." : "Tạo nhân viên"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ── Edit Dialog ───────────────────────────────────────────────── */}
            <Dialog open={editOpen} onOpenChange={setEditOpen}>
                <DialogContent className="max-w-lg">
                    <DialogHeader>
                        <DialogTitle>Chỉnh sửa nhân viên</DialogTitle>
                        <DialogDescription>
                            Cập nhật thông tin cho <strong>{selectedUser?.full_name}</strong>
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-2">
                        <div className="space-y-1">
                            <Label>Tên đăng nhập</Label>
                            <Input value={form.username} disabled className="bg-muted text-muted-foreground" />
                        </div>
                        <Separator />
                        {renderInfoFields("e")}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setEditOpen(false)}>Hủy</Button>
                        <Button onClick={handleUpdate} disabled={formLoading}>
                            {formLoading ? "Đang lưu..." : "Lưu thay đổi"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ── Change Password Dialog ────────────────────────────────────── */}
            <Dialog open={pwdOpen} onOpenChange={setPwdOpen}>
                <DialogContent className="max-w-sm">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <KeyRound className="h-5 w-5" />
                            Đổi mật khẩu
                        </DialogTitle>
                        <DialogDescription>
                            Mật khẩu mới cho <strong>{selectedUser?.full_name}</strong>{" "}
                            (@{selectedUser?.username})
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-2">
                        <div className="space-y-1">
                            <Label>Mật khẩu mới *</Label>
                            <PasswordInput
                                id="pwd-new"
                                value={newPwd}
                                onChange={setNewPwd}
                                placeholder="Tối thiểu 6 ký tự"
                            />
                        </div>
                        <div className="space-y-1">
                            <Label>Xác nhận mật khẩu *</Label>
                            <PasswordInput
                                id="pwd-confirm"
                                value={newPwdConfirm}
                                onChange={setNewPwdConfirm}
                                placeholder="Nhập lại mật khẩu"
                            />
                        </div>
                        <p className="text-xs text-muted-foreground">
                            Mật khẩu được mã hóa BCrypt (salt=10) trước khi lưu vào database.
                        </p>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setPwdOpen(false)}>Hủy</Button>
                        <Button onClick={handleChangePassword} disabled={formLoading}>
                            {formLoading ? "Đang đổi..." : "Đổi mật khẩu"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* ── Delete Confirm ────────────────────────────────────────────── */}
            <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Xóa nhân viên</AlertDialogTitle>
                        <AlertDialogDescription>
                            Bạn có chắc muốn xóa tài khoản của{" "}
                            <strong>{selectedUser?.full_name}</strong> (@{selectedUser?.username})?
                            <br />Hành động này không thể hoàn tác.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleDelete}
                            disabled={formLoading}
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        >
                            {formLoading ? "Đang xóa..." : "Xóa"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* ── Detail Dialog ─────────────────────────────────────────────── */}
            <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Thông tin nhân viên</DialogTitle>
                    </DialogHeader>
                    {selectedUser && (
                        <div className="space-y-5">
                            <div className="flex items-center gap-4">
                                <Avatar className="h-14 w-14">
                                    <AvatarImage src={resolveAvatarSrc(selectedUser.avatar_url)} />
                                    <AvatarFallback className="bg-primary/10 text-primary text-lg font-semibold">
                                        {getInitials(selectedUser.full_name)}
                                    </AvatarFallback>
                                </Avatar>
                                <div>
                                    <h3 className="font-semibold text-base">{selectedUser.full_name || "—"}</h3>
                                    <p className="text-sm text-muted-foreground">@{selectedUser.username}</p>
                                    <Badge className={`mt-1 gap-1 ${getRoleBadgeColor(selectedUser.role_name)}`}>
                                        {getRoleIcon(selectedUser.role_name)}
                                        {selectedUser.role_name}
                                    </Badge>
                                </div>
                            </div>
                            <Separator />
                            <div className="space-y-2 text-sm">
                                {selectedUser.email && (
                                    <div className="flex items-center gap-3">
                                        <Mail className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedUser.email}</span>
                                    </div>
                                )}
                                {selectedUser.phone_number && (
                                    <div className="flex items-center gap-3">
                                        <Phone className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedUser.phone_number}</span>
                                    </div>
                                )}
                                {selectedUser.address && (
                                    <div className="flex items-center gap-3">
                                        <MapPin className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedUser.address}</span>
                                    </div>
                                )}
                                {selectedUser.age ? (
                                    <div className="flex items-center gap-3">
                                        <Users className="h-4 w-4 text-muted-foreground shrink-0" />
                                        <span>{selectedUser.age} tuổi</span>
                                    </div>
                                ) : null}
                                <div className="flex items-center gap-3">
                                    <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
                                    <span>Tham gia: {formatDate(selectedUser.created_at)}</span>
                                </div>
                                <div className="flex items-center gap-3">
                                    <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
                                    <span>Cập nhật: {formatDate(selectedUser.updated_at)}</span>
                                </div>
                            </div>
                        </div>
                    )}
                    <DialogFooter className="gap-2">
                        <Button variant="outline" onClick={() => setDetailOpen(false)}>Đóng</Button>
                        <Button
                            variant="outline"
                            onClick={() => { setDetailOpen(false); openPwd(selectedUser!); }}
                        >
                            <KeyRound className="h-4 w-4 mr-1" />
                            Đổi mật khẩu
                        </Button>
                        <Button onClick={() => { setDetailOpen(false); openEdit(selectedUser!); }}>
                            <Pencil className="h-4 w-4 mr-1" />
                            Chỉnh sửa
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

        </div>
    );
}

