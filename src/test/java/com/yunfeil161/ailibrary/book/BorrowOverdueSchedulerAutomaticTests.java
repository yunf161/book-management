package com.yunfeil161.ailibrary.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** 验证 Spring 调度线程按照 cron 自动执行，而不是由测试手动调用任务方法。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:overdue_scheduler_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "library.borrow.overdue-sync-cron=*/1 * * * * *"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BorrowOverdueSchedulerAutomaticTests {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void scheduledTaskAutomaticallyMarksDueRecordOverdue() throws Exception {
        long applicationId = System.nanoTime();
        jdbc.update("""
                insert into borrow_record(
                    application_id, member_id, copy_id, borrowed_at, due_at,
                    status, overdue_days, approved_by, version
                ) values (?, 900001, 900001, dateadd('DAY', -2, current_timestamp),
                          dateadd('HOUR', -1, current_timestamp), 'BORROWING', 0, 900002, 0)
                """, applicationId);

        System.out.println("[自动定时任务测试] 执行前 status=BORROWING, version=0");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String status = jdbc.queryForObject(
                    "select status from borrow_record where application_id=?", String.class, applicationId);
            if ("OVERDUE".equals(status)) {
                Long version = jdbc.queryForObject(
                        "select version from borrow_record where application_id=?", Long.class, applicationId);
                System.out.println("[自动定时任务测试] 自动执行后 status=" + status + ", version=" + version);
                assertEquals(1L, version);
                Long recordId = jdbc.queryForObject(
                    "select id from borrow_record where application_id=?", Long.class, applicationId);
                assertEquals(1, jdbc.queryForObject(
                    "select count(*) from message where notification_key=?", Integer.class,
                    "borrow-record:" + recordId + ":overdue"));
                return;
            }
            Thread.sleep(100);
        }
        fail("等待5秒后记录仍未被定时任务自动同步为OVERDUE");
    }
}
