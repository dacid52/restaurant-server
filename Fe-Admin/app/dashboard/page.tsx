"use client";

import { useEffect, useState, useCallback } from "react";
import {
  DollarSign,
  ShoppingCart,
  UtensilsCrossed,
  CalendarCheck,
  RefreshCw,
  TrendingUp,
  Download,
} from "lucide-react";
import {
  Line,
  LineChart,
  Bar,
  BarChart,
  XAxis,
  YAxis,
  CartesianGrid,
} from "recharts";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import * as XLSX from "xlsx";
import api from "@/lib/axios";

interface Order {
  id: number;
  table_id: number;
  table_key: string;
  tableName?: string;
  status: string;
  total: number;
  order_time: string;
  details: Array<{
    food_id: number;
    quantity: number;
  }>;
}

interface Food {
  id: number;
  name: string;
}

interface TableData {
  id: number;
  name: string;
  status: string;
}

interface Payment {
  id: number;
  amount: number;
  paid_at: string;
}

interface KPIData {
  totalRevenue: number;
  periodOrders: number;
  occupiedTables: number;
  availableTables: number;
}

interface RevenueData {
  date: string;
  revenue: number;
}

interface TopFoodData {
  name: string;
  quantity: number;
}

interface OrderStatusCount {
  pending: number;
  cooking: number;
  completed: number;
}

type PeriodType = "day" | "week" | "month" | "year";

const PERIOD_LABEL: Record<PeriodType, string> = {
  day: "Ngày",
  week: "Tuần",
  month: "Tháng",
  year: "Năm",
};

const formatCurrency = (value: number): string => {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
  }).format(value);
};

const isToday = (dateString: string): boolean => {
  const date = new Date(dateString);
  const today = new Date();
  return (
    date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()
  );
};

const startOfDay = (date: Date): Date => {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
};

const startOfWeek = (date: Date): Date => {
  const d = startOfDay(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return d;
};

const startOfMonth = (date: Date): Date => new Date(date.getFullYear(), date.getMonth(), 1);
const startOfYear = (date: Date): Date => new Date(date.getFullYear(), 0, 1);

const getPeriodRangeStart = (periodType: PeriodType, now: Date): Date => {
  const cursor = new Date(now);
  switch (periodType) {
    case "day":
      cursor.setDate(cursor.getDate() - 6);
      return startOfDay(cursor);
    case "week":
      cursor.setDate(cursor.getDate() - 6 * 7);
      return startOfWeek(cursor);
    case "month":
      return new Date(cursor.getFullYear(), cursor.getMonth() - 11, 1);
    case "year":
      return new Date(cursor.getFullYear() - 4, 0, 1);
  }
};

const getIsoWeek = (date: Date): { week: number; year: number } => {
  const target = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNr = target.getUTCDay() || 7;
  target.setUTCDate(target.getUTCDate() + 4 - dayNr);
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil((((target.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
  return { week: weekNo, year: target.getUTCFullYear() };
};

const getBucketStart = (date: Date, periodType: PeriodType): Date => {
  switch (periodType) {
    case "day":
      return startOfDay(date);
    case "week":
      return startOfWeek(date);
    case "month":
      return startOfMonth(date);
    case "year":
      return startOfYear(date);
  }
};

const formatBucketLabel = (date: Date, periodType: PeriodType): string => {
  switch (periodType) {
    case "day":
      return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
    case "week": {
      const iso = getIsoWeek(date);
      return `T${iso.week}/${iso.year}`;
    }
    case "month":
      return `${String(date.getMonth() + 1).padStart(2, "0")}/${date.getFullYear()}`;
    case "year":
      return String(date.getFullYear());
  }
};

const calculateRevenueByPeriod = (payments: Payment[], periodType: PeriodType): RevenueData[] => {
  const now = new Date();
  const rangeStart = getPeriodRangeStart(periodType, now);
  const bucketMap = new Map<number, number>();

  payments.forEach((payment) => {
    const paidAt = new Date(payment.paid_at);
    if (paidAt < rangeStart) return;
    const bucket = getBucketStart(paidAt, periodType).getTime();
    bucketMap.set(bucket, (bucketMap.get(bucket) || 0) + payment.amount);
  });

  return Array.from(bucketMap.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([bucket, revenue]) => ({
      date: formatBucketLabel(new Date(bucket), periodType),
      revenue,
    }));
};

const calculateTopFoodsByPeriod = (
  orders: Order[],
  foods: Food[],
  periodType: PeriodType
): TopFoodData[] => {
  const now = new Date();
  const rangeStart = getPeriodRangeStart(periodType, now);
  const foodQuantityMap = new Map<number, number>();

  orders
    .filter((order) => new Date(order.order_time) >= rangeStart)
    .forEach((order) => {
      order.details?.forEach((detail) => {
        foodQuantityMap.set(
          detail.food_id,
          (foodQuantityMap.get(detail.food_id) || 0) + detail.quantity
        );
      });
    });

  const foodMap = new Map(foods.map((f) => [f.id, f.name]));

  return Array.from(foodQuantityMap.entries())
    .map(([foodId, quantity]) => ({
      name: foodMap.get(foodId) || `Mon ${foodId}`,
      quantity,
    }))
    .sort((a, b) => b.quantity - a.quantity)
    .slice(0, 8);
};

const calculateKPIs = (
  orders: Order[],
  tables: TableData[],
  payments: Payment[],
  periodType: PeriodType
): KPIData => {
  const rangeStart = getPeriodRangeStart(periodType, new Date());
  const totalRevenue = payments
    .filter((p) => new Date(p.paid_at) >= rangeStart)
    .reduce((sum, p) => sum + p.amount, 0);

  const periodOrders = orders.filter((o) => new Date(o.order_time) >= rangeStart).length;
  const occupiedTables = tables.filter((t) => t.status === "Đang sử dụng").length;
  const availableTables = tables.filter((t) => t.status === "Trống").length;

  return { totalRevenue, periodOrders, occupiedTables, availableTables };
};

const calculateOrderStatus = (orders: Order[]): OrderStatusCount => {
  return {
    pending: orders.filter((o) => o.status === "Chờ xác nhận").length,
    cooking: orders.filter((o) => o.status === "Đang nấu").length,
    completed: orders.filter((o) => o.status === "Hoàn thành").length,
  };
};

const getRecentOrders = (orders: Order[]): Order[] => {
  return [...orders]
    .sort((a, b) => new Date(b.order_time).getTime() - new Date(a.order_time).getTime())
    .slice(0, 5);
};

const revenueChartConfig = {
  revenue: {
    label: "Doanh thu",
    color: "var(--chart-1)",
  },
} satisfies ChartConfig;

const topFoodsChartConfig = {
  quantity: {
    label: "Số lượng",
    color: "var(--chart-2)",
  },
} satisfies ChartConfig;

const StatusBadge = ({ status }: { status: string }) => {
  const getStatusStyle = () => {
    switch (status) {
      case "Chờ xác nhận":
        return "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400";
      case "Đang nấu":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400";
      case "Hoàn thành":
        return "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400";
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400";
    }
  };

  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${getStatusStyle()}`}>
      {status}
    </span>
  );
};

const DashboardSkeleton = () => (
  <div className="flex flex-col gap-6">
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      {[...Array(4)].map((_, i) => (
        <Card key={i}>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-4 w-4" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-8 w-32" />
            <Skeleton className="mt-2 h-3 w-20" />
          </CardContent>
        </Card>
      ))}
    </div>
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-40" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-[300px] w-full" />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-40" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-[300px] w-full" />
        </CardContent>
      </Card>
    </div>
  </div>
);

export default function AdminDashboard() {
  const { toast } = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [warning, setWarning] = useState<string | null>(null);

  const [periodType, setPeriodType] = useState<PeriodType>("day");
  const [exportRevenue, setExportRevenue] = useState(true);
  const [exporting, setExporting] = useState(false);

  const [rawOrders, setRawOrders] = useState<Order[]>([]);
  const [rawFoods, setRawFoods] = useState<Food[]>([]);
  const [rawTables, setRawTables] = useState<TableData[]>([]);
  const [rawPayments, setRawPayments] = useState<Payment[]>([]);

  const [kpis, setKpis] = useState<KPIData>({
    totalRevenue: 0,
    periodOrders: 0,
    occupiedTables: 0,
    availableTables: 0,
  });
  const [revenueData, setRevenueData] = useState<RevenueData[]>([]);
  const [topFoods, setTopFoods] = useState<TopFoodData[]>([]);
  const [recentOrders, setRecentOrders] = useState<Order[]>([]);
  const [orderStatus, setOrderStatus] = useState<OrderStatusCount>({
    pending: 0,
    cooking: 0,
    completed: 0,
  });

  const recalculateDashboard = useCallback(
    (orders: Order[], foods: Food[], tables: TableData[], payments: Payment[], selectedPeriod: PeriodType) => {
      const enrichedOrders = orders.map((o) => ({
        ...o,
        tableName:
          tables.find((t) => t.id === o.table_id)?.name ||
          (o.table_key ? `Mã ${o.table_key.substring(0, 8)}` : `Bàn ${o.table_id}`),
      }));

      setKpis(calculateKPIs(enrichedOrders, tables, payments, selectedPeriod));
      setRevenueData(calculateRevenueByPeriod(payments, selectedPeriod));
      setTopFoods(calculateTopFoodsByPeriod(enrichedOrders, foods, selectedPeriod));
      setRecentOrders(getRecentOrders(enrichedOrders));
      setOrderStatus(calculateOrderStatus(enrichedOrders));
    },
    []
  );

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setWarning(null);

      const [ordersResult, foodsResult, tablesResult, paymentsResult] = await Promise.allSettled([
        api.get("/orders"),
        api.get("/menu/foods"),
        api.get("/tables"),
        api.get("/payments/history"),
      ]);

      const failedSources: string[] = [];

      const extractData = <T,>(
        result: PromiseSettledResult<{ data: T }>,
        fallback: T,
        sourceLabel: string
      ): T => {
        if (result.status === "fulfilled") {
          return result.value.data ?? fallback;
        }
        const reason = result.reason as { message?: string } | undefined;
        failedSources.push(`${sourceLabel}: ${reason?.message || "Lỗi mạng"}`);
        return fallback;
      };

      const orders = extractData<Order[]>(ordersResult, [], "Orders");
      const foods = extractData<Food[]>(foodsResult, [], "Menu foods");
      const tables = extractData<TableData[]>(tablesResult, [], "Tables");
      const payments = extractData<Payment[]>(paymentsResult, [], "Payments");

      if (failedSources.length === 4) {
        setError("Không thể tải dữ liệu dashboard (lỗi mạng). Vui lòng kiểm tra API Gateway và các service backend.");
        return;
      }
      if (failedSources.length > 0) {
        setWarning(`Một số dữ liệu đang tạm thời không tải được: ${failedSources.join(" | ")}`);
      }

      setRawOrders(orders);
      setRawFoods(foods);
      setRawTables(tables);
      setRawPayments(payments);

      recalculateDashboard(orders, foods, tables, payments, periodType);
    } catch (err) {
      setError("Không thể tải dữ liệu. Vui lòng thử lại.");
      console.error("Dashboard fetch error:", err);
    } finally {
      setLoading(false);
    }
  }, [periodType, recalculateDashboard]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  useEffect(() => {
    recalculateDashboard(rawOrders, rawFoods, rawTables, rawPayments, periodType);
  }, [rawOrders, rawFoods, rawTables, rawPayments, periodType, recalculateDashboard]);

  const handleExportExcel = async () => {
    try {
      setExporting(true);
      const workbook = XLSX.utils.book_new();

      const summaryRows = [
        ["Báo cáo dashboard"],
        ["Kỳ tổng hợp", PERIOD_LABEL[periodType]],
        ["Tạo lúc", new Date().toLocaleString("vi-VN")],
        [],
        ["Tổng doanh thu", kpis.totalRevenue],
        ["Số đơn trong kỳ", kpis.periodOrders],
      ];
      const summarySheet = XLSX.utils.aoa_to_sheet(summaryRows);
      XLSX.utils.book_append_sheet(workbook, summarySheet, "Tong quan");

      const rows = revenueData.map((item) => ({
        Ky: item.date,
        DoanhThu: item.revenue,
      }));
      const sheet = XLSX.utils.json_to_sheet(rows.length ? rows : [{ Ky: "", DoanhThu: 0 }]);
      XLSX.utils.book_append_sheet(workbook, sheet, "Doanh thu");

      const fileName = `dashboard-${periodType}-${new Date().toISOString().slice(0, 10)}.xlsx`;
      XLSX.writeFile(workbook, fileName);

      toast({
        title: "Xuất Excel thành công",
        description: `Đã tạo file ${fileName}`,
      });
    } catch (err) {
      console.error("Export dashboard excel failed:", err);
      toast({
        title: "Xuất Excel thất bại",
        description: "Không thể tạo file báo cáo dashboard.",
        variant: "destructive",
      });
    } finally {
      setExporting(false);
    }
  };

  if (loading) return <DashboardSkeleton />;

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-12">
        <p className="text-destructive">{error}</p>
        <Button onClick={fetchData} variant="outline">
          <RefreshCw className="mr-2 h-4 w-4" />
          Thử lại
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {warning && (
        <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {warning}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Bộ lọc và Xuất Excel</CardTitle>
          <CardDescription>Chọn kỳ báo cáo theo ngày / tuần / tháng / năm và dữ liệu muốn xuất.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div className="grid gap-2">
            <Label>Kỳ báo cáo</Label>
            <Select value={periodType} onValueChange={(v) => setPeriodType(v as PeriodType)}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="Chọn kỳ" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="day">Theo ngày</SelectItem>
                <SelectItem value="week">Theo tuần</SelectItem>
                <SelectItem value="month">Theo tháng</SelectItem>
                <SelectItem value="year">Theo năm</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-wrap items-center gap-6">
            <div className="flex items-center gap-2">
              <Checkbox checked={exportRevenue} onCheckedChange={(v) => setExportRevenue(v === true)} id="cb-revenue" />
              <Label htmlFor="cb-revenue">Doanh thu</Label>
            </div>
            <Button onClick={handleExportExcel} disabled={exporting}>
              <Download className="mr-2 h-4 w-4" />
              {exporting ? "Đang xuất..." : "Xuất Excel"}
            </Button>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Tổng doanh thu ({PERIOD_LABEL[periodType]})</CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formatCurrency(kpis.totalRevenue)}</div>
            <p className="text-xs text-muted-foreground">Theo bộ lọc đang chọn</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Đơn trong kỳ</CardTitle>
            <ShoppingCart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{kpis.periodOrders}</div>
            <p className="text-xs text-muted-foreground">Theo bộ lọc đang chọn</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Bàn đang phục vụ</CardTitle>
            <UtensilsCrossed className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{kpis.occupiedTables}</div>
            <p className="text-xs text-muted-foreground">Đang có khách</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Bàn trống</CardTitle>
            <CalendarCheck className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{kpis.availableTables}</div>
            <p className="text-xs text-muted-foreground">Sẵn sàng phục vụ</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <TrendingUp className="h-5 w-5" />
            Doanh thu theo {PERIOD_LABEL[periodType].toLowerCase()}
          </CardTitle>
          <CardDescription>Dữ liệu tổng hợp theo bộ lọc đang chọn</CardDescription>
        </CardHeader>
        <CardContent>
          <ChartContainer config={revenueChartConfig} className="h-[300px] w-full">
            <LineChart data={revenueData} margin={{ top: 5, right: 10, left: 10, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
              <XAxis dataKey="date" tickLine={false} axisLine={false} tickMargin={8} className="text-xs" />
              <YAxis
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                tickFormatter={(value) => `${(value / 1000000).toFixed(1)}M`}
                className="text-xs"
              />
              <ChartTooltip content={<ChartTooltipContent formatter={(value) => formatCurrency(Number(value))} />} />
              <Line
                type="monotone"
                dataKey="revenue"
                stroke="var(--color-revenue)"
                strokeWidth={2}
                dot={{ fill: "var(--color-revenue)", r: 4 }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ChartContainer>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle>Đơn hàng gần nhất</CardTitle>
            <CardDescription>5 đơn hàng mới nhất</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Bàn</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Tổng tiền</TableHead>
                  <TableHead>Thời gian</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recentOrders.length > 0 ? (
                  recentOrders.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell className="font-medium">{order.tableName}</TableCell>
                      <TableCell>
                        <StatusBadge status={order.status} />
                      </TableCell>
                      <TableCell>{formatCurrency(order.total)}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {new Date(order.order_time).toLocaleString("vi-VN")}
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-muted-foreground">
                      Chưa có đơn hàng
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Trạng thái đơn hàng</CardTitle>
            <CardDescription>Tổng quan đơn hàng</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="h-3 w-3 rounded-full bg-amber-500" />
                <span className="text-sm">Chờ xác nhận</span>
              </div>
              <span className="font-semibold">{orderStatus.pending}</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="h-3 w-3 rounded-full bg-blue-500" />
                <span className="text-sm">Đang nấu</span>
              </div>
              <span className="font-semibold">{orderStatus.cooking}</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="h-3 w-3 rounded-full bg-gray-500" />
                <span className="text-sm">Hoàn thành</span>
              </div>
              <span className="font-semibold">{orderStatus.completed}</span>
            </div>
            <div className="mt-4 rounded-lg bg-muted p-4">
              <p className="text-sm text-muted-foreground">Tổng số đơn</p>
              <p className="text-2xl font-bold">
                {orderStatus.pending + orderStatus.cooking + orderStatus.completed}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="flex items-center justify-end gap-2 text-xs text-muted-foreground">
        <RefreshCw className="h-3 w-3" />
        <span>Tự động cập nhật mỗi 30 giây</span>
        <Button variant="ghost" size="sm" onClick={fetchData} className="h-6 px-2">
          Làm mới
        </Button>
      </div>
    </div>
  );
}
