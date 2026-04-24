package com.coresync.hrms.backend.repository;

import com.coresync.hrms.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Integer employeeId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.employee.id = :employeeId AND n.isRead = false")
    void markAllAsReadByEmployeeId(Integer employeeId);

    long countByEmployeeIdAndIsReadFalse(Integer employeeId);
}
