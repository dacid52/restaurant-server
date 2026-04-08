"use client";

import { useState, useEffect } from "react";
import useSWR, { mutate } from "swr";
import { toast } from "sonner";
import {
    Clock,
    CheckCircle,
    Banknote,
    Receipt,
    DollarSign,
    Eye,
    CreditCard,
    History,
    Wifi,
    WifiOff,
    Printer,
    UtensilsCrossed,
    ExternalLink
} from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import api from "@/lib/axios";
import { paymentSocket, orderSocket, kitchenSocket } from "@/lib/socket";

// Types
interface WaitingPayment {
    order_id: number;
    table_id: number;
    table_key: string;
    total: number;
    request_time: string;
    status: string;
    order_count: number;
    payment_method?: string;   // 'cash' | 'momo'
    momo_trans_id?: string;    // mã giao dịch MoMo (nếu có)
}

interface OrderItem {
    id: number;
    menu_item_name: string;
    quantity: number;
    price: number;
    total: number;
}

interface Order {
    id: number;
    table_id: number;
    table_key: string;
    status: string;
    payment_status: string;
    total: number;
    created_at: string;
    items?: OrderItem[];
}

interface OrderSessionSummary {
    table_id: number;
    table_key: string;
    total_amount: number;
    order_count: number;
    status: string;
    payment_status: string;
    last_order_time: string;
    buffet_active?: boolean;
    buffet_package_name?: string;
    has_pending_buffet?: boolean;
    pending_buffet_order_id?: number;
    pending_buffet_package_name?: string;
    pending_buffet_price?: number;
}

interface OrderSessionDetail {
    summary: OrderSessionSummary;
    orders: Order[];
}

interface PaymentHistory {
    id: number;
    order_id: number;
    amount: number;
    method: string;
    paid_at: string;
    status: string;
}

// Fetchers
const fetcher = (url: string) => api.get(url).then((res) => res.data);

// Format currency
const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
    }).format(amount);
};

// Format date
const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString("vi-VN");
};

export default function CashierPage() {
    const [selectedPayment, setSelectedPayment] = useState<WaitingPayment | null>(null);
    const [detailDialogOpen, setDetailDialogOpen] = useState(false);
    const [paymentDialogOpen, setPaymentDialogOpen] = useState(false);
    const [isProcessing, setIsProcessing] = useState(false);
    const [openingMomoOrderId, setOpeningMomoOrderId] = useState<number | null>(null);
    const [isConnected, setIsConnected] = useState(false);
    const [confirmingBuffetId, setConfirmingBuffetId] = useState<number | null>(null);
    useEffect(() => {
        let mounted = true;

        paymentSocket.connect(
            () => {
                if (!mounted) return;
                setIsConnected(true);
                
                // Lắng nghe sự kiện khách yêu cầu thanh toán
                paymentSocket.subscribe('/topic/payment.request', (data) => {
                    toast.info(`Bàn ${data.table_id} yêu cầu thanh toán!`, {
                        description: `Tổng tiền: ${formatCurrency(data.amount)}`
                    });
                    mutate("/payments/waiting");
                    mutate("/orders/sessions");
                });

                // Lắng nghe sự kiện thanh toán hoàn tất (nếu có cashier khác thao tác)
                paymentSocket.subscribe('/topic/payment.completed', (data) => {
                    mutate("/payments/waiting");
                    mutate("/orders/sessions");
                    if (data?.table_id && data?.table_key) {
                        mutate(`/orders/sessions/detail?tableId=${data.table_id}&tableKey=${encodeURIComponent(data.table_key)}`);
                    }
                    mutate("/payments/history");
                });
            },
            (err) => {
                if (!mounted) return;
                setIsConnected(false);
                console.error("Socket error", err);
            }
        );

        return () => {
            mounted = false;
            paymentSocket.disconnect();
        };
    }, []);

    // Lắng nghe đơn hàng mới + cập nhật trạng thái từ order-service
    useEffect(() => {
        let mounted = true;

        orderSocket.connect(
            () => {
                if (!mounted) return;

                // Khách vừa đặt món mới
                orderSocket.subscribe("/topic/order.created", (data) => {
                    if (!mounted) return;
                    toast.info(`Đơn hàng mới - Bàn ${data?.table_id ?? ""}`, {
                        description: "Khách vừa đặt thêm món",
                    });
                    mutate("/orders/sessions");
                    mutate("/payments/waiting");
                });

                // Trạng thái đơn thay đổi (bếp xác nhận, hoàn thành,…)
                orderSocket.subscribe("/topic/order.status.updated", (data) => {
                    if (!mounted) return;
                    mutate("/orders/sessions");
                    mutate("/payments/waiting");
                    if (data?.table_id != null && data?.table_key) {
                        mutate(`/orders/sessions/detail?tableId=${data.table_id}&tableKey=${encodeURIComponent(data.table_key)}`);
                    }
                });
            },
            (err) => {
                if (!mounted) return;
                console.error("Order socket error (cashier):", err);
            }
        );

        return () => {
            mounted = false;
            orderSocket.disconnect();
        };
    }, []);

    // Kitchen-service phát order.status.updated qua broker riêng (/ws/kitchen)
    // Cần subscribe riêng qua kitchenSocket để thu ngân biết bếp đã nấu xong
    useEffect(() => {
        let mounted = true;

        kitchenSocket.connect(
            () => {
                if (!mounted) return;

                kitchenSocket.subscribe("/topic/order.status.updated", (data) => {
                    if (!mounted) return;
                    mutate("/orders/sessions");
                    mutate("/payments/waiting");
                    if (data?.table_id != null && data?.table_key) {
                        mutate(`/orders/sessions/detail?tableId=${data.table_id}&tableKey=${encodeURIComponent(data.table_key)}`);
                    }
                });
            },
            (err) => {
                if (!mounted) return;
                console.error("Kitchen socket error (cashier):", err);
            }
        );

        return () => {
            mounted = false;
            kitchenSocket.disconnect();
        };
    }, []);

    // Fetch waiting payments
    const { data: waitingPayments, isLoading: isLoadingWaiting } = useSWR<WaitingPayment[]>(
        "/payments/waiting",
        fetcher,
        { refreshInterval: 10000 } // Tăng interval lên 10s vì đã có socket LIVE
    );

    const { data: allSessions, isLoading: isLoadingOrders } = useSWR<OrderSessionSummary[]>(
        "/orders/sessions",
        fetcher,
        { refreshInterval: 15000 }
    );

    const selectedSessionDetailKey = selectedPayment
        ? `/orders/sessions/detail?tableId=${selectedPayment.table_id}&tableKey=${encodeURIComponent(selectedPayment.table_key || "")}`
        : null;

    const { data: selectedSessionDetail } = useSWR<OrderSessionDetail>(
        selectedSessionDetailKey,
        fetcher
    );

    // Fetch payment history
    const { data: payHistory, isLoading: isLoadingHistory } = useSWR<PaymentHistory[]>(
        "/payments/history",
        fetcher
    );

    // Calculate stats
    const totalWaiting = waitingPayments?.length || 0;
    const totalWaitingAmount = waitingPayments?.reduce((sum: number, p: WaitingPayment) => sum + p.total, 0) || 0;

    const selectedSessionOrders = selectedSessionDetail?.orders || [];

    const selectedSessionItems = selectedSessionOrders.flatMap((order: Order) =>
        (order.items || []).map((item: OrderItem) => ({
            ...item,
            order_id: order.id,
        }))
    );

    // Handle view detail
    const handleViewDetail = (payment: WaitingPayment) => {
        setSelectedPayment(payment);
        setDetailDialogOpen(true);
    };

    // Handle open payment dialog
    const handleOpenPaymentDialog = (payment: WaitingPayment) => {
        setSelectedPayment(payment);
        setPaymentDialogOpen(true);
    };

    const handleOpenMomoPayment = async (payment: WaitingPayment) => {
        setOpeningMomoOrderId(payment.order_id);
        try {
            const payload = {
                order_id: payment.order_id,
                table_id: payment.table_id,
                table_key: payment.table_key,
                amount: payment.total,
            };

            const response = await api.post("/payments/momo/create", payload);
            const payUrl = response.data?.payUrl || response.data?.pay_url;
            if (!payUrl) {
                throw new Error("MoMo không trả về liên kết thanh toán");
            }

            const popup = window.open(payUrl, "_blank", "noopener,noreferrer");
            if (!popup) {
                window.location.href = payUrl;
            }

            toast.success("Đã mở trang thanh toán MoMo", {
                description: `Bàn ${payment.table_id}: khách có thể quét và thanh toán sandbox`,
            });
        } catch (error: any) {
            toast.error("Không mở được trang MoMo", {
                description: error.response?.data?.error || error.message,
            });
        } finally {
            setOpeningMomoOrderId(null);
        }
    };

    // Print bill generator
    const printBill = (payment: WaitingPayment) => {
        const printWindow = window.open('', '_blank');
        if (!printWindow) return;

        const html = `
        <html>
        <head>
            <title>Hóa đơn - Bàn ${payment.table_id}</title>
            <style>
                body { font-family: 'Courier New', Courier, monospace; width: 300px; margin: 0 auto; color: #000; }
                h2 { text-align: center; font-size: 18px; margin-bottom: 5px; }
                p { text-align: center; font-size: 12px; margin: 2px 0; }
                .divider { border-top: 1px dashed #000; margin: 10px 0; }
                .flex { display: flex; justify-content: space-between; }
                .bold { font-weight: bold; }
                table { width: 100%; font-size: 12px; border-collapse: collapse; }
                th, td { text-align: left; padding: 4px 0; }
                .right { text-align: right; }
                .total { font-size: 16px; font-weight: bold; margin-top: 10px; }
            </style>
        </head>
        <body>
            <h2>NHÀ HÀNG AURORA</h2>
            <p>123 Đường Tôn Đức Thắng, Q1, TP.HCM</p>
            <p>Điện thoại: 1900 1234</p>
            <div class="divider"></div>
            <p class="bold">HÓA ĐƠN THANH TOÁN</p>
            <div class="flex">
                <span>Bàn: ${payment.table_id}</span>
                <span>${new Date().toLocaleDateString('vi-VN')}</span>
            </div>
            <div class="flex">
                <span>Mã yêu cầu: #${payment.order_id}</span>
                <span>${new Date().toLocaleTimeString('vi-VN')}</span>
            </div>
            <div class="divider"></div>
            <div class="flex total">
                <span>TỔNG CỘNG:</span>
                <span>${formatCurrency(payment.total)}</span>
            </div>
            <div class="divider"></div>
            <p>Cảm ơn & Hẹn gặp lại quý khách!</p>
            <p>Password Wifi: aurora123</p>
            <script>
                window.onload = function() { window.print(); window.close(); }
            </script>
        </body>
        </html>
        `;

        printWindow.document.write(html);
        printWindow.document.close();
    };

    // Handle process payment
    const handleProcessPayment = async () => {
        if (!selectedPayment) return;

        setIsProcessing(true);
        try {
            const payload = {
                order_id: selectedPayment.order_id,
                table_id: selectedPayment.table_id,
                table_key: selectedPayment.table_key,
            };
            
            console.log("📤 Sending payment processing request:", payload);
            
            const response = await api.post("/payments/process/cash", payload);
            
            console.log("✅ Payment processed successfully:", response.data);
            
            toast.success("Thanh toán thành công", {
                description: `Bàn ${selectedPayment.table_id} đã được thanh toán và giải phóng.`,
            });

            // Tự động in hóa đơn
            printBill(selectedPayment);

            setPaymentDialogOpen(false);
            setSelectedPayment(null);
            mutate("/payments/waiting");
            mutate("/orders/sessions");
            mutate("/payments/history");
        } catch (error: any) {
            console.error("❌ Payment error:", error);
            
            // Extract error details
            const errorMessage = error.response?.data?.error || 
                                error.response?.data?.message || 
                                error.message || 
                                "Không rõ lỗi";
            const errorStatus = error.response?.status || "unknown";
            
            console.error(`❌ Error status: ${errorStatus}, message: ${errorMessage}`);
            
            toast.error("Thanh toán thất bại", {
                description: `(${errorStatus}) ${errorMessage}`,
            });
        } finally {
            setIsProcessing(false);
        }
    };

    // Confirm buffet activation (thu ngân duyệt gói buffet cho khách)
    const confirmBuffetActivation = async (orderId: number, tableId: number) => {
        setConfirmingBuffetId(orderId);
        try {
            await api.post(`/orders/${orderId}/confirm`);
            toast.success("Đã kích hoạt buffet", {
                description: `Bàn ${tableId} có thể bắt đầu gọi món buffet.`,
            });
            mutate("/orders/sessions");
        } catch (error: any) {
            toast.error("Kích hoạt buffet thất bại", {
                description: error.response?.data?.message || error.message,
            });
        } finally {
            setConfirmingBuffetId(null);
        }
    };

    const pendingBuffetSessions = allSessions?.filter(
        (s: OrderSessionSummary) => s.has_pending_buffet
    ) || [];

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-3xl font-bold tracking-tight">Thu Ngân</h2>
                    <p className="text-muted-foreground mt-1">Quản lý thanh toán và hóa đơn bàn</p>
                </div>
            </div>

            {/* Stats Cards */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-2">
                <Card className={totalWaiting > 0 ? "border-amber-500 shadow-amber-500/20" : ""}>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium">Chờ thanh toán</CardTitle>
                        <Clock className={totalWaiting > 0 ? "size-4 text-amber-500" : "size-4 text-muted-foreground"} />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{totalWaiting}</div>
                        <p className="text-xs text-muted-foreground">bàn yêu cầu tính tiền</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2">
                        <CardTitle className="text-sm font-medium">Tổng tiền chờ thu</CardTitle>
                        <DollarSign className="size-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-amber-600">{formatCurrency(totalWaitingAmount)}</div>
                        <p className="text-xs text-muted-foreground">từ {totalWaiting} bàn</p>
                    </CardContent>
                </Card>
            </div>

            {/* Main Content */}
            <Tabs defaultValue="waiting" className="space-y-4">
                <TabsList>
                    <TabsTrigger value="waiting" className="gap-2">
                        <Receipt className="size-4" />
                        Yêu cầu (<span className="text-amber-500 font-bold">{totalWaiting}</span>)
                    </TabsTrigger>
                    <TabsTrigger value="buffet" className="gap-2">
                        <UtensilsCrossed className="size-4" />
                        Xác nhận Buffet
                        {pendingBuffetSessions.length > 0 && (
                            <span className="ml-1 rounded-full bg-orange-500 text-white text-xs px-1.5 py-0.5">
                                {pendingBuffetSessions.length}
                            </span>
                        )}
                    </TabsTrigger>
                    <TabsTrigger value="history" className="gap-2">
                        <History className="size-4" />
                        Lịch sử thanh toán
                    </TabsTrigger>
                </TabsList>

                {/* Waiting Payments Tab */}
                <TabsContent value="waiting">
                    <Card>
                        <CardHeader>
                            <CardTitle>Danh sách yêu cầu thanh toán</CardTitle>
                            <CardDescription>
                                Các bàn đã nhấn yêu cầu tính tiền và đang chờ thu ngân xử lý
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            {isLoadingWaiting ? (
                                <div className="space-y-4">
                                    {[1, 2, 3].map((i) => (
                                        <Skeleton key={i} className="h-16 w-full" />
                                    ))}
                                </div>
                            ) : !waitingPayments || waitingPayments.length === 0 ? (
                                <div className="flex flex-col items-center justify-center py-12 text-center text-muted-foreground">
                                    <CheckCircle className="size-12 text-green-500/50 mb-4" />
                                    <h3 className="text-lg font-medium text-foreground">Không có yêu cầu thanh toán</h3>
                                    <p>Tất cả các bàn đã được thanh toán hoặc chưa yêu cầu tính tiền.</p>
                                </div>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Bàn</TableHead>
                                            <TableHead>Số lượng đơn</TableHead>
                                            <TableHead>Tổng tiền</TableHead>
                                            <TableHead>Thời gian yêu cầu</TableHead>
                                            <TableHead>Trạng thái</TableHead>
                                            <TableHead className="text-right">Thao tác</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {waitingPayments.map((payment: WaitingPayment) => (
                                            <TableRow key={payment.order_id} className="bg-amber-500/5 hover:bg-amber-500/10 transition-colors">
                                                <TableCell>
                                                    <div className="flex items-center gap-2">
                                                        <div className="flex size-10 items-center justify-center rounded-lg bg-primary text-primary-foreground font-bold shadow-md">
                                                            {payment.table_id}
                                                        </div>
                                                        <span className="font-semibold text-lg">Bàn {payment.table_id}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <Badge variant="outline">{payment.order_count} đơn liên kết</Badge>
                                                </TableCell>
                                                <TableCell>
                                                    <span className="font-bold text-xl text-amber-600">
                                                        {formatCurrency(payment.total)}
                                                    </span>
                                                </TableCell>
                                                <TableCell>
                                                    <div className="flex flex-col">
                                                        <span>{new Date(payment.request_time).toLocaleTimeString('vi-VN')}</span>
                                                        <span className="text-xs text-muted-foreground">{new Date(payment.request_time).toLocaleDateString('vi-VN')}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    {payment.payment_method === 'momo' ? (
                                                        <div className="flex flex-col gap-1">
                                                            <Badge className="bg-[#a50064] hover:bg-[#8a0055] gap-1 animate-pulse w-fit">
                                                                <div className="size-3 rounded-full bg-white flex items-center justify-center"><span className="text-[#a50064] font-bold" style={{fontSize:'7px'}}>M</span></div>
                                                                {payment.momo_trans_id ? 'MoMo đã TT' : 'Yêu cầu MoMo'}
                                                            </Badge>
                                                        </div>
                                                    ) : (
                                                        <Badge className="bg-amber-500 hover:bg-amber-600 gap-1 animate-pulse">
                                                            <Clock className="size-3" />
                                                            Chờ thu ngân
                                                        </Badge>
                                                    )}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    <div className="flex justify-end gap-2">
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            onClick={() => printBill(payment)}
                                                            title="In tạm tính"
                                                        >
                                                            <Printer className="size-4" />
                                                        </Button>
                                                        <Button
                                                            variant="secondary"
                                                            size="sm"
                                                            onClick={() => handleViewDetail(payment)}
                                                        >
                                                            <Eye className="size-4 mr-1" />
                                                            Chi tiết
                                                        </Button>
                                                        {payment.payment_method === 'momo' ? (
                                                            <Button
                                                                size="sm"
                                                                className="bg-[#a50064] hover:bg-[#8a0055]"
                                                                disabled={openingMomoOrderId === payment.order_id}
                                                                onClick={() => handleOpenMomoPayment(payment)}
                                                            >
                                                                <ExternalLink className="size-4 mr-1" />
                                                                {openingMomoOrderId === payment.order_id ? 'Đang mở...' : 'Mở trang MoMo'}
                                                            </Button>
                                                        ) : (
                                                            <Button
                                                                size="sm"
                                                                className="bg-green-600 hover:bg-green-700"
                                                                onClick={() => handleOpenPaymentDialog(payment)}
                                                            >
                                                                <Banknote className="size-4 mr-1" />
                                                                Thu tiền
                                                            </Button>
                                                        )}
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Buffet Activation Tab */}
                <TabsContent value="buffet">
                    <Card>
                        <CardHeader>
                            <CardTitle>Xác nhận kích hoạt Buffet</CardTitle>
                            <CardDescription>
                                Các bàn đã chọn gói buffet và đang chờ thu ngân xác nhận trước khi được phép gọi món.
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            {isLoadingOrders ? (
                                <div className="space-y-4">
                                    {[1, 2].map((i) => <Skeleton key={i} className="h-16 w-full" />)}
                                </div>
                            ) : pendingBuffetSessions.length === 0 ? (
                                <div className="flex flex-col items-center justify-center py-12 text-center text-muted-foreground">
                                    <UtensilsCrossed className="size-12 text-green-500/50 mb-4" />
                                    <h3 className="text-lg font-medium text-foreground">Không có yêu cầu buffet nào</h3>
                                    <p className="text-sm mt-1">Khi khách chọn gói buffet, yêu cầu sẽ hiển thị ở đây.</p>
                                </div>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Bàn</TableHead>
                                            <TableHead>Gói buffet</TableHead>
                                            <TableHead>Giá gói</TableHead>
                                            <TableHead className="text-right">Thao tác</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {pendingBuffetSessions.map((session: OrderSessionSummary) => (
                                            <TableRow key={`buffet-${session.table_id}-${session.table_key}`}>
                                                <TableCell>
                                                    <Badge variant="outline" className="font-semibold">
                                                        Bàn {session.table_id}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell className="font-medium">
                                                    {session.pending_buffet_package_name || "Gói buffet"}
                                                </TableCell>
                                                <TableCell>
                                                    {session.pending_buffet_price
                                                        ? formatCurrency(session.pending_buffet_price)
                                                        : "—"}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    <Button
                                                        size="sm"
                                                        className="bg-orange-500 hover:bg-orange-600"
                                                        disabled={confirmingBuffetId === session.pending_buffet_order_id}
                                                        onClick={() =>
                                                            session.pending_buffet_order_id &&
                                                            confirmBuffetActivation(
                                                                session.pending_buffet_order_id,
                                                                session.table_id
                                                            )
                                                        }
                                                    >
                                                        <UtensilsCrossed className="size-4 mr-1" />
                                                        {confirmingBuffetId === session.pending_buffet_order_id
                                                            ? "Đang xử lý..."
                                                            : "Kích hoạt Buffet"}
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* History Tab */}
                <TabsContent value="history">
                    <Card>
                        <CardHeader>
                            <CardTitle>Lịch sử thanh toán</CardTitle>
                            <CardDescription>Các giao dịch thanh toán thành công gần đây (Phiên hiện tại)</CardDescription>
                        </CardHeader>
                        <CardContent>
                             {isLoadingHistory ? (
                                <div className="space-y-4">
                                    {[1, 2].map((i) => (
                                        <Skeleton key={i} className="h-16 w-full" />
                                    ))}
                                </div>
                            ) : !payHistory || payHistory.length === 0 ? (
                                <div className="flex flex-col items-center justify-center py-12 text-center text-muted-foreground">
                                    <Receipt className="size-12 opacity-20 mb-4" />
                                    <h3 className="text-lg font-medium text-foreground">Chưa có giao dịch nào</h3>
                                </div>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Mã giao dịch</TableHead>
                                            <TableHead>Mã đơn gốc</TableHead>
                                            <TableHead>Tổng số tiền</TableHead>
                                            <TableHead>Hình thức</TableHead>
                                            <TableHead>Thời gian</TableHead>
                                            <TableHead>Trạng thái</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {payHistory.map((ph: PaymentHistory, idx: number) => (
                                            <TableRow key={ph.id ?? `ph-${idx}`}>
                                                <TableCell><span className="font-mono text-xs">TXN-{ph.id}</span></TableCell>
                                                <TableCell>Order #{ph.order_id}</TableCell>
                                                <TableCell><span className="font-semibold text-green-600">+{formatCurrency(ph.amount)}</span></TableCell>
                                                <TableCell>
                                                    <Badge variant="outline" className="uppercase bg-slate-100 dark:bg-slate-800">
                                                        {ph.method === 'cash' ? 'Tiền mặt' : ph.method}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell>{formatDate(ph.paid_at)}</TableCell>
                                                <TableCell>
                                                    <span title="Thành công"><CheckCircle className="size-4 text-green-500" /></span>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>

            {/* Detail Dialog */}
            <Dialog open={detailDialogOpen} onOpenChange={setDetailDialogOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Phiếu Tính Tiền - Bàn {selectedPayment?.table_id}</DialogTitle>
                        <DialogDescription>
                            Thông tin chi tiết về yêu cầu thanh toán
                        </DialogDescription>
                    </DialogHeader>
                    {selectedPayment && (
                        <div className="space-y-4">
                            {/* Badge phương thức thanh toán */}
                            {selectedPayment.payment_method === 'momo' ? (
                                <div className="flex items-center gap-2 rounded-lg bg-purple-50 dark:bg-purple-950/30 border border-purple-200 dark:border-purple-800 p-3">
                                    <div className="size-8 rounded-full bg-[#a50064] flex items-center justify-center text-white font-bold text-xs flex-shrink-0">M</div>
                                    <div>
                                        <p className="text-sm font-semibold text-purple-700 dark:text-purple-300">
                                            {selectedPayment.momo_trans_id ? 'Khách đã thanh toán qua MoMo' : 'Khách yêu cầu thanh toán qua MoMo'}
                                        </p>
                                        {selectedPayment.momo_trans_id && (
                                            <p className="text-xs text-muted-foreground font-mono">Trans ID: {selectedPayment.momo_trans_id}</p>
                                        )}
                                    </div>
                                    <Badge className="ml-auto bg-[#a50064] text-white">MoMo</Badge>
                                </div>
                            ) : (
                                <div className="flex items-center gap-2 rounded-lg bg-green-50 dark:bg-green-950/30 border border-green-200 dark:border-green-800 p-3">
                                    <CreditCard className="size-5 text-green-600" />
                                    <p className="text-sm font-medium text-green-700 dark:text-green-300">Thanh toán tiền mặt — thu ngân nhận tiền trực tiếp</p>
                                </div>
                            )}
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1">
                                    <p className="text-sm text-muted-foreground">Số bàn</p>
                                    <p className="font-semibold text-lg">Bàn {selectedPayment.table_id}</p>
                                </div>
                                <div className="space-y-1">
                                    <p className="text-sm text-muted-foreground">Các đơn gộp</p>
                                    <p className="font-semibold">{selectedPayment.order_count} đơn liên kết</p>
                                </div>
                                <div className="space-y-1">
                                    <p className="text-sm text-muted-foreground">Thời gian khách báo</p>
                                    <p className="font-medium text-amber-600">{new Date(selectedPayment.request_time).toLocaleTimeString('vi-VN')}</p>
                                </div>
                                <div className="space-y-1">
                                    <p className="text-sm text-muted-foreground">Mã YC</p>
                                    <p className="font-mono text-sm">#{selectedPayment.order_id}</p>
                                </div>
                            </div>
                            <div className="border-t pt-4 bg-muted/30 p-4 rounded-lg mt-2">
                                <div className="flex items-center justify-between">
                                    <span className="text-lg font-medium">Khách cần trả:</span>
                                    <span className="text-3xl font-black text-primary">
                                        {formatCurrency(selectedPayment.total)}
                                    </span>
                                </div>
                            </div>
                            <div className="space-y-3">
                                <div className="flex items-center justify-between">
                                    <h4 className="font-semibold">Chi tiết món trong session</h4>
                                    <span className="text-sm text-muted-foreground">
                                        {selectedSessionOrders.length} đơn / {selectedSessionItems.length} món
                                    </span>
                                </div>
                                {selectedSessionItems.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">Chưa tải được chi tiết món.</p>
                                ) : (
                                    <div className="max-h-64 space-y-2 overflow-y-auto rounded-md border p-3">
                                        {selectedSessionItems.map((item: OrderItem & { order_id: number }, index: number) => (
                                            <div key={`${item.order_id}-${item.id}-${index}`} className="flex items-center justify-between gap-4 text-sm">
                                                <div className="min-w-0">
                                                    <p className="font-medium truncate">{item.menu_item_name}</p>
                                                    <p className="text-xs text-muted-foreground">
                                                        Đơn #{item.order_id} · SL {item.quantity}
                                                    </p>
                                                </div>
                                                <span className="font-medium whitespace-nowrap">
                                                    {formatCurrency(item.total ?? item.price * item.quantity)}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                    <DialogFooter className="mt-6 flex-col sm:flex-row gap-2">
                        <Button variant="outline" onClick={() => {
                            if (selectedPayment) printBill(selectedPayment);
                        }}>
                             <Printer className="size-4 mr-1" />
                             In Tạm Tính
                        </Button>
                        <Button
                             className={selectedPayment?.payment_method === 'momo'
                                 ? "bg-[#a50064] hover:bg-[#8a0055] w-full sm:w-auto"
                                 : "bg-green-600 hover:bg-green-700 w-full sm:w-auto"}
                            onClick={() => {
                                setDetailDialogOpen(false);
                                if (selectedPayment) {
                                    if (selectedPayment.payment_method === 'momo') {
                                        handleOpenMomoPayment(selectedPayment);
                                    } else {
                                        handleOpenPaymentDialog(selectedPayment);
                                    }
                                }
                            }}
                        >
                            {selectedPayment?.payment_method === 'momo' ? (
                                <>
                                    <ExternalLink className="size-4 mr-1" />
                                    Mở trang MoMo
                                </>
                            ) : (
                                <>
                                    <Banknote className="size-5 mr-2" />
                                    Xác Nhận Đã Nhận Tiền
                                </>
                            )}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Payment Confirmation Dialog */}
            <AlertDialog open={paymentDialogOpen} onOpenChange={setPaymentDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle className="flex items-center gap-2">
                            <Banknote className="size-5 text-green-600" />
                            {selectedPayment?.payment_method === 'momo'
                                ? 'Xác nhận MoMo đã thanh toán'
                                : 'Xác nhận đã nhận tiền mặt'}
                        </AlertDialogTitle>
                        <AlertDialogDescription asChild>
                            <div className="space-y-4">
                                <p>Bạn chuẩn bị xác nhận hoàn tất giao dịch cho:</p>
                                {selectedPayment && (
                                    <div className="rounded-lg border bg-amber-500/5 p-4 space-y-2">
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Bàn:</span>
                                            <span className="font-semibold text-xl text-foreground">
                                                Bàn {selectedPayment.table_id}
                                            </span>
                                        </div>
                                        <div className="flex justify-between border-t border-border/50 pt-2 mt-2">
                                            <span className="text-muted-foreground">Số tiền khách đưa:</span>
                                            <span className="text-2xl font-black text-green-600">
                                                {formatCurrency(selectedPayment.total)}
                                            </span>
                                        </div>
                                    </div>
                                )}
                                <p className="text-sm font-medium text-amber-600 bg-amber-500/10 p-2 rounded flex items-center gap-2">
                                    <Clock className="size-4" />
                                    Hành động này sẽ xóa phiên bàn hiện tại và cho phép bàn nhận khách mới!
                                </p>
                            </div>
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isProcessing}>Hủy bỏ</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleProcessPayment}
                            disabled={isProcessing}
                            className="bg-green-600 hover:bg-green-700 px-6 gap-2"
                        >
                            {isProcessing ? (
                                <>
                                    <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                                    Đang xử lý...
                                </>
                            ) : (
                                <>
                                    <CheckCircle className="size-4" />
                                    Xác nhận và In Hóa Đơn
                                </>
                            )}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
