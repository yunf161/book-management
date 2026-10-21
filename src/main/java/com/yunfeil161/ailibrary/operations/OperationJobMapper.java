package com.yunfeil161.ailibrary.operations;

import java.util.List;
import org.apache.ibatis.annotations.*;

/** 文件payload只在执行和授权下载时单独读取，分页不载入BLOB。 */
@Mapper
public interface OperationJobMapper {
  String COLUMNS =
    "id,job_id,member_id,type,state,request_hash,total_rows,completed_rows,success_rows,failed_rows,result_json,error_code,created_at,updated_at";

  @Insert(
    "insert into operation_job(job_id,member_id,type,request_hash,input_data) values(#{jobId},#{memberId},#{type},#{requestHash},#{inputData})"
  )
  int insert(OperationJob job);

  @Select("select " + COLUMNS + " from operation_job where job_id=#{id}")
  OperationJob find(String id);

  @Select(
    "select " + COLUMNS + " from operation_job where request_hash=#{hash}"
  )
  OperationJob hash(String hash);

  @Select(
    "<script>select " +
      COLUMNS +
      " from operation_job where member_id=#{member}<if test='q.jobId != null'> and job_id=#{q.jobId}</if><if test='q.type != null'> and type=#{q.type}</if><if test='q.state != null'> and state=#{q.state}</if> order by id desc</script>"
  )
  List<OperationJob> query(
    @Param("member") Long member,
    @Param("q") OperationJobQuery query
  );
}
