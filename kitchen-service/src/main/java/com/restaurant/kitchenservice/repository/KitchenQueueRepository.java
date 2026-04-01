package com.restaurant.kitchenservice.repository;

import com.restaurant.kitchenservice.entity.KitchenQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface KitchenQueueRepository extends JpaRepository<KitchenQueue, Integer> {

    @Query(value = "SELECT kq.id, kq.order_detail_id, kq.status, kq.updated_at, " +
            "od.quantity, od.order_id, f.name as food_name, f.image_url as food_image, t.name as table_name " +
            "FROM KitchenDB.kitchen_queue kq " +
            "JOIN OrderDB.order_details od ON kq.order_detail_id = od.id " +
            "JOIN MenuDB.foods f ON od.food_id = f.id " +
            "JOIN OrderDB.orders o ON od.order_id = o.id " +
            "JOIN TableDB.tables t ON o.table_id = t.id " +
            "WHERE (:status IS NULL OR kq.status = :status) " +
            "ORDER BY kq.updated_at ASC", nativeQuery = true)
    List<Map<String, Object>> getQueueWithDetails(@Param("status") String status);

    @Query(value = "SELECT " +
            "(SELECT COUNT(*) FROM KitchenDB.kitchen_queue WHERE status = N'Chờ chế biến') as pending_count, " +
            "(SELECT COUNT(*) FROM KitchenDB.kitchen_queue WHERE status = N'Đang chế biến') as cooking_count, " +
            "(SELECT COUNT(*) FROM KitchenDB.kitchen_queue WHERE status = N'Hoàn thành') as completed_count, " +
            "(SELECT COUNT(*) FROM KitchenDB.kitchen_queue) as total_count", nativeQuery = true)
    Map<String, Object> getKitchenStats();

    void deleteByStatus(String status);

    @Query(value = "SELECT od.order_id FROM OrderDB.order_details od WHERE od.id = :orderDetailId", nativeQuery = true)
    Integer findOrderIdByOrderDetailId(@Param("orderDetailId") Integer orderDetailId);

    @Query(value = "SELECT COUNT(*) FROM KitchenDB.kitchen_queue kq " +
            "JOIN OrderDB.order_details od ON kq.order_detail_id = od.id " +
            "WHERE od.order_id = :orderId AND kq.status != N'Hoàn thành'", nativeQuery = true)
    Integer countIncompleteItemsForOrder(@Param("orderId") Integer orderId);

    @Query(value = "SELECT COUNT(*) FROM KitchenDB.kitchen_queue WHERE order_detail_id = :orderDetailId", nativeQuery = true)
    Integer countByOrderDetailId(@Param("orderDetailId") Integer orderDetailId);

    @Query(value = "SELECT kq.status FROM KitchenDB.kitchen_queue kq " +
            "JOIN OrderDB.order_details od ON kq.order_detail_id = od.id " +
            "WHERE od.order_id = :orderId", nativeQuery = true)
    List<String> findAllStatusesForOrder(@Param("orderId") Integer orderId);

    @Query(value = "SELECT od.food_id as foodId, od.quantity as quantity " +
            "FROM KitchenDB.kitchen_queue kq " +
            "JOIN OrderDB.order_details od ON kq.order_detail_id = od.id " +
            "WHERE kq.id = :id", nativeQuery = true)
    Map<String, Object> findFoodIdAndQuantityByQueueId(@Param("id") Integer id);
}
