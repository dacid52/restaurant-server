package com.restaurant.tableservice.repository;

import com.restaurant.tableservice.entity.TableKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TableKeyRepository extends JpaRepository<TableKey, Integer> {
    List<TableKey> findByTableIdAndIsValidTrueOrderByCreatedAtDesc(Integer tableId);

    /** Tìm key đang còn hiệu lực (is_valid=true VÀ chưa hết hạn). */
    @Query("SELECT k FROM TableKey k WHERE k.tableId = :tableId AND k.keyValue = :keyValue AND k.isValid = true AND k.expiresAt > CURRENT_TIMESTAMP")
    List<TableKey> findValidKey(@Param("tableId") Integer tableId, @Param("keyValue") String keyValue);

    /**
     * Tra cứu key bất kể trạng thái — dùng để phân biệt "key expired/invalidate" vs "key không tồn tại".
     * Chỉ cần Optional để biết key có từng được tạo không.
     */
    @Query("SELECT k FROM TableKey k WHERE k.tableId = :tableId AND k.keyValue = :keyValue ORDER BY k.createdAt DESC")
    java.util.Optional<TableKey> findByTableIdAndKeyValue(@Param("tableId") Integer tableId, @Param("keyValue") String keyValue);

    @Modifying
    @Query("UPDATE TableKey k SET k.isValid = false WHERE k.tableId = :tableId AND k.isValid = true")
    void invalidateKeysByTableId(@Param("tableId") Integer tableId);

    void deleteByTableId(Integer tableId);

    // BUG-005: Atomic claim — chỉ cập nhật nếu deviceSession vẫn còn null
    @Modifying
    @Query("UPDATE TableKey k SET k.deviceSession = :device " +
           "WHERE k.id = :id AND k.deviceSession IS NULL AND k.isValid = true AND k.expiresAt > CURRENT_TIMESTAMP")
    int claimDeviceSession(@Param("id") Integer id, @Param("device") String device);

    @Query("SELECT k FROM TableKey k WHERE k.tableId = :tableId AND k.isValid = true AND k.expiresAt > CURRENT_TIMESTAMP ORDER BY k.createdAt DESC")
    java.util.Optional<TableKey> findActiveKey(@Param("tableId") Integer tableId);
}
