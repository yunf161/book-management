package com.yunfeil161.ailibrary.audit;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 无修改、删除审计记录的方法。 */
@Mapper
public interface OperationLogMapper {
  int insert(OperationLog log);
  List<OperationLog> find(OperationLogQuery query);
}
