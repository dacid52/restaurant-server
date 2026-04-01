"use client";

import { useEffect, useState, useCallback } from "react";
import {
    Search,
    RefreshCw,
    Filter,
    Eye,
    ChefHat,
    Clock,
    CheckCircle,
    XCircle,
    CreditCard,
    Package,
    AlertCircle,
    Receipt,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios";
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
import { Separator } from "@/components/ui/separator";

// Types
interface OrderDetail {
    id: number;
    food_id: number;
    food_name?: string;
    quantity: number;
    price: number;
}

interface Order {
    id: number;
    table_id: number;
    user_id: number | null;
    table_key: string;
    order_time: string;
    status: string;
    total: number;
    is_buffet: boolean;
    payment_status: string;
    updated_at: string;
    details: OrderDetail[];
}

// Helper functions
const formatCurrency = (value: number): string => {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
    }).format(value);
};

const formatDateTime = (dateString: string): string => {
    return new Date(dateString).toLocaleString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });
};

const getStatusColor = (status: string | null): string => {
    if (!status) return "bg-muted text-muted-foreground";
    switch (status.toLowerCase()) {
        case "chờ xác nhận":
            return "bg-yellow-500/10 text-yellow-600 border-yellow-500/20";
        case "đang nấu":
            return "bg-blue-500/10 text-blue-600 border-blue-500/20";
        case "hoàn thành":
            return "bg-green-500/10 text-green-600 border-green-500/20";
        case "đã hủy":
            return "bg-red-500/10 text-red-600 border-red-500/20";
        default:
            return "bg-muted text-muted-foreground";
    }
};

const getPaymentStatusColor = (status: string | null): string => {
    if (!status) return "bg-orange-500/10 text-orange-600 border-orange-500/20"; // default to unpaid
    switch (status.toLowerCase()) {
        case "paid":
            return "bg-green-500/10 text-green-600 border-green-500/20";
        case "unpaid":
            return "bg-orange-500/10 text-orange-600 border-orange-500/20";
        case "pending":
            return "bg-yellow-500/10 text-yellow-600 border-yellow-500/20";
        default:
            return "bg-muted text-muted-foreground";
    }
};

const getPaymentStatusText = (status: string | null): string => {
    if (!status) return "Chưa thanh toán";
    switch (status.toLowerCase()) {
        case "paid":
            return "Đã thanh toán";
        case "unpaid":
            return "Chưa thanh toán";
        case "pending":
            return "Đang chờ";
        default:
            return status;
    }
};

const getStatusIcon = (status: string | null) => {
    if (!status) return <AlertCircle className="h-4 w-4" />;
    switch (status.toLowerCase()) {
        case "chờ xác nhận":
            return <Clock className="h-4 w-4" />;
        case "đang nấu":
            return <ChefHat className="h-4 w-4" />;
        case "hoàn thành":
            return <CheckCircle className="h-4 w-4" />;
        case "đã hủy":
            return <XCircle className="h-4 w-4" />;
        default:
            return <AlertCircle className="h-4 w-4" />;
    }
};

const ORDER_STATUSES = [
    "Chờ xác nhận",
    "Đang nấu",
    "Hoàn thành",
    "Đã hủy",
];

export default function OrdersManagementPage() {
    const [orders, setOrders] = useState<Order[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [filterStatus, setFilterStatus] = useState<string>("all");
    const [filterPayment, setFilterPayment] = useState<string>("all");
    const [filterDate, setFilterDate] = useState<string>(
        new Date().toLocaleDateString('en-CA') // YYYY-MM-DD local format
    );

    // Dialog states
    const [isDetailDialogOpen, setIsDetailDialogOpen] = useState(false);
    const [isStatusDialogOpen, setIsStatusDialogOpen] = useState(false);
    const [isConfirmDialogOpen, setIsConfirmDialogOpen] = useState(false);
    const [isPaymentDialogOpen, setIsPaymentDialogOpen] = useState(false);
    const [isCompletePaymentDialogOpen, setIsCompletePaymentDialogOpen] = useState(false);

    // Selected order
    const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
    const [newStatus, setNewStatus] = useState<string>("");
    const [actionLoading, setActionLoading] = useState(false);

    // Fetch orders
    const fetchOrders = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await api.get("/orders");
            setOrders(response.data || []);
        } catch (err) {
            const errorMessage = err instanceof Error ? err.message : "Không thể tải dữ liệu đơn hàng";
            setError(errorMessage);
            console.error("Error fetching orders:", err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchOrders();
        // Auto refresh every 10s to catch new orders from customers
        const interval = setInterval(fetchOrders, 10000);
        return () => clearInterval(interval);
    }, [fetchOrders]);

    // Filter orders
    const filteredOrders = orders.filter((order) => {
        const matchesSearch =
            order.id.toString().includes(searchQuery) ||
            order.table_id.toString().includes(searchQuery);
        const matchesStatus = filterStatus === "all" || order.status === filterStatus;
        const paymentStatus = order.payment_status || "unpaid";
        const matchesPayment = filterPayment === "all" || paymentStatus === filterPayment;

        let matchesDate = true;
        if (filterDate) {
            const orderDateStr = new Date(order.order_time).toLocaleDateString('en-CA');
            matchesDate = orderDateStr === filterDate;
        }

        return matchesSearch && matchesStatus && matchesPayment && matchesDate;
    });

    // Actions
    const handleViewDetail = (order: Order) => {
        setSelectedOrder(order);
        setIsDetailDialogOpen(true);
    };

    const handleOpenStatusDialog = (order: Order) => {
        setSelectedOrder(order);
        setNewStatus(order.status);
        setIsStatusDialogOpen(true);
    };

    const handleUpdateStatus = async () => {
        if (!selectedOrder || !newStatus) return;

        setActionLoading(true);
        try {
            await api.put(`/orders/${selectedOrder.id}/status`, { status: newStatus });
            toast.success("Cập nhật trạng thái thành công");
            setIsStatusDialogOpen(false);
            await fetchOrders();
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Lỗi khi cập nhật trạng thái";
            toast.error(errorMsg);
            console.error("Error updating status:", err);
        } finally {
            setActionLoading(false);
        }
    };

    const handleConfirmOrder = async () => {
        if (!selectedOrder) return;

        setActionLoading(true);
        try {
            await api.post(`/orders/${selectedOrder.id}/confirm`);
            toast.success("Đã xác nhận và gửi đơn sang bếp");
            setIsConfirmDialogOpen(false);
            await fetchOrders();
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Lỗi khi xác nhận đơn hàng";
            toast.error(errorMsg);
            console.error("Error confirming order:", err);
        } finally {
            setActionLoading(false);
        }
    };

    const handleRequestPayment = async () => {
        if (!selectedOrder) return;

        setActionLoading(true);
        try {
            await api.post(`/orders/${selectedOrder.id}/request-payment`);
            toast.success("Đã gửi yêu cầu thanh toán");
            setIsPaymentDialogOpen(false);
            await fetchOrders();
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Lỗi khi gửi yêu cầu thanh toán";
            toast.error(errorMsg);
            console.error("Error requesting payment:", err);
        } finally {
            setActionLoading(false);
        }
    };

    const handleCompletePayment = async () => {
        if (!selectedOrder) return;

        setActionLoading(true);
        try {
            await api.post("/orders/payments/complete", { order_id: selectedOrder.id });
            toast.success("Đã hoàn tất thanh toán và đóng bàn");
            setIsCompletePaymentDialogOpen(false);
            await fetchOrders();
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Lỗi khi hoàn tất thanh toán";
            toast.error(errorMsg);
            console.error("Error completing payment:", err);
        } finally {
            setActionLoading(false);
        }
    };

    // Stats
    const stats = {
        total: orders.length,
        pending: orders.filter((o) => o.status === "Chờ xác nhận").length,
        cooking: orders.filter((o) => o.status === "Đang nấu").length,
        completed: orders.filter((o) => o.status === "Hoàn thành").length,
        unpaid: orders.filter((o) => (o.payment_status || "unpaid") === "unpaid").length,
    };

    // Loading state
    if (loading) {
        return (
            <div className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    {Array.from({ length: 4 }).map((_, i) => (
                        <Skeleton key={i} className="h-24 rounded-lg" />
                    ))}
                </div>
                <Skeleton className="h-96 rounded-lg" />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Stats Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium text-muted-foreground">Tổng đơn</CardTitle>
                        <Receipt className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{stats.total}</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium text-muted-foreground">Chờ xác nhận</CardTitle>
                        <Clock className="h-4 w-4 text-yellow-500" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-yellow-600">{stats.pending}</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium text-muted-foreground">Đang nấu</CardTitle>
                        <ChefHat className="h-4 w-4 text-blue-500" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-blue-600">{stats.cooking}</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium text-muted-foreground">Hoàn thành</CardTitle>
                        <CheckCircle className="h-4 w-4 text-green-500" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-green-600">{stats.completed}</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium text-muted-foreground">Chưa thanh toán</CardTitle>
                        <CreditCard className="h-4 w-4 text-orange-500" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-orange-600">{stats.unpaid}</div>
                    </CardContent>
                </Card>
            </div>

            {/* Filters */}
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div className="flex flex-1 flex-wrap items-center gap-4">
                    <div className="relative flex-1 min-w-[200px] max-w-xs">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                        <Input
                            placeholder="Tìm theo ID đơn, bàn..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="pl-10"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <Input 
                            type="date"
                            value={filterDate}
                            onChange={(e) => setFilterDate(e.target.value)}
                            className="w-auto"
                        />
                    </div>
                    <Select value={filterStatus} onValueChange={setFilterStatus}>
                        <SelectTrigger className="w-44">
                            <Filter className="h-4 w-4 mr-2" />
                            <SelectValue placeholder="Trạng thái" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="all">Tất cả trạng thái</SelectItem>
                            {ORDER_STATUSES.map((status) => (
                                <SelectItem key={status} value={status}>
                                    {status}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                    <Select value={filterPayment} onValueChange={setFilterPayment}>
                        <SelectTrigger className="w-44">
                            <CreditCard className="h-4 w-4 mr-2" />
                            <SelectValue placeholder="Thanh toán" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="all">Tất cả</SelectItem>
                            <SelectItem value="paid">Đã thanh toán</SelectItem>
                            <SelectItem value="unpaid">Chưa thanh toán</SelectItem>
                            <SelectItem value="pending">Đang chờ</SelectItem>
                        </SelectContent>
                    </Select>
                </div>
                <Button variant="outline" size="icon" onClick={fetchOrders}>
                    <RefreshCw className="h-4 w-4" />
                </Button>
            </div>

            {/* Error State */}
            {error && (
                <div className="bg-destructive/10 border border-destructive/20 rounded-lg p-4 flex justify-between items-center">
                    <div>
                        <p className="font-medium text-destructive">Lỗi</p>
                        <p className="text-sm text-destructive/80">{error}</p>
                    </div>
                    <Button variant="outline" size="sm" onClick={fetchOrders}>
                        Thử lại
                    </Button>
                </div>
            )}

            {/* Orders Table */}
            {filteredOrders.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center border rounded-lg">
                    <Package className="h-12 w-12 text-muted-foreground mb-4" />
                    <p className="text-muted-foreground">
                        {searchQuery || filterStatus !== "all" || filterPayment !== "all"
                            ? "Không tìm thấy đơn hàng nào"
                            : "Chưa có đơn hàng nào"}
                    </p>
                </div>
            ) : (
                <div className="border rounded-lg overflow-hidden">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-20">ID</TableHead>
                                <TableHead className="w-24">Bàn</TableHead>
                                <TableHead>Thời gian</TableHead>
                                <TableHead>Trạng thái</TableHead>
                                <TableHead>Thanh toán</TableHead>
                                <TableHead className="text-right">Tổng tiền</TableHead>
                                <TableHead className="w-32 text-center">Thao tác</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {filteredOrders.map((order) => (
                                <TableRow key={order.id}>
                                    <TableCell className="font-medium">#{order.id}</TableCell>
                                    <TableCell>
                                        <Badge variant="outline">Bàn {order.table_id}</Badge>
                                    </TableCell>
                                    <TableCell className="text-sm text-muted-foreground">
                                        {formatDateTime(order.order_time)}
                                    </TableCell>
                                    <TableCell>
                                        <Badge className={`gap-1 ${getStatusColor(order.status)}`}>
                                            {getStatusIcon(order.status)}
                                            {order.status}
                                        </Badge>
                                    </TableCell>
                                    <TableCell>
                                        <Badge className={getPaymentStatusColor(order.payment_status)}>
                                            {getPaymentStatusText(order.payment_status)}
                                        </Badge>
                                    </TableCell>
                                    <TableCell className="text-right font-semibold">
                                        {formatCurrency(order.total)}
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex items-center justify-center gap-1">
                                            <Button
                                                variant="ghost"
                                                size="icon"
                                                onClick={() => handleViewDetail(order)}
                                                title="Xem chi tiết"
                                            >
                                                <Eye className="h-4 w-4" />
                                            </Button>
                                            {order.status === "Chờ xác nhận" && (
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => {
                                                        setSelectedOrder(order);
                                                        setIsConfirmDialogOpen(true);
                                                    }}
                                                    title="Xác nhận và gửi bếp"
                                                    className="text-green-600 hover:text-green-700"
                                                >
                                                    <CheckCircle className="h-4 w-4" />
                                                </Button>
                                            )}
                                            {order.payment_status === "unpaid" && order.status === "Hoàn thành" && (
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => {
                                                        setSelectedOrder(order);
                                                        setIsCompletePaymentDialogOpen(true);
                                                    }}
                                                    title="Hoàn tất thanh toán"
                                                    className="text-blue-600 hover:text-blue-700"
                                                >
                                                    <CreditCard className="h-4 w-4" />
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

            {/* Order Detail Dialog */}
            <Dialog open={isDetailDialogOpen} onOpenChange={setIsDetailDialogOpen}>
                <DialogContent className="max-w-lg">
                    <DialogHeader>
                        <DialogTitle>Chi tiết đơn hàng #{selectedOrder?.id}</DialogTitle>
                        <DialogDescription>Thông tin chi tiết về đơn hàng</DialogDescription>
                    </DialogHeader>
                    {selectedOrder && (
                        <div className="space-y-4">
                            <div className="grid grid-cols-2 gap-4 text-sm">
                                <div>
                                    <span className="text-muted-foreground">Bàn:</span>
                                    <span className="ml-2 font-medium">{selectedOrder.table_id}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">Buffet:</span>
                                    <span className="ml-2 font-medium">{selectedOrder.is_buffet ? "Có" : "Không"}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">Thời gian:</span>
                                    <span className="ml-2 font-medium">{formatDateTime(selectedOrder.order_time)}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">Cập nhật:</span>
                                    <span className="ml-2 font-medium">{formatDateTime(selectedOrder.updated_at)}</span>
                                </div>
                            </div>
                            <div className="flex items-center gap-4">
                                <Badge className={getStatusColor(selectedOrder.status)}>{selectedOrder.status}</Badge>
                                <Badge className={getPaymentStatusColor(selectedOrder.payment_status)}>
                                    {getPaymentStatusText(selectedOrder.payment_status)}
                                </Badge>
                            </div>
                            <Separator />
                            <div>
                                <h4 className="font-medium mb-2">Món ăn đã đặt</h4>
                                <div className="space-y-2">
                                    {selectedOrder.details.map((item) => (
                                        <div key={item.id} className="flex justify-between items-center text-sm">
                                            <span>
                                                {item.food_name || `Món #${item.food_id}`} x{item.quantity}
                                            </span>
                                            <span className="font-medium">{formatCurrency(item.price * item.quantity)}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                            <Separator />
                            <div className="flex justify-between items-center font-bold text-lg">
                                <span>Tổng cộng</span>
                                <span className="text-primary">{formatCurrency(selectedOrder.total)}</span>
                            </div>
                        </div>
                    )}
                    <DialogFooter className="gap-2">
                        <Button variant="outline" onClick={() => setIsDetailDialogOpen(false)}>
                            Đóng
                        </Button>
                        <Button variant="outline" onClick={() => handleOpenStatusDialog(selectedOrder!)}>
                            Đổi trạng thái
                        </Button>
                        {selectedOrder?.payment_status === "unpaid" && (
                            <Button
                                onClick={() => {
                                    setIsDetailDialogOpen(false);
                                    setIsPaymentDialogOpen(true);
                                }}
                            >
                                Yêu cầu thanh toán
                            </Button>
                        )}
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Update Status Dialog */}
            <Dialog open={isStatusDialogOpen} onOpenChange={setIsStatusDialogOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Cập nhật trạng thái đơn hàng</DialogTitle>
                        <DialogDescription>Chọn trạng thái mới cho đơn hàng #{selectedOrder?.id}</DialogDescription>
                    </DialogHeader>
                    <Select value={newStatus} onValueChange={setNewStatus}>
                        <SelectTrigger>
                            <SelectValue placeholder="Chọn trạng thái" />
                        </SelectTrigger>
                        <SelectContent>
                            {ORDER_STATUSES.map((status) => (
                                <SelectItem key={status} value={status}>
                                    {status}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsStatusDialogOpen(false)}>
                            Hủy
                        </Button>
                        <Button onClick={handleUpdateStatus} disabled={actionLoading}>
                            {actionLoading ? "Đang cập nhật..." : "Cập nhật"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Confirm Order Dialog */}
            <AlertDialog open={isConfirmDialogOpen} onOpenChange={setIsConfirmDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Xác nhận đơn hàng</AlertDialogTitle>
                        <AlertDialogDescription>
                            Bạn có chắc muốn xác nhận và gửi đơn hàng #{selectedOrder?.id} sang bếp?
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                        <AlertDialogAction onClick={handleConfirmOrder} disabled={actionLoading}>
                            {actionLoading ? "Đang xử lý..." : "Xác nhận & Gửi bếp"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Request Payment Dialog */}
            <AlertDialog open={isPaymentDialogOpen} onOpenChange={setIsPaymentDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Yêu cầu thanh toán</AlertDialogTitle>
                        <AlertDialogDescription>
                            Gửi yêu cầu thanh toán cho đơn hàng #{selectedOrder?.id}?
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                        <AlertDialogAction onClick={handleRequestPayment} disabled={actionLoading}>
                            {actionLoading ? "Đang gửi..." : "Gửi yêu cầu"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Complete Payment Dialog */}
            <AlertDialog open={isCompletePaymentDialogOpen} onOpenChange={setIsCompletePaymentDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Hoàn tất thanh toán</AlertDialogTitle>
                        <AlertDialogDescription>
                            Xác nhận đã thu tiền xong cho đơn hàng #{selectedOrder?.id}?
                            <br />
                            <span className="font-medium">
                                Tổng tiền: {selectedOrder && formatCurrency(selectedOrder.total)}
                            </span>
                            <br />
                            Bàn sẽ được đặt về trạng thái trống sau khi hoàn tất.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                        <AlertDialogAction onClick={handleCompletePayment} disabled={actionLoading}>
                            {actionLoading ? "Đang xử lý..." : "Hoàn tất thanh toán"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
