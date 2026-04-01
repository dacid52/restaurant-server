'use client';

import { useEffect, useState } from 'react';
import { QrCode, Trash2, Plus, Wine, Search } from 'lucide-react';
import { toast } from 'sonner';
import api from '@/lib/axios';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';

interface Table {
  id: string;
  name: string;
  status: 'Trống' | 'Đang sử dụng' | 'Đã đặt' | string;
  is_buffet: boolean;
  capacity?: number;
  created_at?: string;
  updated_at?: string;
}

export default function TableManagementPage() {
  const [tables, setTables] = useState<Table[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTable, setSelectedTable] = useState<Table | null>(null);
  const [isDetailDialogOpen, setIsDetailDialogOpen] = useState(false);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [showQRDialog, setShowQRDialog] = useState(false);

  // Form state
  const [formData, setFormData] = useState<{
    name: string;
    status: 'Trống' | 'Đang sử dụng' | 'Đã đặt';
    isBuffet: boolean;
    capacity: number;
  }>({
    name: '',
    status: 'Trống',
    isBuffet: false,
    capacity: 2,
  });

  // Fetch tables on mount
  useEffect(() => {
    fetchTables();
    const interval = setInterval(fetchTables, 20000); // Auto refresh every 20s
    return () => clearInterval(interval);
  }, []);

  const fetchTables = async () => {
    try {
      setError(null);
      const response = await api.get('/tables');
      setTables(response.data || []);
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : 'Failed to fetch tables';
      setError(errorMessage);
      console.error('Error fetching tables:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateTable = async () => {
    if (!formData.name.trim()) {
      toast.error('Vui lòng nhập tên bàn');
      return;
    }

    setFormLoading(true);
    try {
      await api.post('/tables', {
        name: formData.name,
        status: formData.status,
        isBuffet: formData.isBuffet,
        capacity: formData.capacity,
      });
      toast.success('Tạo bàn thành công');
      setIsCreateDialogOpen(false);
      resetForm();
      await fetchTables();
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : 'Failed to create table';
      toast.error(`Lỗi: ${errorMsg}`);
      console.error('Error creating table:', err);
    } finally {
      setFormLoading(false);
    }
  };

  const handleUpdateTable = async () => {
    if (!selectedTable || !formData.name.trim()) {
      toast.error('Vui lòng nhập tên bàn');
      return;
    }

    setFormLoading(true);
    try {
      await api.put(`/tables/${selectedTable.id}`, {
        name: formData.name,
        status: formData.status,
        is_buffet: formData.isBuffet,
        capacity: formData.capacity,
      });
      toast.success('Cập nhật bàn thành công');
      setIsDetailDialogOpen(false);
      resetForm();
      await fetchTables();
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : 'Failed to update table';
      toast.error(`Lỗi: ${errorMsg}`);
      console.error('Error updating table:', err);
    } finally {
      setFormLoading(false);
    }
  };

  const handleDeleteTable = async () => {
    if (!selectedTable) return;

    setFormLoading(true);
    try {
      await api.delete(`/tables/${selectedTable.id}`);
      toast.success('Xóa bàn thành công');
      setDeleteConfirmOpen(false);
      setIsDetailDialogOpen(false);
      resetForm();
      await fetchTables();
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : 'Failed to delete table';
      toast.error(`Lỗi: ${errorMsg}`);
      console.error('Error deleting table:', err);
    } finally {
      setFormLoading(false);
    }
  };

  const handleGenerateQR = async () => {
    if (!selectedTable) return;

    try {
      const response = await api.post(`/tables/${selectedTable.id}/qr/dynamic`);
      setQrCode(response.data?.qr_code || response.data?.url);
      setShowQRDialog(true);
      toast.success('Tạo QR code thành công');
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : 'Failed to generate QR';
      toast.error(`Lỗi: ${errorMsg}`);
      console.error('Error generating QR:', err);
    }
  };

  const handleInvalidateKeys = async () => {
    if (!selectedTable) return;

    try {
      await api.post(`/tables/${selectedTable.id}/keys/invalidate`);
      toast.success('Vô hiệu hóa khóa thành công');
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : 'Failed to invalidate keys';
      toast.error(`Lỗi: ${errorMsg}`);
      console.error('Error invalidating keys:', err);
    }
  };

  const openDetailDialog = (table: Table) => {
    setSelectedTable(table);
    setFormData({
      name: table.name,
      status: table.status as any,
      isBuffet: table.is_buffet,
      capacity: table.capacity || 2,
    });
    setIsDetailDialogOpen(true);
  };

  const resetForm = () => {
    setFormData({
      name: '',
      status: 'Trống',
      isBuffet: false,
      capacity: 2,
    });
    setSelectedTable(null);
  };

  const getStatusBadge = (status: string) => {
    const s = status ? status.toLowerCase() : '';
    let config = { variant: 'secondary', label: 'Trống' };
    
    if (s.includes('occupied') || s.includes('đang sử dụng') || s.includes('đang phục vụ') || s.includes('chờ')) {
      config = { variant: 'destructive', label: 'Đang phục vụ' };
    } else if (s.includes('reserved') || s.includes('đã đặt')) {
      config = { variant: 'outline', label: 'Đã đặt' };
    }
    
    return <Badge variant={config.variant as any}>{config.label}</Badge>;
  };

  const filteredTables = tables.filter((table) =>
    table.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <div className="flex-1 flex gap-2">
            <Search className="w-5 h-5 text-muted-foreground" />
            <Skeleton className="h-9 flex-1" />
          </div>
          <Skeleton className="h-9 w-24" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-48 rounded-lg" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-muted-foreground" />
          <Input
            placeholder="Tìm kiếm bàn theo tên..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10"
          />
        </div>
        <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={() => resetForm()}>
              <Plus className="w-4 h-4" />
              Thêm bàn
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Tạo bàn mới</DialogTitle>
              <DialogDescription>Điền thông tin để tạo bàn mới</DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="name">Tên bàn</Label>
                <Input
                  id="name"
                  placeholder="VD: Bàn 01, Bàn VIP..."
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="capacity">Sức chứa</Label>
                <Input
                  id="capacity"
                  type="number"
                  min="1"
                  value={formData.capacity}
                  onChange={(e) =>
                    setFormData({ ...formData, capacity: parseInt(e.target.value) || 2 })
                  }
                />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="buffet">Là bàn buffet</Label>
                <Switch
                  id="buffet"
                  checked={formData.isBuffet}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, isBuffet: checked })
                  }
                />
              </div>
            </div>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setIsCreateDialogOpen(false)}
              >
                Hủy
              </Button>
              <Button onClick={handleCreateTable} disabled={formLoading}>
                {formLoading ? 'Đang tạo...' : 'Tạo bàn'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {/* Error State */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex justify-between items-center">
          <div>
            <p className="font-medium text-red-900">Lỗi</p>
            <p className="text-sm text-red-700">{error}</p>
          </div>
          <Button variant="outline" size="sm" onClick={fetchTables}>
            Thử lại
          </Button>
        </div>
      )}

      {/* Tables Grid */}
      {filteredTables.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-muted-foreground">
            {searchQuery ? 'Không tìm thấy bàn nào' : 'Chưa có bàn nào'}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filteredTables.map((table) => (
            <div
              key={table.id}
              className="bg-card border rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer group"
              onClick={() => openDetailDialog(table)}
            >
              <div className="space-y-3">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <h3 className="font-semibold text-foreground">{table.name}</h3>
                      {table.is_buffet && (
                        <Wine className="w-4 h-4 text-purple-500" />
                      )}
                    </div>
                    {table.capacity && (
                      <p className="text-xs text-muted-foreground">Sức chứa: {table.capacity} người</p>
                    )}
                  </div>
                </div>
                <div>{getStatusBadge(table.status)}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Detail Dialog */}
      <Dialog open={isDetailDialogOpen} onOpenChange={setIsDetailDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Chi tiết bàn</DialogTitle>
            <DialogDescription>Quản lý thông tin bàn</DialogDescription>
          </DialogHeader>
          {selectedTable && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="detail-name">Tên bàn</Label>
                <Input
                  id="detail-name"
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="status">Trạng thái</Label>
                <Select
                  value={formData.status}
                  onValueChange={(value: any) =>
                    setFormData({ ...formData, status: value })
                  }
                >
                  <SelectTrigger id="status">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Trống">Trống</SelectItem>
                    <SelectItem value="Đang sử dụng">Đang phục vụ</SelectItem>
                    <SelectItem value="Đã đặt">Đã đặt</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="detail-capacity">Sức chứa</Label>
                <Input
                  id="detail-capacity"
                  type="number"
                  min="1"
                  value={formData.capacity}
                  onChange={(e) =>
                    setFormData({ ...formData, capacity: parseInt(e.target.value) || 2 })
                  }
                />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="detail-buffet">Là bàn buffet</Label>
                <Switch
                  id="detail-buffet"
                  checked={formData.isBuffet}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, isBuffet: checked })
                  }
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <div className="flex flex-col sm:flex-row gap-2 w-full">
              <Button
                variant="outline"
                onClick={() => setIsDetailDialogOpen(false)}
                className="flex-1"
              >
                Đóng
              </Button>
              <Button
                variant="outline"
                onClick={handleGenerateQR}
                className="flex-1"
              >
                <QrCode className="w-4 h-4" />
                QR code
              </Button>
              <Button
                variant="outline"
                onClick={handleInvalidateKeys}
                className="flex-1"
              >
                Vô hiệu hóa
              </Button>
              <Button
                variant="destructive"
                onClick={() => setDeleteConfirmOpen(true)}
                className="flex-1"
              >
                <Trash2 className="w-4 h-4" />
                Xóa
              </Button>
              <Button
                onClick={handleUpdateTable}
                disabled={formLoading}
                className="flex-1"
              >
                {formLoading ? 'Đang lưu...' : 'Lưu'}
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteConfirmOpen} onOpenChange={setDeleteConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa bàn</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn xóa bàn "{selectedTable?.name}"? Hành động này không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDeleteTable}
              disabled={formLoading}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {formLoading ? 'Đang xóa...' : 'Xóa'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* QR Code Dialog */}
      <Dialog open={showQRDialog} onOpenChange={setShowQRDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>QR Code cho bàn {selectedTable?.name}</DialogTitle>
            <DialogDescription>
              Sử dụng QR code này cho bàn
            </DialogDescription>
          </DialogHeader>
          {qrCode && (
            <div className="flex justify-center p-4">
              <img
                src={qrCode}
                alt="QR Code"
                className="w-48 h-48 border rounded-lg"
              />
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowQRDialog(false)}>
              Đóng
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}