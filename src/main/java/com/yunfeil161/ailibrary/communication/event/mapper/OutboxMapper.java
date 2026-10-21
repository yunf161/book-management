package com.yunfeil161.ailibrary.communication.event.mapper;

import com.yunfeil161.ailibrary.communication.event.entity.OutboxEvent;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 短事务领取事件，网络发布不持有数据库行锁。 */
@Mapper
public interface OutboxMapper {
  int insert(OutboxEvent event);

  List<OutboxEvent> pending();

  int claim(@Param("id") Long id, @Param("token") String token);

  int published(@Param("id") Long id, @Param("token") String token);

  int failed(
      @Param("id") Long id,
      @Param("token") String token,
      @Param("error") String error,
      @Param("next") java.time.LocalDateTime next);
}
